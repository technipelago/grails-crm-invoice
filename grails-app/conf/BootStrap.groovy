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

import grails.plugins.crm.invoice.CrmInvoice

class BootStrap {

    def crmInvoiceService
    def crmProductService
    def crmContactService
    def crmTagService

    def init = { servletContext ->

        crmTagService.createTag(name: CrmInvoice.name, multiple: true)

        def hardware = crmProductService.createProductGroup(name: "Hardware", true)

        def personal = crmProductService.createPriceList(name: "Personal", true)

        def apple = crmContactService.createCompany(name: "Apple").save(failOnError: true)

        def iPhone4s = crmProductService.createProduct(number: "iPhone4s", name: "iPhone 4S 16 GB Black Unlocked", group: hardware, supplier: apple, suppliersNumber: "MD235KS/A", weight: 0.14)
        iPhone4s.addToPrices(priceList: personal, unit: "pcs", fromAmount: 1, inPrice: 0, outPrice: 3068.8, vat: 0.25)
        iPhone4s.save(failOnError: true)

        def customer = crmContactService.createCompany(name: "ACME Inc.",
                address: [address1: "Grails Street 211", postalCode: "21123", city: "Alice Springs"], telephone: "+1(234)56789", email: "info@acme.com").save(failOnError: true)
        def contact = crmContactService.createPerson(parent: customer, firstName: "Joe", lastName: "Average", title: "Software Engineer",
                email: "joe@acme.com").save(failOnError: true)

        def invoice = crmInvoiceService.createInvoice(customer: customer, customerTel: contact.preferredPhone, customerEmail: contact.email)
        crmInvoiceService.addInvoiceItem(invoice, [orderIndex: 1, productNumber: iPhone4s.number, productName: iPhone4s.name,
                unit: "item", quantity: 1, price: iPhone4s.getPrice(personal), vat: iPhone4s.getVat(personal)])
        invoice.save(failOnError: true)

        println "Invoice #$invoice created for ${invoice.customer} at ${invoice.invoice}"
    }

    def destroy = {

    }
}