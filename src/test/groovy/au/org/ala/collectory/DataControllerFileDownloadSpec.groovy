package au.org.ala.collectory

import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification
import java.security.Principal

class DataControllerFileDownloadSpec extends Specification implements ControllerUnitTest<DataController> {

    def setup() {
        controller.collectoryAuthService = Mock(CollectoryAuthService)
        config.uploadFilePath = "/tmp/test-uploads"
        config.security = [
            upload: [
                ipWhitelist: ["127.0.0.1", "10.0.0.0/8"],
                apiKey: "secret-test-key"
            ]
        ]
    }

    def "unauthorized request with non-whitelisted IP is rejected with 403"() {
        given:
        request.remoteAddr = "203.0.113.50"
        params.directory = "dr123/12345"
        request.forwardURI = "/upload/dr123/12345/test.csv"

        when:
        controller.fileDownload()

        then:
        response.status == 403
        response.text.contains("Access denied")
    }

    def "unauthorized request with non-whitelisted IP attempting to spoof X-Forwarded-For is rejected with 403"() {
        given:
        request.remoteAddr = "203.0.113.50"
        request.addHeader("X-Forwarded-For", "127.0.0.1")
        params.directory = "dr123/12345"
        request.forwardURI = "/upload/dr123/12345/test.csv"

        when:
        controller.fileDownload()

        then:
        response.status == 403
        response.text.contains("Access denied")
    }

    def "request from whitelisted client IP through trusted reverse proxy is authorized"() {
        given:
        request.remoteAddr = "127.0.0.1"
        request.addHeader("X-Forwarded-For", "10.1.2.3")
        params.directory = "dr123/12345"
        request.forwardURI = "/upload/dr123/12345/test.csv"

        when:
        boolean authorized = controller.isFileDownloadAuthorized()

        then:
        authorized
    }

    def "unauthenticated request with whitelisted IP is authorized"() {
        given:
        request.remoteAddr = "10.1.2.3"
        params.directory = "dr123/12345"
        request.forwardURI = "/upload/dr123/12345/test.csv"

        when:
        boolean authorized = controller.isFileDownloadAuthorized()

        then:
        authorized
    }

    def "request with valid X-API-Key header is authorized from non-whitelisted IP"() {
        given:
        request.remoteAddr = "203.0.113.50"
        request.addHeader("X-API-Key", "secret-test-key")

        when:
        boolean authorized = controller.isFileDownloadAuthorized()

        then:
        authorized
    }

    def "request with valid apiKey query parameter is authorized from non-whitelisted IP"() {
        given:
        request.remoteAddr = "203.0.113.50"
        params.apiKey = "secret-test-key"

        when:
        boolean authorized = controller.isFileDownloadAuthorized()

        then:
        authorized
    }

    def "request with invalid API key falls back to IP check and fails if IP not whitelisted"() {
        given:
        request.remoteAddr = "203.0.113.50"
        request.addHeader("X-API-Key", "wrong-key")

        when:
        boolean authorized = controller.isFileDownloadAuthorized()

        then:
        !authorized
    }

    def "authenticated editor is authorized regardless of IP"() {
        given:
        request.remoteAddr = "203.0.113.50"
        request.userPrincipal = Mock(Principal)
        controller.collectoryAuthService.isAuthorised(_ as String[], _ as String[]) >> true

        when:
        boolean authorized = controller.isFileDownloadAuthorized()

        then:
        authorized
    }
}
