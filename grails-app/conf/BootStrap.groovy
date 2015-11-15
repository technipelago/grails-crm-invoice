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
import grails.util.Environment

class BootStrap {

    def crmInvoiceService
    def crmProductService
    def crmTagService

    def init = { servletContext ->

        crmTagService.createTag(name: CrmInvoice.name, multiple: true)

        if (Environment.current == Environment.DEVELOPMENT) {
            def hardware = crmProductService.createProductGroup(name: "Hardware", true)

            def personal = crmProductService.createPriceList(name: "Personal", true)

            def iPhone4s = crmProductService.createProduct(number: "iPhone4s", name: "iPhone 4S 16 GB Black Unlocked", group: hardware, suppliersNumber: "MD235KS/A", weight: 0.14)
            iPhone4s.addToPrices(priceList: personal, unit: "pcs", fromAmount: 1, inPrice: 0, outPrice: 3068.8, vat: 0.25)
            iPhone4s.save(failOnError: true)

            def s = crmInvoiceService.createInvoiceStatus(name: "Invoice", true)
            def t = crmInvoiceService.createPaymentTerm(name: "Web Order", true)

            def invoice = crmInvoiceService.createInvoice(invoiceStatus: s, paymentTerm: t, customer: "ACME Inc.", customerTel: "+4685551234", customerEmail: "customer@acme.com")
            crmInvoiceService.addInvoiceItem(invoice, [orderIndex: 1, productNumber: iPhone4s.number, productName: iPhone4s.name,
                    unit: "item", quantity: 1, price: iPhone4s.getPrice(1,personal), vat: iPhone4s.getVat(personal)])
            invoice.save(failOnError: true)

            println "Invoice #$invoice created for ${invoice.customer} at ${invoice.invoice}"
        }
    }

    def destroy = {

    }
}
