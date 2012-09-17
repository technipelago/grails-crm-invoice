class CrmInvoiceGrailsPlugin {
    // Dependency group
    def groupId = "grails.crm"
    // the plugin version
    def version = "1.0-SNAPSHOT"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.0 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/conf/ApplicationResources.groovy",
            "grails-app/services/grails/plugins/crm/invoice/TestSecurityService.groovy",
            "grails-app/views/error.gsp"
    ]

    def title = "Grails CRM Invoice Plugin"
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''\
Simple invoice administration for Grails CRM.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/crm-invoice"
    def license = "APACHE"
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]
    def issueManagement = [system: "github", url: "https://github.com/goeh/grails-crm-invoice/issues"]
    def scm = [url: "https://github.com/goeh/grails-crm-invoice"]

    def features = {
        crmInvoice {
            description "Invoice Management"
            link controller: "crmInvoice", action: "index"
            permissions {
                guest "crmInvoice:index,list,show"
                user "crmInvoice:*"
                admin "crmInvoice:*"
            }
            hidden true
        }
    }
}
