/*
 * Copyright (c) 2016 Goran Ehrsson.
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

class CrmInvoiceGrailsPlugin {
    def groupId = ""
    def version = "2.4.1-SNAPSHOT"
    def grailsVersion = "2.4 > *"
    def dependsOn = [:]
    def pluginExcludes = [
            "grails-app/services/grails/plugins/crm/invoice/TestSecurityService.groovy",
            "grails-app/views/error.gsp"
    ]

    def title = "GR8 CRM Invoice Management Services"
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''\
Simple invoice administration for GR8 CRM applications.
'''

    // URL to the plugin's documentation
    def documentation = "http://gr8crm.github.io/plugins/crm-invoice"
    def license = "APACHE"
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]
    def issueManagement = [system: "github", url: "https://github.com/technipelago/grails-crm-invoice/issues"]
    def scm = [url: "https://github.com/technipelago/grails-crm-invoice"]

    def features = {
        crmInvoice {
            description "Invoice Management"
            hidden true
        }
    }
}
