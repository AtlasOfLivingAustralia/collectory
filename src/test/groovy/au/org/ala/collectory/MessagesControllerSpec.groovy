package au.org.ala.collectory

import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification
import spock.lang.Unroll

class MessagesControllerSpec extends Specification implements ControllerUnitTest<MessagesController> {

    static class StubMessageSource {
        Map<String, Properties> propertiesByLocale = [:]

        def getMergedProperties(Locale locale) {
            def key = locale ? locale.toString() : ''
            def props = propertiesByLocale[key] ?: propertiesByLocale[''] ?: new Properties()
            return [properties: props]
        }
    }

    StubMessageSource stubMessageSource

    def setup() {
        stubMessageSource = new StubMessageSource()
        stubMessageSource.propertiesByLocale[''] = new Properties().tap {
            put('default.title', 'Default Title')
        }
        stubMessageSource.propertiesByLocale['en'] = new Properties().tap {
            put('welcome', 'Welcome')
        }
        stubMessageSource.propertiesByLocale['en_AU'] = new Properties().tap {
            put('welcome', 'G\'day')
        }
        stubMessageSource.propertiesByLocale['zh_CN'] = new Properties().tap {
            put('welcome', 'Ni Hao')
        }
        controller.messageSource = stubMessageSource
    }

    @Unroll
    def "returns 404 Not Found for invalid filename: '#filename'"() {
        when:
        controller.i18n(filename)

        then:
        response.status == 404

        where:
        filename << [
                null,
                '',
                '.env',
                'messages.env',
                'messages_en.env',
                'messages_en_AU.env',
                'messages.properties.env',
                'config.env',
                'foo.properties',
                'messages',
                'messages_en',
                'secrets.txt'
        ]
    }

    def "serves messages.properties using request locale"() {
        given:
        request.addPreferredLocale(Locale.ENGLISH)

        when:
        controller.i18n('messages.properties')

        then:
        response.status == 200
        response.contentType.startsWith('text/plain')
        response.contentType.contains('charset=UTF-8')
        response.text.contains('welcome=Welcome')
    }

    @Unroll
    def "serves #filename extracting locale '#expectedLocale'"() {
        when:
        controller.i18n(filename)

        then:
        response.status == 200
        response.contentType.startsWith('text/plain')
        response.contentType.contains('charset=UTF-8')
        response.text.contains(expectedContent)

        where:
        filename                        | expectedLocale | expectedContent
        'messages_en.properties'        | 'en'           | 'welcome=Welcome'
        'messages_en_AU.properties'     | 'en_AU'        | 'welcome=G\'day'
        'messages_zh_CN.properties'     | 'zh_CN'        | 'welcome=Ni Hao'
    }
}
