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
import grails.plugins.crm.core.DateUtils
import grails.plugins.crm.core.SearchUtils
import grails.plugins.crm.core.TenantUtils
import org.codehaus.groovy.grails.web.metaclass.BindDynamicMethod

/**
 * Name says it all, invoicing features.
 */
class CrmInvoiceService {

    def crmSecurityService
    def crmTagService

    @Listener(namespace = "crmInvoice", topic = "enableFeature")
    def enableFeature(event) {
        // event = [feature: feature, tenant: tenant, role:role, expires:expires]
        def tenant = crmSecurityService.getTenantInfo(event.tenant)
        TenantUtils.withTenant(tenant.id) {
            crmTagService.createTag(name: CrmInvoice.name, multiple: true)
        }
    }

    /**
     * Empty query = search all records.
     *
     * @param params pagination parameters
     * @return List or CrmInvoice domain instances
     */
    def list(Map params = [:]) {
        list([:], params)
    }

    /**
     * Find CrmInvoice instances filtered by query.
     *
     * @param query filter parameters
     * @param params pagination parameters
     * @return List or CrmInvoice domain instances
     */
    def list(Map query, Map params) {
        def tagged

        if (query.tags) {
            tagged = crmTagService.findAllByTag(CrmInvoice, query.tags).collect {it.id}
            if (!tagged) {
                tagged = [0L] // Force no search result.
            }
        }

        CrmInvoice.createCriteria().list(params) {
            eq('tenantId', TenantUtils.tenant)
            if (tagged) {
                inList('id', tagged)
            }
            if (query.number) {
                ilike('number', SearchUtils.wildcard(query.number))
            }
            if (query.customer) {
                customer {
                    ilike('name', SearchUtils.wildcard(query.customer))
                }
            }
            if (params.fromDate && params.toDate) {
                def d1 = DateUtils.parseSqlDate(params.fromDate)
                def d2 = DateUtils.parseSqlDate(params.toDate)
                between('invoiceDate', d1, d2)
            } else if (params.fromDate) {
                def d1 = DateUtils.parseSqlDate(params.fromDate)
                ge('invoiceDate', d1)
            } else if (params.toDate) {
                def d2 = DateUtils.parseSqlDate(params.toDate)
                le('invoiceDate', d2)
            }
        }
    }

    CrmInvoice createInvoice(Map params, boolean save = false) {
        def tenant = TenantUtils.tenant
        def m = new CrmInvoice()
        def args = [m, params]
        new BindDynamicMethod().invoke(m, 'bind', args.toArray())
        m.tenantId = tenant

        if (m.invoice == null) {

        }

        if (save) {
            m.save()
        } else {
            m.validate()
            m.clearErrors()
        }
        return m
    }

    CrmInvoiceItem addInvoiceItem(CrmInvoice invoice, Map params, boolean save = false) {
        def m = new CrmInvoiceItem(invoice:invoice)
        def args = [m, params]
        new BindDynamicMethod().invoke(m, 'bind', args.toArray())
        if (m.validate()) {
            invoice.addToItems(m)
            if (save && invoice.validate()) {
                invoice.save()
            }
        }
        return m
    }

    void cancelInvoice(CrmInvoice invoice) {
        throw new UnsupportedOperationException("CrmInvoiceService#cancelInvoice() not implemented")
    }
}
