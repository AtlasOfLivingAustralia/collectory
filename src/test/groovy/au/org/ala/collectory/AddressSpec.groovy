package au.org.ala.collectory

import spock.lang.Specification
import spock.lang.Unroll

class AddressSpec extends Specification {

    def "test postBox sanitizes unicode line separator and extra whitespace"() {
        given:
        def address = new Address()

        when:
        address.postBox = "PO Box 1749 \u2028Margaret River WA. 6285"

        then:
        address.postBox == "PO Box 1749 Margaret River WA. 6285"
    }

    def "test map constructor sanitizes address fields"() {
        when:
        def address = new Address(
                street: "123 Main St\nSuite 4",
                postBox: "PO Box 1749 \u2028Margaret River WA. 6285",
                city: "Margaret River\r\n",
                state: "WA\u2029",
                postcode: " 6285 ",
                country: "Australia"
        )

        then:
        address.street == "123 Main St Suite 4"
        address.postBox == "PO Box 1749 Margaret River WA. 6285"
        address.city == "Margaret River"
        address.state == "WA"
        address.postcode == "6285"
        address.country == "Australia"
    }

    @Unroll
    def "clean handles various whitespace and separator cases: '#input' -> '#expected'"() {
        expect:
        Address.clean(input) == expected

        where:
        input                                              | expected
        "PO Box 1749 \u2028Margaret River WA. 6285"        | "PO Box 1749 Margaret River WA. 6285"
        "PO Box 1749 ?Margaret River WA. 6285"             | "PO Box 1749 ?Margaret River WA. 6285"
        "Is this an address?"                              | "Is this an address?"
        "خیابان\u200Cها"                                   | "خیابان\u200Cها"
        "A\u200DB"                                         | "A\u200DB"
        "PO Box 1749\u200BMargaret River WA. 6285"         | "PO Box 1749 Margaret River WA. 6285"
        "\uFEFF123 Main St"                                | "123 Main St"
        "Line 1\nLine 2\rLine 3\r\nLine 4"                 | "Line 1 Line 2 Line 3 Line 4"
        "Paragraph 1\u2029Paragraph 2"                     | "Paragraph 1 Paragraph 2"
        "  Multiple   spaces   and \t tabs  "              | "Multiple spaces and tabs"
        "   "                                              | null
        ""                                                 | null
        null                                               | null
        "Valid Street 123"                                 | "Valid Street 123"
    }

    def "test cleanAddress method sanitizes all existing fields"() {
        given:
        def address = new Address()
        address.@street = "123 Main St\n"
        address.@postBox = "PO Box 123\u2028"
        address.@city = " Sydney "
        address.@state = " NSW "
        address.@postcode = " 2000 "
        address.@country = " Australia "

        when:
        address.cleanAddress()

        then:
        address.street == "123 Main St"
        address.postBox == "PO Box 123"
        address.city == "Sydney"
        address.state == "NSW"
        address.postcode == "2000"
        address.country == "Australia"
    }
}
