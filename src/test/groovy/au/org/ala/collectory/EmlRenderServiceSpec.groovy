package au.org.ala.collectory

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class EmlRenderServiceSpec extends Specification implements ServiceUnitTest<EmlRenderService> {

    void "getTaxonomicCoverage accepts lax taxonomy hints"() {
        given:
        def hints = "{coverage:[{kingdom:'plantae'},{kingdom:'fungi'},{kingdom:'chromista'},{kingdom:'protozoa'},{kingdom:'bacteria'}]}"

        when:
        def xml = service.getTaxonomicCoverage(hints)

        then:
        xml.toString().contains('<taxonRankValue>plantae</taxonRankValue>')
        xml.toString().contains('<taxonRankValue>bacteria</taxonRankValue>')
    }
}
