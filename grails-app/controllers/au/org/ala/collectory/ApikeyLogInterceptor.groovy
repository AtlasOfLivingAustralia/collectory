/*
 * Copyright (C) 2022 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.collectory
import javax.servlet.http.HttpServletRequest

/**
 * This interceptor can be removed after Apikey is fully removed from systems
 */
class ApikeyLogInterceptor {

    ApiKeyLogService apiKeyLogService

    ApikeyLogInterceptor(){
        match(controller: 'data', action: "saveEntity")
        match(controller:'data', action:"syncGBIF")
        match('controller':'data', action: 'updateContact')
        match('controller':'data', action: 'updateContactFor')
        match('controller':'data', action: 'contacts')
        match(controller:'gbif', action:"scan")
        match(controller:'ipt', action:"scan")
        order = -1

    }
    boolean before() {
        // Record API key usage in the database
        if (grailsApplication.config.legacy.apikey.log) {
            def apiKey = getApiKey(params, request)
            if (apiKey) {
                String controller = params.controller
                String action = params.action + " [${request.method}]"
                def referer = request.getHeader("Referer")
                def remoteAddr = request.getRemoteAddr()
                def remoteHost = request.getRemoteHost()
                def xForwardedFor = request.getHeader("X-Forwarded-For")
                def apiKeyLog = new ApiKeyLog(remoteReferer: referer, remoteAddr: remoteAddr, remoteHost: remoteHost, remoteForwardedFor: xForwardedFor, controllerName: controller, actionName: action)
                apiKeyLogService.save(apiKeyLog)
            }
        }
        return true

        // Leave those codes for checking which roles are required for certain actions
        // and if the API key is valid for the request.
        // This is not used in the current implementation but can be used in the future.
        /*
        def requiredRole = grailsApplication.config.ROLE_EDITOR
        if (params.apiKey) {
            requiredRole = grailsApplication.config.gbifRegistrationRole
        }
        if (params.api_key) {
            requiredRole = grailsApplication.config.gbifRegistrationRole
        }
        */
      /*  // set default role requirement for protected ROLE_EDITOR as the same info is only available to ROLE_EDITOR via the UI.
        String requiredRole = grailsApplication.config.ROLE_EDITOR

        // set gbifRegistrationRole role requirement for GBIF  operations
        if(controllerName == 'gbif' || actionName == 'syncGBIF'){
            requiredRole = grailsApplication.config.gbifRegistrationRole
        }

        // set ROLE_ADMIN role requirement for certain controllers and actions as per admin UI
        if( controllerName == 'ipt'){
            requiredRole = grailsApplication.config.ROLE_ADMIN
        }

        if (collectoryAuthService.isAuthorisedWsRequest(params, request, response, requiredRole, null)) {
            return true
        }
        log.warn("Denying access to $actionName from remote addr: ${request.remoteAddr}, remote host: ${request.remoteHost}")
        response.sendError(HttpStatus.SC_UNAUTHORIZED)
        return false*/
    }

    boolean after() { true }

    void afterView() {
        // no-op
    }

    private static String getApiKey(params, HttpServletRequest request) {
        def apiKey = {
            // handle api keys if present in params
            if (request.JSON && !(request.JSON instanceof List) && request.JSON.api_key) {
                request.JSON.api_key
            } else if (request.JSON && !(request.JSON instanceof List) && request.JSON.apiKey) {
                request.JSON.apiKey
            } else if (params.api_key) {
                params.api_key
            }else if (params.apiKey) {
                params.apiKey
                // handle api keys if present in cookie
            } else  if (request.cookies.find { cookie -> cookie.name == "ALA-API-Key" }){
                def cookieApiKey = request.cookies.find { cookie -> cookie.name == "ALA-API-Key" }
                cookieApiKey.value
            }
        }.call()
        apiKey
    }
}
