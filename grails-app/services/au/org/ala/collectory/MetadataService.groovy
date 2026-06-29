/*
 * Copyright (C) 2011 Atlas of Living Australia
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

import grails.converters.JSON
import grails.plugin.cache.Cacheable

class MetadataService {

    static transactional = false

    // cache connection metadata
    def grailsApplication
    def connectionProfileMetadata = null
    def connectionParameterMetadata = null

    /**
     * Return object representing a connection profile with connection parameters.
     * @param profileName name of the profile
     */
    def getConnectionProfile(profileName) {
        checkConnectionMetadata()
        def pr = connectionProfileMetadata[profileName]
        // create a clone to hold the full param objects (so we don't change the cached profile)
        def clone = [name:pr.name, display:pr.display]
        if (pr) {
            clone.params = pr.params.collect { p ->
                connectionParameterMetadata[p.toString()]
            }
        }
        clone
    }

    def convertAnyLocalPaths(obj){
        def oldPath = "file:///" + grailsApplication.config.uploadFilePath
        def newPath = resolveUploadExternalUrl()
        if(obj in String){
            obj.replaceAll(oldPath, newPath)
        } else if(obj in JSON){
            obj.toString().replaceAll(oldPath, newPath)
        }
    }

    def convertPath(obj){
        def oldPath = "file:///" + grailsApplication.config.uploadFilePath
        def newPath = resolveUploadExternalUrl()
        obj.replaceAll(oldPath,newPath)
    }

    /**
     * Resolves the external base URL used for uploaded files.
     *
     * If `uploadExternalUrlPath` is not configured, the application `serverURL`
     * is returned. If an absolute HTTP(S) URL is configured, its path is combined
     * with the scheme and authority from `serverURL`. Otherwise, the configured
     * relative path is appended to `serverURL`.
     *
     * @return the resolved external upload URL
     */
    private String resolveUploadExternalUrl() {
        def external = grailsApplication.config.uploadExternalUrlPath?.toString()
        if (!external) {
            return grailsApplication.config.grails.serverURL
        }

        if (external.startsWith('http://') || external.startsWith('https://')) {
            def serverUri = new URI(grailsApplication.config.grails.serverURL.toString())
            def externalUri = new URI(external)
            def basePath = (serverUri.path && serverUri.path != '/') ? serverUri.path : ''
            def uploadPath = externalUri.path.endsWith('/') ? externalUri.path : externalUri.path + '/'
            def resolvedPath = (basePath.endsWith('/') ? basePath[0..-2] : basePath) + uploadPath
            return new URI(serverUri.scheme, serverUri.authority, resolvedPath, externalUri.query, externalUri.fragment).toString()
        }

        return grailsApplication.config.grails.serverURL + (external.endsWith('/') ? external : external + '/')
    }

    def getConnectionProfiles() {
        checkConnectionMetadata()
        return connectionProfileMetadata
    }

    def getConnectionProfilesAsList() {
        getConnectionProfiles().values().toList()
    }

    def getConnectionProfilesWithFileUpload() {
        getConnectionProfiles()?.values().toList().findAll({ it.supportFileUpload })
    }

    private checkConnectionMetadata() {
        if (!connectionProfileMetadata) {
            loadConnectionMetadata()
        }
    }

    private loadConnectionMetadata() {
        def json = null;
        def path = "/data/" + grailsApplication.config.grails.appName + "/config/connection-profiles.json";
        //def path = "/data/ala-collectory/config/connection-profiles.json";
        log.info "Loading connection profiles and parameters from disk from path: " + path
        def pFile = new File(path)
        if(pFile.exists())
            json = pFile.text

        if(json != null) {
            def md = JSON.parse(json)
            // TODO: handle errors
            // load as map for quick lookup
            connectionProfileMetadata = md.profiles.inject([:]) {map, pr -> map << [(pr.name): pr]}
            connectionParameterMetadata = md.parameters.inject([:]) {map, pa -> map << [(pa.name): pa]}
        } else {
            log.info "Connection profiles does not exist under " + path;
        }
    }

    def clearConnectionProfiles() {
        connectionProfileMetadata = null
    }

    def getConnectionParameters() {
        checkConnectionMetadata()
        return connectionParameterMetadata
    }

    def getConnectionParameter(name) {
        checkConnectionMetadata()
        return connectionParameterMetadata[name]
    }

    def clearConnectionParameters() {
        connectionParameterMetadata = null
    }
}
