package au.org.ala.collectory

import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification
import spock.lang.Unroll

class IpWhitelistHelperSpec extends Specification {

    @Unroll
    def "test exact IP and hostname matching: #clientIp against #whitelistEntry -> #expected"() {
        expect:
        IpWhitelistHelper.matches(clientIp, whitelistEntry) == expected

        where:
        clientIp          | whitelistEntry  | expected
        "127.0.0.1"       | "127.0.0.1"     | true
        "127.0.0.1"       | "127.0.0.2"     | false
        "192.168.1.100"   | "192.168.1.100" | true
        "192.168.1.100"   | "192.168.1.101" | false
        "::1"             | "::1"           | true
        "0:0:0:0:0:0:0:1" | "::1"           | true
        "::1"             | "0:0:0:0:0:0:0:1"| true
        "127.0.0.1"       | "localhost"     | true
        "invalid-ip"      | "127.0.0.1"     | false
        "127.0.0.1"       | "invalid-entry" | false
        ""                | "127.0.0.1"     | false
        "127.0.0.1"       | ""              | false
        null              | "127.0.0.1"     | false
        "127.0.0.1"       | null            | false
    }

    @Unroll
    def "test IPv4 CIDR matching: #clientIp in #cidr -> #expected"() {
        expect:
        IpWhitelistHelper.matches(clientIp, cidr) == expected

        where:
        clientIp        | cidr             | expected
        "10.0.0.1"      | "10.0.0.0/8"     | true
        "10.255.255.255"| "10.0.0.0/8"     | true
        "11.0.0.1"      | "10.0.0.0/8"     | false
        "192.168.1.50"  | "192.168.0.0/16" | true
        "192.168.254.1" | "192.168.0.0/16" | true
        "192.169.1.1"   | "192.168.0.0/16" | false
        "172.16.5.10"   | "172.16.0.0/12"  | true
        "172.31.255.1"  | "172.16.0.0/12"  | true
        "172.32.1.1"    | "172.16.0.0/12"  | false
        "192.168.1.1"   | "192.168.1.1/32" | true
        "192.168.1.2"   | "192.168.1.1/32" | false
        "192.168.1.1"   | "0.0.0.0/0"      | true
    }

    @Unroll
    def "test IPv6 CIDR matching: #clientIp in #cidr -> #expected"() {
        expect:
        IpWhitelistHelper.matches(clientIp, cidr) == expected

        where:
        clientIp               | cidr          | expected
        "::1"                  | "::1/128"     | true
        "::2"                  | "::1/128"     | false
        "fe80::1"              | "fe80::/10"   | true
        "2001:db8:abcd:0012::1"| "2001:db8::/32" | true
        "2001:db9::1"          | "2001:db8::/32" | false
    }

    def "test extractClientIps from MockHttpServletRequest"() {
        given:
        def request = new MockHttpServletRequest()
        request.remoteAddr = "10.0.0.1"
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178")
        request.addHeader("X-Real-IP", "203.0.113.195")

        when:
        def ips = IpWhitelistHelper.extractClientIps(request)

        then:
        ips == ["203.0.113.195", "70.41.3.18", "150.172.238.178", "10.0.0.1"]
    }

    def "test isIpWhitelisted with various whitelist formats"() {
        given:
        def clientIps = ["192.168.1.50"]

        expect:
        // List format
        IpWhitelistHelper.isIpWhitelisted(clientIps, ["10.0.0.0/8", "192.168.0.0/16"]) == true
        IpWhitelistHelper.isIpWhitelisted(clientIps, ["10.0.0.0/8"]) == false

        // String array format
        IpWhitelistHelper.isIpWhitelisted(clientIps, ["192.168.1.50"] as String[]) == true

        // Comma-separated string format
        IpWhitelistHelper.isIpWhitelisted(clientIps, "10.0.0.0/8, 192.168.1.50") == true
        IpWhitelistHelper.isIpWhitelisted(clientIps, "10.0.0.0/8, 172.16.0.0/12") == false

        // Null / empty handling
        IpWhitelistHelper.isIpWhitelisted([], ["192.168.1.50"]) == false
        IpWhitelistHelper.isIpWhitelisted(clientIps, null) == false
        IpWhitelistHelper.isIpWhitelisted(clientIps, []) == false
    }
}
