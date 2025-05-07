package au.org.ala.collectory

import au.ala.org.ws.security.RequireApiKey
import au.org.ala.web.AlaSecured
import au.org.ala.ws.service.WebService
import grails.converters.JSON

class ApiKeyLogController {
    ApiKeyLogService apiKeyLogService
    WebService webService

    @AlaSecured(value = 'ROLE_ADMIN', redirectController = 'public', redirectAction = 'warning', message = "You don’t have permission to view this statistics page for legacy API key usage.")
    def index() {
        params.sort = params.sort ?: 'lastCalled'
        params.order = params.order ?: 'desc'
        def logs = apiKeyLogService.list(params)
        render view: 'index', model: [logs: logs]
    }

    def roleTest(String dest, String method) {
        String url = "${grailsApplication.config.grails.serverURL}${dest}"
        try {
            Map result = [:]
            if (method == 'POST') {
                result = webService.post(url, [:])
            } else {
                result =webService.get(url)
            }
            render result as JSON
        } catch (Exception e) {
            render "Error calling URL: ${url} - ${e.message}"
        }
    }

    @RequireApiKey(roles = ["ROLE_Editor"])
    def requireEditorRole() {
        return true
    }
}