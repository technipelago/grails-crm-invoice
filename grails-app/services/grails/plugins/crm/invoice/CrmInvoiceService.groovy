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
import grails.plugins.crm.core.*
import grails.plugins.selection.Selectable
import groovy.transform.CompileStatic
import org.grails.databinding.SimpleMapDataBindingSource

/**
 * Name says it all, invoicing features.
 */
class CrmInvoiceService {

    private static final List INVOICE_ADDRESS_BIND_WHITELIST = ['addressee'] + CrmEmbeddedAddress.BIND_WHITELIST

    def grailsApplication
    def grailsWebDataBinder
    def messageSource

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
                ilike('number', SearchUtils.wildcard(query.number))
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

        if (params.status && !params.invoiceStatus) {
            params.invoiceStatus = params.status
        }
        if (!params.invoiceStatus) {
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

        if (params.reference) {
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
            grailsWebDataBinder.bind(m, customerParams as SimpleMapDataBindingSource)
        }

        for (item in params.items) {
            def row = addInvoiceItem(m, item, false)
            if (row.hasErrors()) {
                log.error("Validation error for invoice item", row.errors.allErrors.toString())
            }
        }

        if (save) {
            if (m.save(flush: true)) {
                String username = crmSecurityService.currentUser?.username
                event(for: 'crmInvoice', topic: 'created', data: m.dao + [user: username])
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
            m.orderIndex = (invoice.items ? invoice.items.collect { it.orderIndex }.max() : 0) + 1
        }

        if (m.validate()) {
            invoice.addToItems(m)
            // invoice.validate() is crucial here, it updates totalAmount and totalVat on the invoice,
            // even if we're not saving the instance.
            if (invoice.validate() && save) {
                invoice.save(flush: true)
                String username = crmSecurityService.currentUser?.username
                event(for: 'crmInvoice', topic: 'updated', data: m.dao + [user: username])
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

    CrmInvoice getInvoice(Long id, Long tenant = null) {
        if (tenant == null) {
            tenant = TenantUtils.tenant
        }
        final CrmInvoice m = CrmInvoice.get(id)
        return (tenant == m?.tenantId) ? m : null
    }

    CrmInvoice findByNumber(String number, Long tenant = null) {
        if (tenant == null) {
            tenant = TenantUtils.tenant
        }
        CrmInvoice.findByNumberAndTenantId(number, tenant)
    }

    private CrmInvoice useInvoiceInstance(CrmInvoice crmInvoice = null) {
        def tenant = TenantUtils.tenant
        if (crmInvoice == null) {
            crmInvoice = new CrmInvoice()
        }
        if (crmInvoice.tenantId) {
            if (crmInvoice.tenantId != tenant) {
                throw new IllegalStateException("The current tenant is [$tenant] and the specified domain instance belongs to another tenant [${crmInvoice.tenantId}]")
            }
        } else {
            crmInvoice.tenantId = tenant
        }
        crmInvoice
    }


    CrmInvoice saveInvoice(CrmInvoice crmInvoice, Map params) {
        crmInvoice = useInvoiceInstance(crmInvoice)
        def tenant = TenantUtils.tenant
        def currentUser = crmSecurityService.getUserInfo()
        def oldStatus = crmInvoice.invoiceStatus

        try {
            bindDate(crmInvoice, 'invoiceDate', params.remove('invoiceDate'), currentUser?.timezone)
            bindDate(crmInvoice, 'dueDate', params.remove('dueDate'), currentUser?.timezone)
        } catch (CrmValidationException e) {
            throw new CrmValidationException(e.message, crmInvoice)
        }

        // Bind "normal" properties.
        grailsWebDataBinder.bind(crmInvoice, params as SimpleMapDataBindingSource, null, CrmInvoice.BIND_WHITELIST, null, null)

        // Bind invoice address
        if (!crmInvoice.invoice) {
            crmInvoice.invoice = new CrmEmbeddedAddress()
        }
        if (params.invoice instanceof Map) {
            grailsWebDataBinder.bind(crmInvoice.invoice, params.invoice as SimpleMapDataBindingSource, null, INVOICE_ADDRESS_BIND_WHITELIST, null, null)
        } else {
            grailsWebDataBinder.bind(crmInvoice.invoice, params as SimpleMapDataBindingSource, 'invoice', INVOICE_ADDRESS_BIND_WHITELIST, null, null)
        }

        // Bind delivery address.
        if (!crmInvoice.delivery) {
            crmInvoice.delivery = new CrmEmbeddedAddress()
        }
        if (params.delivery instanceof Map) {
            grailsWebDataBinder.bind(crmInvoice.delivery, params.delivery as SimpleMapDataBindingSource, null, INVOICE_ADDRESS_BIND_WHITELIST, null, null)
        } else {
            grailsWebDataBinder.bind(crmInvoice.delivery, params as SimpleMapDataBindingSource, 'delivery', INVOICE_ADDRESS_BIND_WHITELIST, null, null)
        }

        if (!crmInvoice.invoice.addressee) {
            crmInvoice.invoice.addressee = crmInvoice.customerName
        }

        // If delivery address is empty, copy invoice address (if configured to do so).
        if (crmInvoice.delivery.empty && grailsApplication.config.crm.invoice.delivery.address.copy == 'invoice') {
            crmInvoice.invoice.copyTo(crmInvoice.delivery)
            if (!crmInvoice.delivery.addressee) {
                crmInvoice.delivery.addressee = crmInvoice.invoice.addressee ?: crmInvoice.customerName
            }
        }

        // Bind items.
        if (params.items instanceof List) {
            for (row in params.items) {
                def item = row.id ? CrmInvoiceItem.get(row.id) : new CrmInvoiceItem(order: crmInvoice)
                grailsWebDataBinder.bind(item, row as SimpleMapDataBindingSource, null, CrmInvoiceItem.BIND_WHITELIST, null, null)
                if (item.id) {
                    item.save()
                } else if (!item.hasErrors()) {
                    crmInvoice.addToItems(item)
                }
            }
        } else {
            bindItems(crmInvoice, params)
        }

        if (!crmInvoice.invoiceStatus) {
            crmInvoice.invoiceStatus = CrmInvoiceStatus.withNewSession {
                CrmInvoiceStatus.createCriteria().get() {
                    eq('tenantId', tenant)
                    order 'orderIndex', 'asc'
                    maxResults 1
                }
            }
        }
        if (!crmInvoice.paymentTerm) {
            crmInvoice.paymentTerm = CrmPaymentTerm.withNewSession {
                CrmPaymentTerm.createCriteria().get() {
                    eq('tenantId', tenant)
                    order 'orderIndex', 'asc'
                    maxResults 1
                }
            }
        }

        if (!crmInvoice.currency) {
            crmInvoice.currency = grailsApplication.config.crm.currency.default ?: "EUR"
        }

        if (!crmInvoice.invoiceDate) {
            crmInvoice.invoiceDate = new java.sql.Date(System.currentTimeMillis())
        }

        // If the order is new or it's status has changed, set the EVENT_CHANGED flag.
        if (grailsApplication.config.crm.invoice.changeEvent) {
            def newStatus = crmInvoice.invoiceStatus
            if (crmInvoice.id == null || oldStatus != newStatus) {
                crmInvoice.event = CrmInvoice.EVENT_CHANGED
            }
        }

        if (crmInvoice.save()) {
            return crmInvoice
        } else {
            // Eager fetch associations to avoid LazyInitializationException
            crmInvoice.items?.size()
        }

        throw new CrmValidationException('crmInvoice.validation.error', crmInvoice)
    }

    private void bindDate(def target, String property, Object value, TimeZone timezone = null) {
        if (value) {
            def tenant = crmSecurityService.getCurrentTenant()
            def locale = tenant?.localeInstance ?: Locale.getDefault()
            try {
                if (value instanceof Date) {
                    target[property] = new java.sql.Date(value.time)
                } else {
                    target[property] = DateUtils.parseSqlDate(value.toString(), timezone)
                }
            } catch (Exception e) {
                def entityName = messageSource.getMessage('crmInvoice.label', null, 'Invoice', locale)
                def propertyName = messageSource.getMessage('crmInvoice.' + property + '.label', null, property, locale)
                target.errors.rejectValue(property, 'default.invalid.date.message', [propertyName, entityName, value.toString(), e.message].toArray(), "Invalid date: {2}")
                throw new CrmValidationException('crmInvoice.invalid.date.message', target)
            }
        } else {
            target[property] = null
        }
    }

    private void bindItems(CrmInvoice crmInvoice, Map params) {
        // This is a workaround for Grails 2.4.4 data binding that does not insert a new CrmInvoiceItem when 'id' is null.
        // I consider this to be a bug in Grails 2.4.4 but I'm not sure how it's supposed to work with Set.
        // This workaround was not needed in Grails 2.2.4.
        int i = 0
        int miss = 0
        while (miss < 10) {
            def a = params["items[$i]".toString()]
            if (a?.id) {
                def item = CrmInvoiceItem.get(a.id)
                if (crmInvoice.id != item?.invoiceId) {
                    throw new RuntimeException("CrmInvoiceItem [${item.invoiceId}] is not associated with CrmInvoice [${crmInvoice.id}]")
                }
                grailsWebDataBinder.bind(item, a as SimpleMapDataBindingSource, null, CrmInvoiceItem.BIND_WHITELIST, null, null)
                item.save()
            } else if (a) {
                def item = new CrmInvoiceItem(invoice: crmInvoice)
                grailsWebDataBinder.bind(item, a as SimpleMapDataBindingSource, null, CrmInvoiceItem.BIND_WHITELIST, null, null)
                if (!item.isEmpty()) {
                    if (item.validate()) {
                        crmInvoice.addToItems(item)
                    } else {
                        for(error in item.errors) {
                            crmInvoice.errors.reject(error.fieldError.toString())
                        }
                    }
                }
            } else {
                miss++
            }
            i++
        }
    }

    /**
     * Update status of an invoice.
     *
     * @param crmInvoice the invoice instance to update
     * @param newStatus new status as a CrmInvoiceStatus instance or a String matching the 'param' property of a CrmInvoiceStatus
     * @return the updated invoice instance
     */
    CrmInvoice updateStatus(CrmInvoice crmInvoice, Object newStatus) {
        CrmInvoiceStatus status
        if (newStatus instanceof CrmInvoiceStatus) {
            status = newStatus
        } else {
            status = getInvoiceStatus(newStatus.toString())
        }

        if (!status) {
            throw new IllegalArgumentException("no such invoice status: $newStatus")
        }

        def oldStatus = crmInvoice.invoiceStatus.dao

        crmInvoice.invoiceStatus = status

        if (crmInvoice.save(flush: true)) {
            def payload = crmInvoice.dao
            payload.changes = [invoiceStatus: [before: oldStatus, after: crmInvoice.invoiceStatus.dao]]
            payload.user = crmSecurityService.currentUser?.username
            event(for: 'crmInvoice', topic: 'updated', data: payload)
        }

        return crmInvoice
    }

    /**
     * Assign a new invoice number using the SequencegeneratorService.
     *
     * @param crmInvoice the invoice instance to assign number to
     * @param group optional sequence number group/series
     * @return the updated invoice instance
     */
    CrmInvoice assignNumber(CrmInvoice crmInvoice, String group = null) {
        def old = crmInvoice.number
        crmInvoice.number = sequenceGeneratorService.nextNumber(CrmInvoice, group, crmInvoice.tenantId)
        if (crmInvoice.save(flush: true)) {
            def payload = crmInvoice.dao
            payload.changes = [number: [before: old, after: crmInvoice.number]]
            payload.user = crmSecurityService.currentUser?.username
            event(for: 'crmInvoice', topic: 'updated', data: payload)
        }
    }

    List<CrmInvoiceStatus> listInvoiceStatus(String name, Map params = [:]) {
        CrmInvoiceStatus.createCriteria().list(params) {
            eq('tenantId', TenantUtils.tenant)
            if (name) {
                or {
                    ilike('name', SearchUtils.wildcard(name))
                    eq('param', name)
                }
            }
        }
    }

    List<CrmPaymentTerm> listPaymentTerm(String name, Map params = [:]) {
        CrmPaymentTerm.createCriteria().list(params) {
            eq('tenantId', TenantUtils.tenant)
            if (name) {
                or {
                    ilike('name', SearchUtils.wildcard(name))
                    eq('param', name)
                }
            }
        }
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
