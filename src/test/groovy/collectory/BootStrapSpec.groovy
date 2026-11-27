package collectory

import org.grails.spring.context.support.ReloadableResourceBundleMessageSource
import spock.lang.Specification
import spock.lang.TempDir

class BootStrapSpec extends Specification {

    @TempDir
    File dir

    def "init drops the messages merged before it set the basenames"() {
        given: "a request served during startup merged es from the default basenames"
        new File(dir, 'early_es.properties').text = 'map.link=without overlay\n'
        def messageSource = new ReloadableResourceBundleMessageSource()
        messageSource.basename = "file:${dir}/early"
        def es = new Locale('es')
        assert messageSource.getMessage('map.link', null, es) == 'without overlay'

        and:
        def bootStrap = new BootStrap(
                messageSource: messageSource,
                application: [config: [:]],
                grailsApplication: [config: [gbifDefaultEntityCountry: 'ESP']])

        when:
        bootStrap.init.call(null)

        then: "es is merged again from the basenames of BootStrap"
        messageSource.getMessage('map.link', null, 'not found', es) == 'not found'
    }
}
