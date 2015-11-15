package grails.plugins.crm.invoice

/**
 * Tests for CrmOrderService.
 */
class CrmInvoiceServiceSpec extends grails.test.spock.IntegrationSpec {

    def crmInvoiceService

    def "create invoice"() {
        given:
        def s = crmInvoiceService.createInvoiceStatus(name: "Invoice", true)
        def t = crmInvoiceService.createPaymentTerm(name: "30", true)

        when:
        def i = crmInvoiceService.createInvoice(customer: "ACME Inc.", invoiceStatus: s, paymentTerm: t,  true)

        then:
        i.ident() != null

        when:
        crmInvoiceService.addInvoiceItem(i, [orderIndex: 1, productId: "water", productName: "Fresh water", unit: "l", quantity: 10, price: 10, vat: 0.25], false)
        crmInvoiceService.addInvoiceItem(i, [orderIndex: 2, productId: "air", productName: "Fresh air", unit: "m3", quantity: 100, price: 1, vat: 0.25], false)

        then:
        i.items.size() == 2
        i.totalAmount == 200
        i.totalVat == 50
    }
}
