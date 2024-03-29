package au.org.ala.collectory

import groovy.json.JsonSlurper

class CitationsTagLib {

    static namespace = 'citations'

    def gbifLink = { attrs ->
        def gbifUrl = """${grailsApplication.config.gbif.citations.lookup}${attrs.gbifRegistryKey}"""
        try {
            if (grailsApplication.config.gbif.citations.enabled.toBoolean()) {
                def js = new JsonSlurper()
                def data = js.parse(new URL(gbifUrl))
                if (data.count) {
                    out << """<a class="btn btn-default" href="${grailsApplication.config.gbif.citations.search}${attrs.gbifRegistryKey}">&nbsp;<span class="glyphicon glyphicon-bullhorn"></span>&nbsp; ${data.count} ${g.message(code:"citations.available", default:"citations for these data")}</a>"""
                }
            }
        } catch (Exception e){
            log.error("Problem retrieving citation count from GBIF" + e.getMessage(), e)
        }
    }

    /**
     * Convert the gbifDoi value to a DOI URL (link)
     *
     * @attr gbifDoi REQUIRED the gbifDoi value
     */
    def doiLink = { attrs, body ->
        String gbifDoi = (attrs.gbifDoi as String).toLowerCase()
        String doiUrl

        if (gbifDoi.startsWith("https://doi")) {
            // properly formatted DOI
            doiUrl = gbifDoi
        } else if (gbifDoi.startsWith("doi")) {
            // Old GBIF DOI API which used a "doi:" prefix
            doiUrl = "https://${gbifDoi.replaceAll('doi:', 'doi.org/')}"
        } else {
            // New GBIF DOI API provides the DOI "path" only
            doiUrl = "https://doi.org/${gbifDoi}"
        }

        out << doiUrl
    }
}
