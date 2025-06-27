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

import org.apache.http.HttpStatus


class GBIFInterceptor {
    int order = 1 // after the TokenInterceptor

    CollectoryAuthService collectoryAuthService

    GBIFInterceptor(){
        match(controller:'gbif', action:"syncGBIF")
        match(controller:'gbif', action:"healthCheck")
        match(controller:'gbif', action:"healthCheckLinked")
        match(controller:'gbif', action:"downloadCSV")
    }
    boolean before() {
        if (collectoryAuthService.userInRole(grailsApplication.config.gbifRegistrationRole)) {
            return true
        }
        log.warn("Denying access to $actionName from remote addr: ${request.remoteAddr}, remote host: ${request.remoteHost}")
        response.sendError(HttpStatus.SC_UNAUTHORIZED)
        return false
    }

    boolean after() { true }

    void afterView() {
        // no-op
    }
}