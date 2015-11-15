class CrmInvoiceGrailsPlugin {
    // Dependency group
    def groupId = ""
    // the plugin version
    def version = "2.4.0-SNAPSHOT"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.4 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/conf/ApplicationResources.groovy",
            "grails-app/services/grails/plugins/crm/invoice/TestSecurityService.groovy",
            "grails-app/views/error.gsp"
    ]

    def title = "GR8 CRM Invoice Services"
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''\
Simple invoice administration for GR8 CRM applications.
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
