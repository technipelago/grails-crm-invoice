/*
 * Copyright (c) 2012 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.crm.invoice

import grails.events.Listener
import grails.plugins.crm.core.CrmContactInformation
import grails.plugins.crm.core.DateUtils
import grails.plugins.crm.core.PagedResultList
import grails.plugins.crm.core.SearchUtils
import grails.plugins.crm.core.TenantUtils
import grails.plugins.selection.Selectable
import groovy.transform.CompileStatic
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod
import org.grails.databinding.SimpleMapDataBindingSource

/**
 * Name says it all, invoicing features.
 */
class CrmInvoiceService {

    def grailsApplication
    def grailsWebDataBinder

    def sequenceGeneratorService

    def crmCoreService
    def crmSecurityService
    def crmTagService

    @Listener(namespace = "crmInvoice", topic = "enableFeature")
    def enableFeature(event) {
        // event = [feature: feature, tenant: tenant, role:role, expires:expires]
        def tenant = event.tenant
        TenantUtils.withTenant(tenant) {
            // Initialize a number sequence for CrmInvoice
            def config = grailsApplication.config.crm.invoice.sequence
            Long start = config.start ?: 1L
            String format = config.format ?: "%s"
            sequenceGeneratorService.initSequence(CrmInvoice, null, tenant, start, format)

            // Create generic tag for CrmInvoice
            crmTagService.createTag(name: CrmInvoice.name, multiple: true)

            // Create default lookup codes.
            createInvoiceStatus(name: "Created", param: 'created', true)
            createInvoiceStatus(name: "Printed", param: 'printed', true)
            createInvoiceStatus(name: "Payed", param: 'payed', true)
            createPaymentTerm(name: "10 days", param: '10', true)
            createPaymentTerm(name: "20 days", param: '20', true)
            createPaymentTerm(name: "30 days", param: '30', true)

            log.debug "crmInvoiceService finished setup in tenant ${tenant}"
        }
    }

    @Listener(namespace = "crmTenant", topic = "requestDelete")
    def requestDeleteTenant(event) {
        def tenant = event.id
        def count = 0
        count += CrmInvoice.countByTenantId(tenant)
        count += CrmInvoiceStatus.countByTenantId(tenant)
        count += CrmPaymentTerm.countByTenantId(tenant)
        count ? [namespace: 'crmInvoice', topic: 'deleteTenant'] : null
    }

    @Listener(namespace = "crmInvoice", topic = "deleteTenant")
    def deleteTenant(event) {
        def tenant = event.id
        def count = CrmInvoice.countByTenantId(tenant)
        // Remove all invoices
        CrmInvoice.findAllByTenantId(tenant)*.delete()
        // Remove lookup codes.
        CrmInvoiceStatus.findAllByTenantId(tenant)*.delete()
        CrmPaymentTerm.findAllByTenantId(tenant)*.delete()
        log.warn("Deleted $count invoices in tenant $tenant")
    }

    /**
     * Empty query = search all records.
     *
     * @param params pagination parameters
     * @return List or CrmInvoice domain instances
     */
    @Selectable
    def list(Map params = [:]) {
        executeCriteria('list', [:], params)
    }

    /**
     * Find CrmInvoice instances filtered by query.
     *
     * @param query filter parameters
     * @param params pagination parameters
     * @return List or CrmInvoice domain instances
     */
    @Selectable
    def list(Map query, Map params) {
        executeCriteria('list', query, params)
    }

    /**
     * Count number of CrmInvoice instances filtered by query.
     *
     * @param query filter parameters
     * @return number of CrmInvoice
     */
    def count(Map query = [:]) {
        executeCriteria('count', query, null)
    }

    private Object executeCriteria(String criteriaMethod, Map query, Map params) {
        def tagged

        if (query.tags) {
            tagged = crmTagService.findAllByTag(CrmInvoice, query.tags).collect { it.id }
            if (!tagged) {
                // No need to continue with the query if tags don't match.
                return new PagedResultList([])
            }
        }

        final Closure criteria = {
            eq('tenantId', TenantUtils.tenant)
            if (query.id) {
                eq('id', Long.valueOf(query.id))
            } else if (tagged) {
                inList('id', tagged)
            }
            if (query.number) {
                eq('number', query.number)
            }
            if (query.customer) {
                if (crmCoreService.isDomainClass(query.customer)
                        || crmCoreService.isDomainReference(query.customer)) {
                    eq('customerRef', crmCoreService.getReferenceIdentifier(query.customer))
                } else {
                    or {
                        ilike('customerFirstName', SearchUtils.wildcard(query.customer))
                        ilike('customerLastName', SearchUtils.wildcard(query.customer))
                        ilike('customerCompany', SearchUtils.wildcard(query.customer))
                    }
                }
            }
            if (query.address) {
                or {
                    ilike('invoice.address1', SearchUtils.wildcard(query.address))
                    ilike('invoice.address2', SearchUtils.wildcard(query.address))
                    ilike('invoice.postalCode', SearchUtils.wildcard(query.address))
                    ilike('invoice.city', SearchUtils.wildcard(query.address))

                    ilike('delivery.address1', SearchUtils.wildcard(query.address))
                    ilike('delivery.address2', SearchUtils.wildcard(query.address))
                    ilike('delivery.postalCode', SearchUtils.wildcard(query.address))
                    ilike('delivery.city', SearchUtils.wildcard(query.address))
                }
            }
            if (query.email) {
                ilike('customerEmail', SearchUtils.wildcard(query.email))
            }
            if (query.telephone) {
                ilike('customerTel', SearchUtils.wildcard(query.telephone))
            }
            if (query.fromDate && query.toDate) {
                def d1 = DateUtils.parseSqlDate(query.fromDate)
                def d2 = DateUtils.parseSqlDate(query.toDate)
                between('invoiceDate', d1, d2)
            } else if (query.fromDate) {
                def d1 = DateUtils.parseSqlDate(query.fromDate)
                ge('invoiceDate', d1)
            } else if (query.toDate) {
                def d2 = DateUtils.parseSqlDate(query.toDate)
                le('invoiceDate', d2)
            }

            if (query.status) {
                invoiceStatus {
                    or {
                        eq('param', query.status)
                        ilike('name', SearchUtils.wildcard(query.status))
                    }
                }
            }

            if (query.paymentTerm) {
                paymentTerm {
                    or {
                        eq('param', query.delivery)
                        ilike('name', SearchUtils.wildcard(query.delivery))
                    }
                }
            }

            if (query.reference) {
                eq('ref', crmCoreService.getReferenceIdentifier(query.reference))
            } else if (query.ref) {
                eq('ref', query.ref)
            } else if (query.referenceType) {
                def rt = crmCoreService.getReferenceType(query.referenceType)
                ilike('ref', rt + '@%')
            }
        }

        switch (criteriaMethod) {
            case 'count':
                return CrmInvoice.createCriteria().count(criteria)
            case 'get':
                return CrmInvoice.createCriteria().get(params, criteria)
            default:
                return CrmInvoice.createCriteria().list(params, criteria)
        }
    }

    CrmInvoice createInvoice(Map params, boolean save = false) {
        def tenant = TenantUtils.tenant
        def m = new CrmInvoice(tenantId: tenant)

        if(params.status && ! params.invoiceStatus) {
            params.invoiceStatus = params.status
        }
        if(! params.invoiceStatus) {
            params.invoiceStatus = 'created'
        }
        if (params.invoiceStatus && !(params.invoiceStatus instanceof CrmInvoiceStatus)) {
            def status = getInvoiceStatus(params.invoiceStatus.toString())
            if (status) {
                params.invoiceStatus = status
            }
        }

        if (params.paymentTerm && !(params.paymentTerm instanceof CrmPaymentTerm)) {
            def term = getPaymentTerm(params.paymentTerm.toString())
            if (term) {
                params.paymentTerm = term
            }
        }

        grailsWebDataBinder.bind(m, params as SimpleMapDataBindingSource, null, CrmInvoice.BIND_WHITELIST, null, null)

        if(params.reference) {
            m.setReference(params.reference)
        }

        def customer = params.customer
        if (customer instanceof CrmContactInformation) {
            final Map customerParams = [
                    customerRef      : crmCoreService.getReferenceIdentifier(customer),
                    customerNumber   : customer.getNumber(),
                    customerFirstName: customer.getFirstName(),
                    customerLastName : customer.getLastName(),
                    customerCompany  : customer.getCompanyName(),
                    customerTel      : customer.getTelephone(),
                    customerEmail    : customer.getEmail()
            ]
            println "Invoice customer: $customerParams"
            grailsWebDataBinder.bind(m, customerParams as SimpleMapDataBindingSource)
        }

        if (save) {
            if(m.save(flush: true)) {
                event(for: 'crmInvoice', topic: 'created', data: m.dao)
            }
        } else {
            m.validate()
            m.clearErrors()
        }
        return m
    }

    CrmInvoiceItem addInvoiceItem(CrmInvoice invoice, Map params, boolean save = false) {
        def m = new CrmInvoiceItem(invoice: invoice)

        grailsWebDataBinder.bind(m, params as SimpleMapDataBindingSource, null, CrmInvoiceItem.BIND_WHITELIST, null, null)

        if (m.orderIndex == null) {
            m.orderIndex = (invoice.items ? invoice.items.collect{it.orderIndex}.max() : 0) + 1
        }

        if (m.validate()) {
            invoice.addToItems(m)
            if (invoice.validate() && save) {
                invoice.save(flush: true)
                event(for: 'crmInvoice', topic: 'updated', data: m.dao)
            }
        }
        return m
    }

    void cancelInvoice(CrmInvoice invoice) {
        throw new UnsupportedOperationException("CrmInvoiceService#cancelInvoice() not implemented")
    }

    CrmInvoiceStatus getInvoiceStatus(String param) {
        CrmInvoiceStatus.findByParamAndTenantId(param, TenantUtils.tenant, [cache: true])
    }

    CrmInvoiceStatus createInvoiceStatus(Map params, boolean save = false) {
        if (!params.param && params.name) {
            params.param = paramify(params.name, new CrmInvoiceStatus().constraints.param.maxSize)
        }
        def tenant = TenantUtils.tenant
        def m = CrmInvoiceStatus.findByParamAndTenantId(params.param, tenant)
        if (!m) {
            m = new CrmInvoiceStatus(tenantId: tenant)
            grailsWebDataBinder.bind(m, params as SimpleMapDataBindingSource, null, CrmInvoiceStatus.BIND_WHITELIST, null, null)
            if (params.enabled == null) {
                m.enabled = true
            }
            if (save) {
                m.save()
            } else {
                m.validate()
                m.clearErrors()
            }
        }
        return m
    }

    CrmPaymentTerm getPaymentTerm(String param) {
        CrmPaymentTerm.findByParamAndTenantId(param, TenantUtils.tenant, [cache: true])
    }

    CrmPaymentTerm createPaymentTerm(Map params, boolean save = false) {
        if (!params.param && params.name) {
            params.param = paramify(params.name, new CrmPaymentTerm().constraints.param.maxSize)
        }
        def tenant = TenantUtils.tenant
        def m = CrmPaymentTerm.findByParamAndTenantId(params.param, tenant)
        if (!m) {
            m = new CrmPaymentTerm(tenantId: tenant)
            grailsWebDataBinder.bind(m, params as SimpleMapDataBindingSource, null, CrmPaymentTerm.BIND_WHITELIST, null, null)
            if (params.enabled == null) {
                m.enabled = true
            }
            if (save) {
                m.save()
            } else {
                m.validate()
                m.clearErrors()
            }
        }
        return m
    }

    @CompileStatic
    private String paramify(final String name, Integer maxSize = 20) {
        String param = name.toLowerCase().replace(' ', '-')
        if (param.length() > maxSize) {
            param = param[0..(maxSize - 1)]
            if (param[-1] == '-') {
                param = param[0..-2]
            }
        }
        param
    }
}
