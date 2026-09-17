package collectory

import java.net.HttpURLConnection

class BootStrap {
    def messageSource
    def application
    def grailsApplication
    private static final String[] MESSAGE_BASENAMES = [
            "file:///var/opt/atlas/i18n/collectory-plugin/messages",
            "file:///opt/atlas/i18n/collectory-plugin/messages",
            "WEB-INF/grails-app/i18n/messages",
            "classpath:messages"
    ] as String[]

    def init = { servletContext ->
        List<String> basenames = MESSAGE_BASENAMES as List<String>
        def biocacheServicesUrl = application.config.biocacheServicesUrl
        if (biocacheServicesUrl) {
            String facetsI18nUrl = "${biocacheServicesUrl}/facets/i18n"
            HttpURLConnection connection = null
            try {
                connection = (HttpURLConnection) new URL(facetsI18nUrl).openConnection()
                connection.setRequestMethod("GET")
                connection.setConnectTimeout(2000)
                connection.setReadTimeout(2000)
                if (connection.responseCode >= 200 && connection.responseCode < 300) {
                    basenames << facetsI18nUrl
                }
            } finally {
                connection?.disconnect()
            }
        }
        messageSource.setBasenames(*basenames as String[])

        // gbifDefaultEntityCountry is mandatory. Not validating this value as 'ZZZ'
        if (!grailsApplication.config.gbifDefaultEntityCountry) {
            throw new MissingPropertyException("config `gbifDefaultEntityCountry` is not defined")
        }
    }
    def destroy = {
    }
}
