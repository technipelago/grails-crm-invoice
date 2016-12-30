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

import grails.plugins.crm.core.*
import grails.plugins.sequence.SequenceEntity
import grails.util.Holders
import groovy.transform.CompileStatic

/**
 * Invoice domain class.
 */
@TenantEntity
@AuditEntity
@SequenceEntity
class CrmInvoice implements Iterable<CrmInvoiceItem> {

    public static final int PAYMENT_STATUS_UNKNOWN = 0
    public static final int PAYMENT_STATUS_OPEN = 1
    public static final int PAYMENT_STATUS_WAIT = 11
    public static final int PAYMENT_STATUS_CASH = 12
    public static final int PAYMENT_STATUS_PARTIAL = 31
    public static final int PAYMENT_STATUS_FULL = 35
    public static final List<Integer> PAYMENT_STATUS_LIST = [PAYMENT_STATUS_UNKNOWN,
                                                             PAYMENT_STATUS_OPEN,
                                                             PAYMENT_STATUS_WAIT,
                                                             PAYMENT_STATUS_CASH,
                                                             PAYMENT_STATUS_PARTIAL,
                                                             PAYMENT_STATUS_FULL]
    public static final int EVENT_RESET = 0
    public static final int EVENT_CHANGED = 1
    public static final int EVENT_PUBLISHED = 2

    public static
    final List<String> BIND_WHITELIST = ['number', 'description', 'orderNumber', 'invoiceDate', 'dueDate', 'paymentDate',
                                         'reference1', 'reference2', 'reference3', 'reference4', 'invoiceStatus', 'paymentTerm', 'paymentStatus', 'payedAmount',
                                         'customerNumber', 'customerRef', 'customerFirstName', 'customerLastName', 'customerCompany', 'customerTel', 'customerEmail',
                                         'invoice', 'delivery', 'totalAmount', 'totalVat', 'currency'
    ].asImmutable()

    private def _crmCoreService

    String number
    String description

    String orderNumber
    java.sql.Date invoiceDate
    java.sql.Date dueDate
    java.sql.Date paymentDate

    String ref // entityName@id
    String reference1
    String reference2
    String reference3
    String reference4

    CrmInvoiceStatus invoiceStatus
    CrmPaymentTerm paymentTerm

    String customerNumber
    String customerRef
    String customerFirstName
    String customerLastName
    String customerCompany
    String customerTel
    String customerEmail

    CrmEmbeddedAddress invoice
    CrmEmbeddedAddress delivery

    String currency

    Float totalAmount = 0f
    Float totalVat = 0f

    int paymentStatus = PAYMENT_STATUS_UNKNOWN
    String paymentType
    String paymentId
    Double payedAmount = 0

    int event = EVENT_RESET

    static embedded = ['invoice', 'delivery']

    static hasMany = [items: CrmInvoiceItem]

    static constraints = {
        number(maxSize: 20, nullable: true, unique: 'tenantId')
        description(maxSize: 2000, nullable: true, widget: 'textarea')
        orderNumber(maxSize: 20, nullable: true)
        invoiceDate(nullable: true)
        dueDate(nullable: true)
        paymentDate(nullable: true)
        ref(maxSize: 80, nullable: true)
        reference1(maxSize: 80, nullable: true)
        reference2(maxSize: 80, nullable: true)
        reference3(maxSize: 80, nullable: true)
        reference4(maxSize: 80, nullable: true)
        customerNumber(maxSize: 20, nullable: true)
        customerRef(maxSize: 80, nullable: true)
        customerFirstName(maxSize: 80, nullable: true)
        customerLastName(maxSize: 80, nullable: true)
        customerCompany(maxSize: 80, nullable: true)
        customerTel(maxSize: 20, nullable: true)
        customerEmail(maxSize: 80, nullable: true, email: true)
        currency(maxSize: 4, blank: false)
        totalAmount(min: -999999f, max: 999999f, scale: 2)
        totalVat(min: -999999f, max: 999999f, scale: 2)
        paymentStatus(min: PAYMENT_STATUS_UNKNOWN, max: PAYMENT_STATUS_FULL)
        paymentDate(nullable: true)
        paymentType(maxSize: 40, nullable: true)
        paymentId(maxSize: 80, nullable: true)
        payedAmount(min: -999999d, max: 999999d, scale: 2)
        invoice(nullable: true)
        delivery(nullable: true)
    }

    static mapping = {
        sort 'number'
        customerRef index: 'crm_invoice_customer_idx'
        items sort: 'orderIndex', 'asc'
    }

    static transients = ['reference', 'customer', 'customerName', 'totalAmountVAT', 'dao', 'syncPending', 'syncPublished']

    static taggable = true
    static attachmentable = true
    static dynamicProperties = true

    // Lazy injection of service.
    private CrmCoreService getCrmCoreService() {
        if (_crmCoreService == null) {
            _crmCoreService = this.getDomainClass().getGrailsApplication().getMainContext().getBean('crmCoreService')
        }
        _crmCoreService
    }

    @CompileStatic
    void setReference(object) {
        ref = object ? getCrmCoreService().getReferenceIdentifier(object) : null
    }

    @CompileStatic
    transient Object getReference() {
        ref ? getCrmCoreService().getReference(ref) : null
    }

    transient Double getTotalAmountVAT() {
        def p = totalAmount ?: 0
        def v = totalVat ?: 0
        return p + v
    }

    transient CrmContactInformation getCustomer() {
        getCrmCoreService().getReference(customerRef)
    }

    @CompileStatic
    transient void setCustomer(CrmContactInformation arg) {
        customerRef = getCrmCoreService().getReferenceIdentifier(arg)
    }

    @CompileStatic
    transient void setCustomer(String arg) {
        customerRef = arg
    }

    @CompileStatic
    transient String getCustomerName() {
        def s = new StringBuilder()
        if (customerFirstName) {
            s.append(customerFirstName)
        }
        if (customerLastName) {
            if (s.length()) {
                s.append(' ')
            }
            s.append(customerLastName)
        }
        if (s.length() == 0 && customerRef?.startsWith('crmContact@')) {
            // TODO this is a hack, find a better impl.
            def c = getCustomer()
            if (c) {
                s << c.toString()
            }
        }
        s.toString()
    }

    @CompileStatic
    Iterator<CrmInvoiceItem> iterator() {
        (items ?: Collections.EMPTY_SET).iterator()
    }

    transient Map<String, Object> getDao() {
        def map = BIND_WHITELIST.inject([:]) { m, i ->
            if (i != 'invoice' && i != 'delivery') {
                def v = this."$i"
                if (v != null) {
                    m[i] = v
                }
            }
            m
        }
        map.tenant = getTenantId()
        map.id = id
        map.customer = getCustomer()?.getDao()
        map.customerName = getCustomerName()
        if (invoice != null) {
            map.invoice = invoice.getDao()
        }
        if (delivery != null) {
            map.delivery = delivery.getDao()
        }
        map.totalAmountVAT = getTotalAmountVAT()

        if (paymentDate || payedAmount) {
            map.payedAmount = payedAmount
            map.paymentDate = paymentDate
            map.paymentType = paymentType
            map.paymentId = paymentId
        }

        map.items = items?.collect { it.getDao() }

        return map
    }

    @CompileStatic
    transient boolean isSyncPending() {
        event == EVENT_CHANGED
    }

    @CompileStatic
    void setSyncPending() {
        event = EVENT_CHANGED
    }

    @CompileStatic
    transient boolean isSyncPublished() {
        event == EVENT_PUBLISHED
    }

    @CompileStatic
    void setSyncPublished() {
        event = EVENT_PUBLISHED
    }

    @CompileStatic
    void setNoSync() {
        event = EVENT_RESET
    }

    def beforeValidate() {
        if (!number) {
            number = getNextSequenceNumber()
        }
        if (!invoiceDate) {
            invoiceDate = new java.sql.Date(System.currentTimeMillis())
        }

        if (!currency) {
            currency = Holders.getConfig().crm.currency.default ?: 'EUR'
        }

        def (tot, vat) = calculateAmount()
        totalAmount = tot
        totalVat = vat

        if (invoice == null) {
            def cust = getCustomer()
            if (cust != null) {
                def customerAddress = cust.address
                if (customerAddress) {
                    invoice = new CrmEmbeddedAddress(customerAddress)
                }
            }
        }
    }

    @CompileStatic
    private Pair<Float, Float> calculateAmount() {
        Double sum = 0d
        Double vat = 0d
        if (items) {
            for (item in items) {
                sum += item.totalPrice
                vat += item.totalVat
            }
        }
        return new Pair(sum.floatValue(), vat.floatValue())
    }

    CrmInvoice copy(Boolean credit = false) {
        def original = this
        def invoiceAddress = original.invoice ? original.invoice.copy() : new CrmEmbeddedAddress()
        def deliveryAddress = original.delivery ? original.delivery.copy() : new CrmEmbeddedAddress()
        def crmInvoice = new CrmInvoice(ref: original.ref, invoice: invoiceAddress, delivery: deliveryAddress, invoiceDate: new java.sql.Date(System.currentTimeMillis()))
        def properties = BIND_WHITELIST - ['number', 'dueDate', 'invoiceDate', 'paymentDate', 'invoiceStatus', 'paymentStatus', 'payedAmount']

        for (prop in properties) {
            crmInvoice."$prop" = original."$prop"
        }

        for(item in original.items) {
            def newItem = item.copy(credit)
            if(newItem.validate()) {
                crmInvoice.addToItems(newItem)
            }
        }

        return crmInvoice
    }

    @CompileStatic
    String toString() {
        number.toString()
    }
}
