package collectory

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
        if (application.config.biocacheServicesUrl) {
            basenames << "${application.config.biocacheServicesUrl}/facets/i18n"
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
