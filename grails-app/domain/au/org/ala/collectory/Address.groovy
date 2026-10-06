package au.org.ala.collectory

class Address {

    static final long serialVersionUID = 1L;//1681261914339207268L;

    String street           // includes number eg 186 Tinaroo Creek Road
    String postBox          // eg PO Box 2104
    String city
    String state            // full name eg Queensland
    String postcode
    String country

    static transients = ['empty']

    static constraints = {
        street(nullable:true)
        postBox(nullable:true)
        city(nullable:true)
        state(nullable:true)
        postcode(nullable:true)
        country(nullable:true)
    }

    static String clean(String s) {
        if (!s) {
            return null
        }
        String cleaned = s.replaceAll(/[\r\n\f\u000B\u0085\u2028\u2029\u200B\uFEFF]+/, ' ')
                          .replaceAll(/[\s\u00A0]+/, ' ')
                          .trim()
        return cleaned.isEmpty() ? null : cleaned
    }

    void setStreet(String street) {
        this.street = clean(street)
    }

    void setPostBox(String postBox) {
        this.postBox = clean(postBox)
    }

    void setCity(String city) {
        this.city = clean(city)
    }

    void setState(String state) {
        this.state = clean(state)
    }

    void setPostcode(String postcode) {
        this.postcode = clean(postcode)
    }

    void setCountry(String country) {
        this.country = clean(country)
    }

    void cleanAddress() {
        this.street = clean(this.street)
        this.postBox = clean(this.postBox)
        this.city = clean(this.city)
        this.state = clean(this.state)
        this.postcode = clean(this.postcode)
        this.country = clean(this.country)
    }

    def isEmpty() {
        return [street, postBox, city, state, postcode, country].every {!it}
        //return !(street || postBox || city || state || postcode || country)
    }

    List<String> nonEmptyAddressElements(includePostal) {
        def fields = ['street','city','state','postcode']
        if (includePostal) {fields << 'postBox'}
        List<String> elements = []
        fields.each {
            if (this."${it}") {
                elements << this."${it}"
            }
        }
        return elements
    }

    String buildAddress() {
        return nonEmptyAddressElements(false).join(", ")
    }

    def String toString() {
        return nonEmptyAddressElements(true).join(", ")
    }

    def boolean equals(Object obj) {
        return obj instanceof Address &&
                street == obj.street &&
                postBox == obj.postBox &&
                city == obj.city &&
                state == obj.state &&
                postcode == obj.postcode &&
                country == obj.country
    }
}
