package au.org.ala.collectory

import grails.testing.web.interceptor.InterceptorUnitTest
import spock.lang.Specification

class TokenInterceptorSpec extends Specification implements InterceptorUnitTest<TokenInterceptor> {

    def setup() {
    }

    def cleanup() {

    }

    void "Test token interceptor matching"() {
        when:"A request matches the interceptor"
            withRequest(controller:"token")

        then:"The interceptor does match"
            interceptor.doesMatch()
    }
}
