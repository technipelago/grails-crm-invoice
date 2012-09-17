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

import grails.plugins.crm.contact.CrmEmbeddedAddress
import grails.plugins.crm.contact.CrmContact
import grails.plugins.crm.core.AuditEntity
import grails.plugins.crm.core.TenantEntity
import grails.plugins.sequence.SequenceEntity

/**
 * Invoice domain class.
 */
@TenantEntity
@AuditEntity
@SequenceEntity
class CrmInvoice {

    String number
    String description

    String orderNumber
    java.sql.Date orderDate
    java.sql.Date deliveryDate
    java.sql.Date invoiceDate
    java.sql.Date dueDate
    java.sql.Date paymentDate

    String reference1
    String reference2
    String reference3
    String reference4

    //CrmInvoiceType invoiceType
    //CrmInvoiceStatus invoiceStatus
    //CrmPaymentTerm paymentTerm
    //CrmDeliveryTerm deliveryTerm
    //CrmDeliveryType deliveryType

    CrmContact customer
    String customerTel
    String customerEmail

    CrmEmbeddedAddress invoice
    CrmEmbeddedAddress delivery

    Float invoiceAmount = 0f
    Float vatAmount = 0f
    Float payedAmount = 0f
    String paymentType
    String paymentId

    SortedSet items

    static embedded = ['invoice', 'delivery']

    static hasMany = [items: CrmInvoiceItem]

    static constraints = {
        number(maxSize: 20, nullable: true, unique: 'tenantId')
        description(maxSize: 2000, nullable: true, widget: 'textarea')
        orderNumber(maxSize: 20, nullable: true)
        orderDate(nullable: true)
        deliveryDate(nullable: true)
        invoiceDate(nullable: true)
        dueDate(nullable: true)
        paymentDate(nullable: true)
        reference1(maxSize: 80, nullable: true)
        reference2(maxSize: 80, nullable: true)
        reference3(maxSize: 80, nullable: true)
        reference4(maxSize: 80, nullable: true)
        customer()
        customerTel(maxSize: 20, nullable: true)
        customerEmail(maxSize: 80, nullable: true, email: true)
        invoiceAmount(min: -999999f, max: 999999f, scale: 2)
        vatAmount(min: -999999f, max: 999999f, scale: 2)
        payedAmount(min: -999999f, max: 999999f, scale: 2)
        paymentType(maxSize: 50, nullable: true)
        paymentId(maxSize: 100, nullable: true)
        invoice(nullable: true)
        delivery(nullable: true)
    }

    static mapping = {
        sort 'number'
        items sort: 'orderIndex', 'asc'
    }

    //static transients = ['']

    def beforeValidate() {
        if (!number) {
            number = getNextSequenceNumber()
        }
        invoiceAmount = calculateAmount()
        if (invoice == null && customer != null) {
            def customerAddress = customer.address
            if (customerAddress) {
                invoice = new CrmEmbeddedAddress(customerAddress)
            }
        }
    }

    static taggable = true
    static attachmentable = true
    static dynamicProperties = true

    Float calculateAmount() {
        float sum = 0
        for (item in items) {
            sum += item.totalPrice
        }
        return sum
    }

    String toString() {
        number.toString()
    }
}
