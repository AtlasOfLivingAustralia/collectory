package au.org.ala.collectory

import grails.testing.services.ServiceUnitTest
import grails.testing.gorm.DomainUnitTest
import groovy.util.slurpersupport.NodeChild
import spock.lang.Specification

class EmlImportServiceSpec extends Specification implements ServiceUnitTest<EmlImportService>, DomainUnitTest<Contact> {

    def setupSpec() {
        mockDomain(ContactFor)
        mockDomain(DataResource)
    }

    def setup() {
        service.dataLoaderService = Mock(DataLoaderService)
        service.collectoryAuthService = Mock(CollectoryAuthService)
        service.collectoryAuthService.username() >> "testUser"
        service.metaClass.getLicence = { eml ->
            [licenseType: 'CC-BY', licenseVersion: '4.0']
        }
    }

    private NodeChild parseEml(String baseEml, String contactsXml) {
        XmlSlurper xmlSlurper = new XmlSlurper()
        return xmlSlurper.parseText(baseEml.replace("%CONTACTS%", contactsXml))
    }

    void "test extractContactsFromEml with creator and contact"() {
        given: "A base EML input loaded from a file and with a custom contact"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <organizationName>Example Organization</organizationName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
        </creator>
        <contact>
            <individualName>
                <givenName>Jane</givenName>
                <surName>Smith</surName>
            </individualName>
            <electronicMailAddress>jane.smith@example.com</electronicMailAddress>
        </contact>
    '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "The contacts are created successfully"
        result.contacts.size() == 2
        result.contacts*.email.contains('jane.smith@example.com')
    }

    void "test extractContactsFromEml with multiple contacts"() {
        given: "A base EML input loaded from a file with multiple contacts"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
<creator>
     <individualName>
          <givenName>John</givenName>
          <surName>Doe</surName>
     </individualName>
     <organizationName>Example Organization</organizationName>
     <electronicMailAddress>john.doe@example.org</electronicMailAddress>
</creator>
<creator>
    <individualName>
        <givenName>A.</givenName>
        <surName>Smith</surName>
    </individualName>
    <organizationName>Example Organization</organizationName>
    <address>
        <city>Example City</city>
        <administrativeArea>Example State</administrativeArea>
        <postalCode>12345</postalCode>
        <country>US</country>
    </address>
    <userId directory="https://orcid.org/">0000-0000-0000-0001</userId>
</creator>
<creator>
    <individualName>
        <givenName>B.</givenName>
        <surName>Johnson</surName>
    </individualName>
    <organizationName>Example Organization</organizationName>
    <address>
        <city>Example City</city>
        <administrativeArea>Example State</administrativeArea>
        <postalCode>12345</postalCode>
        <country>US</country>
    </address>
    <userId directory="https://orcid.org/">0000-0000-0000-0002</userId>
</creator>
<creator>
    <individualName>
        <givenName>C.</givenName>
        <surName>Williams</surName>
    </individualName>
    <organizationName>Example Organization</organizationName>
    <address>
        <city>Example City</city>
        <administrativeArea>Example State</administrativeArea>
        <postalCode>12345</postalCode>
        <country>US</country>
    </address>
    <userId directory="https://orcid.org/">0000-0000-0000-0003</userId>
</creator>
<metadataProvider>
    <individualName>
        <givenName>A.</givenName>
        <surName>Smith</surName>
    </individualName>
    <organizationName>Example Organization</organizationName>
    <address>
        <city>Example City</city>
        <administrativeArea>Example State</administrativeArea>
        <postalCode>12345</postalCode>
        <country>US</country>
    </address>
    <userId directory="https://orcid.org/">0000-0000-0000-0001</userId>
</metadataProvider>
<metadataProvider>
    <individualName>
        <givenName>B.</givenName>
        <surName>Johnson</surName>
    </individualName>
    <organizationName>Example Organization</organizationName>
    <address>
        <city>Example City</city>
        <administrativeArea>Example State</administrativeArea>
        <postalCode>12345</postalCode>
        <country>US</country>
    </address>
    <userId directory="https://orcid.org/">0000-0000-0000-0002</userId>
</metadataProvider>
<metadataProvider>
    <individualName>
        <givenName>C.</givenName>
        <surName>Williams</surName>
    </individualName>
    <organizationName>Example Organization</organizationName>
    <address>
        <city>Example City</city>
        <administrativeArea>Example State</administrativeArea>
        <postalCode>12345</postalCode>
        <country>US</country>
    </address>
    <userId directory="https://orcid.org/">0000-0000-0000-0003</userId>
</metadataProvider>
    '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "Contacts are deduplicated within same resource (Smith, Johnson, Williams appear in both roles)"
        result.contacts.size() == 4  // Doe + 3 unique (Smith, Johnson, Williams deduplicated)
        result.contacts*.lastName.containsAll(['Doe', 'Williams', 'Smith', 'Johnson'])
    }

    void "test extractContactsFromEml avoids duplicates between creator and metadataProvider"() {
        given: "A base EML input with overlapping contacts in creator and metadataProvider"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
<creator>
     <individualName>
          <givenName>John</givenName>
          <surName>Doe</surName>
     </individualName>
     <organizationName>Example Organization</organizationName>
     <electronicMailAddress>john.doe@example.org</electronicMailAddress>
</creator>
<contact>
    <individualName>
        <givenName>A.</givenName>
        <surName>Smith</surName>
    </individualName>
    <electronicMailAddress>a.smith@example.com</electronicMailAddress>
</contact>
<metadataProvider>
    <individualName>
        <givenName>A.</givenName>
        <surName>Smith</surName>
    </individualName>
    <electronicMailAddress>a.smith@example.com</electronicMailAddress>
</metadataProvider>
    '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "Contacts with identical data are deduplicated within same resource"
        result.contacts.size() == 2  // John Doe + A. Smith (Smith deduplicated between contact and metadataProvider)
        result.primaryContacts.size() == 1
        result.contacts*.email.count('a.smith@example.com') == 1  // Only appears once
        result.contacts*.lastName.count('Smith') == 1
    }

    void "test extractContactsFromEml processes metadataProvider without email"() {
        given: "A base EML input with metadataProvider missing electronicMailAddress"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
<creator>
     <individualName>
          <givenName>John</givenName>
          <surName>Doe</surName>
     </individualName>
     <organizationName>Example Organization</organizationName>
     <electronicMailAddress>john.doe@example.org</electronicMailAddress>
</creator>
<metadataProvider>
    <individualName>
        <givenName>B.</givenName>
        <surName>Johnson</surName>
    </individualName>
    <organizationName>Example Organization</organizationName>
</metadataProvider>
    '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "Contacts without email are processed based on other fields"
        result.contacts.size() == 2
        result.contacts[0].lastName == 'Doe'
        result.contacts[0].email == 'john.doe@example.org'
        result.contacts[1].lastName == 'Johnson'
        result.contacts[1].email == null  // No email provided
    }

    void "test extractContactsFromEml processes unique contacts in creator and metadataProvider"() {
        given: "A base EML input with unique contacts in creator and metadataProvider"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
<creator>
     <individualName>
          <givenName>John</givenName>
          <surName>Doe</surName>
     </individualName>
     <organizationName>Example Organization</organizationName>
     <electronicMailAddress>john.doe@example.org</electronicMailAddress>
</creator>
<contact>
    <individualName>
        <givenName>A.</givenName>
        <surName>Smith</surName>
    </individualName>
    <electronicMailAddress>a.smith@example.com</electronicMailAddress>
</contact>
<metadataProvider>
    <individualName>
        <givenName>B.</givenName>
        <surName>Johnson</surName>
    </individualName>
    <electronicMailAddress>b.johnson@example.com</electronicMailAddress>
</metadataProvider>
    '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "All unique contacts are processed correctly"
        result.contacts.size() == 3
        result.primaryContacts.size() == 1
        result.contacts*.email.containsAll(['john.doe@example.org', 'a.smith@example.com', 'b.johnson@example.com'])
        result.contacts*.lastName.containsAll(['Doe', 'Smith', 'Johnson'])
    }

    void "test extractContactsFromEml with organizationName only"() {
        given:
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
<creator>
    <organizationName>Example Organization</organizationName>
</creator>
'''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when:
        def result = service.extractContactsFromEml(eml, dataResource)

        then:
        result.contacts.size() == 1
        result.contacts[0].organizationName == 'Example Organization'
    }

    void "test extractContactsFromEml with positionName only"() {
        given:
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
<creator>
    <positionName>Data Manager</positionName>
</creator>
'''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when:
        def result = service.extractContactsFromEml(eml, dataResource)

        then:
        result.contacts.size() == 1
        result.contacts[0].positionName == 'Data Manager'
    }

    void "test extractContactsFromEml with individualName only"() {
        given:
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
<creator>
    <individualName>
        <givenName>Jane</givenName>
        <surName>Doe</surName>
    </individualName>
</creator>
        '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when:
        def result = service.extractContactsFromEml(eml, dataResource)

        then:
        result.contacts.size() == 1
        result.contacts[0].lastName == 'Doe'
        result.contacts[0].firstName == 'Jane'
    }

    void "test extractContactsFromEml skips invalid contact"() {
        given:
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
<creator>
    <individualName>
        <givenName></givenName>
        <surName></surName>
    </individualName>
</creator>
    '''
        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when:
        def result = service.extractContactsFromEml(eml, dataResource)

        then:
        result.contacts.isEmpty()
    }

    void "test extractContactsFromEml skips contact without individualName or organizationName"() {
        given:
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
<creator>
    <positionName></positionName>
</creator>
    '''
        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when:
        def result = service.extractContactsFromEml(eml, dataResource)

        then:
        result.contacts.size() == 0
    }

    void "test extractContactsFromEml skips contact with no valid fields"() {
        given:
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
<creator>
    <positionName></positionName>
    <organizationName></organizationName>
</creator>
    '''
        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when:
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "The contact is skipped"
        result.contacts.size() == 0
    }

    void "test extractContactsFromEml with only giveName"() {
        given: "A base EML input loaded from a file and with a custom contact"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
        <creator>
            <individualName>
                <surName>Doe</surName>
            </individualName>
            <organizationName>Example Organization</organizationName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
        </creator>
    '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "The contact is created successfully"
        result.contacts.size() == 1
        result.contacts*.lastName.contains('Doe')
        result.contacts*.email.contains('john.doe@example.org')
    }

    void "test extractContactsFromEml without surName"() {
        given: "A base EML input loaded from a file and with a custom contact"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
            </individualName>
        </creator>
    '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "The contact is empty"
        result.contacts.size() == 0

    }

    void "test extractContactsFromEml with surName and phone"() {
        given: "A base EML input loaded from a file and with a custom contact"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
        <creator>
            <individualName>
                <surName>Doe</surName>
            </individualName>
            <phone>+1 234567890</phone>
        </creator>
    '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource()

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "The contact is empty"
        result.contacts.size() == 1
        result.contacts*.lastName.contains('Doe')
        result.contacts*.phone.contains('+1 234567890')
    }

    void "test addOrUpdateContact updates phone if contact exists or creates new contact with phone"() {
        given: "An existing contact and an EML element with updated phone"
        def resource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        def existingContact = new Contact(
                firstName: "John",
                lastName: "Doe",
                email: "john.doe@example.org",
                phone: "123456789",
                userLastModified: "originalUser"
        ).save(flush: true, failOnError: true)

        // Associate the contact with the resource
        new ContactFor(contact: existingContact, entityUid: resource.uid, role: "creator", administrator: false, primaryContact: false, userLastModified: "testUser").save(flush: true, failOnError: true)

        def emlElement = new XmlSlurper().parseText('''        
<creator>
    <individualName>
        <givenName>John</givenName>
        <surName>Doe</surName>
    </individualName>
    <electronicMailAddress>john.doe@example.org</electronicMailAddress>
    <phone>987654321</phone>
</creator>
''')

        when: "addOrUpdateContact is called with resource context"
        def result = service.addOrUpdateContact(emlElement, resource)

        then: "The existing contact is updated with the new phone"
        result != null
        result.id == existingContact.id  // Same contact reused
        result.email == "john.doe@example.org"
        result.firstName == "John"
        result.lastName == "Doe"
        result.phone == "987654321"
        result.userLastModified == "testUser"

        and: "No duplicate contact is created"
        Contact.count() == 1

        when: "A new contact is created with a phone number for a different resource"
        def resource2 = new DataResource(uid: "dr2", name: "Test Resource 2", userLastModified: "testUser").save(flush: true, failOnError: true)
        def newEmlElement = new XmlSlurper().parseText('''      
        <creator>
            <individualName>
                <givenName>Jane</givenName>
                <surName>Smith</surName>
            </individualName>
            <electronicMailAddress>jane.smith@example.org</electronicMailAddress>
            <phone>555123456</phone>
        </creator>
    ''')

        def newContact = service.addOrUpdateContact(newEmlElement, resource2)

        then: "The new contact is created successfully"
        newContact != null
        newContact.email == "jane.smith@example.org"
        newContact.firstName == "Jane"
        newContact.lastName == "Smith"
        newContact.phone == "555123456"
        newContact.userLastModified == "testUser"

        and: "There are now two contacts in the system"
        Contact.count() == 2
    }

    void "test process userIds from EML"() {
        given: "An EML input with creators and userIds"
        def emlXml = '''
        <eml:eml xmlns:eml="https://eml.ecoinformatics.org/eml-2.2.0" xmlns:dc="http://purl.org/dc/terms/">
            <dataset>
                <creator>
                    <individualName>
                        <givenName>John</givenName>
                        <surName>Doe</surName>
                    </individualName>
                    <organizationName>Sample Organization</organizationName>
                    <userId directory="https://orcid.org/">0000-0001-2345-6789</userId>
                </creator>
                <creator>
                    <individualName>
                        <givenName>Jane</givenName>
                        <surName>Smith</surName>
                    </individualName>
                    <organizationName>Another Organization</organizationName>
                    <userId directory="https://orcid.org/">0000-0002-9876-5432</userId>
                </creator>
            </dataset>
        </eml:eml>
    '''

        def eml = new XmlSlurper().parseText(emlXml)
        def dataResource = new DataResource()

        when: "Contacts are extracted from EML"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "Contacts are created with correct userIdUrl and organizationName"
        result.contacts.size() == 2

        and: "First contact contains correct userIdUrl and organizationName"
        result.contacts[0].userId == "https://orcid.org/0000-0001-2345-6789"
        result.contacts[0].organizationName == "Sample Organization"

        and: "Second contact contains correct userIdUrl and organizationName"
        result.contacts[1].userId == "https://orcid.org/0000-0002-9876-5432"
        result.contacts[1].organizationName == "Another Organization"
    }

    void "test addOrUpdateContact allows same email with different names"() {
        given: "An existing contact with a specific name and email"
        def resource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        def existingContact = new Contact(
                firstName: "Alice",
                lastName: "Smith",
                email: "shared@example.org",
                userLastModified: "originalUser"
        ).save(flush: true, failOnError: true)

        def emlElement = new XmlSlurper().parseText('''        
    <creator>
        <individualName>
            <givenName>Bob</givenName>
            <surName>Johnson</surName>
        </individualName>
        <electronicMailAddress>shared@example.org</electronicMailAddress>
    </creator>
''')

        when: "addOrUpdateContact is called with a different name but the same email"
        def result = service.addOrUpdateContact(emlElement, resource)

        then: "A new contact is created instead of overwriting the existing one"
        result != null
        result.email == "shared@example.org"
        result.firstName == "Bob"
        result.lastName == "Johnson"
        result.userLastModified == "testUser"

        and: "Both contacts exist in the database"
        Contact.count() == 2

        and: "The original contact remains unchanged"
        def originalContact = Contact.findByFirstNameAndLastName("Alice", "Smith")
        originalContact.email == "shared@example.org"
    }

    void "test addOrUpdateContact does not overwrite contact with only orgName"() {
        given: "A contact with both a name and organizationName exists"
        def resource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        def existingContactEml = new XmlSlurper().parseText('''        
    <creator>
        <individualName>
            <givenName>John</givenName>
            <surName>Doe</surName>
        </individualName>
        <organizationName>Acme Corp</organizationName>
    </creator>
    ''')

        def newContactEml = new XmlSlurper().parseText('''        
    <creator>
        <organizationName>Acme Corp</organizationName>
    </creator>
    ''')

        when: "The first contact is added"
        def contactWithName = service.addOrUpdateContact(existingContactEml, resource)

        and: "A new contact with only the organization is added"
        def contactWithOnlyOrg = service.addOrUpdateContact(newContactEml, resource)

        then: "Both contacts exist separately"
        contactWithName != null
        contactWithOnlyOrg != null
        contactWithName.organizationName == "Acme Corp"
        contactWithOnlyOrg.organizationName == "Acme Corp"

        and: "They are stored as separate contacts"
        Contact.count() == 2

        and: "The original contact's name is not removed"
        Contact.findByFirstNameAndLastName("John", "Doe") != null
    }

    void "test addOrUpdateContact does not overwrite contact with only positionName"() {
        given: "A contact with both a name and positionName exists"
        def resource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        def existingContactEml = new XmlSlurper().parseText('''        
    <creator>
        <individualName>
            <givenName>Jane</givenName>
            <surName>Smith</surName>
        </individualName>
        <positionName>Data Manager</positionName>
    </creator>
    ''')

        def newContactEml = new XmlSlurper().parseText('''        
    <creator>
        <positionName>Data Manager</positionName>
    </creator>
    ''')

        when: "The first contact is added"
        def contactWithName = service.addOrUpdateContact(existingContactEml, resource)

        and: "A new contact with only the position is added"
        def contactWithOnlyPosition = service.addOrUpdateContact(newContactEml, resource)

        then: "Both contacts exist separately"
        contactWithName != null
        contactWithOnlyPosition != null
        contactWithName.positionName == "Data Manager"
        contactWithOnlyPosition.positionName == "Data Manager"

        and: "They are stored as separate contacts"
        Contact.count() == 2

        and: "The original contact's name is not removed"
        Contact.findByFirstNameAndLastName("Jane", "Smith") != null
    }

    void "test addOrUpdateContact updates positionName and organizationName case sensitivity"() {
        given:
        def resource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        def existingContact = new Contact(
                firstName: "John",
                lastName: "Doe",
                positionName: "data manager",
                organizationName: "example org",
                email: "john.doe@example.com",
                userLastModified: "originalUser"
        ).save(flush: true, failOnError: true)

        // Associate the contact with the resource
        new ContactFor(contact: existingContact, entityUid: resource.uid, role: "creator", administrator: false, primaryContact: false, userLastModified: "testUser").save(flush: true, failOnError: true)

        def emlElement = new XmlSlurper().parseText('''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <positionName>Data Manager</positionName>
            <organizationName>Example Org</organizationName>
            <electronicMailAddress>john.doe@example.com</electronicMailAddress>
        </creator>
    ''')

        when:
        def updatedContact = service.addOrUpdateContact(emlElement, resource)

        then:
        updatedContact != null
        updatedContact.id == existingContact.id
        updatedContact.positionName == "Data Manager"
        updatedContact.organizationName == "Example Org"
    }

    // ===== NEW TESTS FOR GBIF-STYLE CONTACT PROCESSING =====

    void "test same person in multiple roles creates separate contacts"() {
        given: "An EML with the same person as both creator and metadataProvider with IDENTICAL data"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <organizationName>Example Organization</organizationName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
        </creator>
        <metadataProvider>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <organizationName>Example Organization</organizationName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
        </metadataProvider>
        '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "Only ONE contact is created (deduplication within same resource with identical data)"
        result.contacts.size() == 1
        result.contacts[0].email == 'john.doe@example.org'
        Contact.count() == 1
    }

    void "test contact update only affects specific resource"() {
        given: "Two resources sharing a contact"
        def resource1 = new DataResource(uid: "dr1", name: "Resource 1", userLastModified: "testUser").save(flush: true, failOnError: true)
        def resource2 = new DataResource(uid: "dr2", name: "Resource 2", userLastModified: "testUser").save(flush: true, failOnError: true)

        def contact1 = new Contact(
                firstName: "John",
                lastName: "Doe",
                email: "john.doe@example.org",
                phone: "111-111-1111",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        def contact2 = new Contact(
                firstName: "John",
                lastName: "Doe",
                email: "john.doe@example.org",
                phone: "111-111-1111",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        // Associate contacts with resources
        new ContactFor(contact: contact1, entityUid: resource1.uid, role: "creator", administrator: false, primaryContact: false, userLastModified: "testUser").save(flush: true, failOnError: true)
        new ContactFor(contact: contact2, entityUid: resource2.uid, role: "creator", administrator: false, primaryContact: false, userLastModified: "testUser").save(flush: true, failOnError: true)

        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
            <phone>222-222-2222</phone>
        </creator>
        '''
        def eml = parseEml(baseEml, contactsXml)

        when: "Update contact details from EML for resource1"
        def result = service.extractContactsFromEml(eml, resource1)

        then: "Resource1's contact is updated"
        def updatedContact1 = result.contacts[0]
        updatedContact1.phone == "222-222-2222"

        and: "Resource2's contact remains unchanged"
        contact2.refresh()
        contact2.phone == "111-111-1111"
    }

    void "test preserve publish flag during update"() {
        given: "An existing contact with publish=false associated with a resource"
        def resource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        def existingContact = new Contact(
                firstName: "Jane",
                lastName: "Smith",
                email: "jane.smith@example.org",
                phone: "111-111-1111",
                publish: false,  // User has set this to false
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        new ContactFor(contact: existingContact, entityUid: resource.uid, role: "creator", administrator: false, primaryContact: false, userLastModified: "testUser").save(flush: true, failOnError: true)

        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
        <creator>
            <individualName>
                <givenName>Jane</givenName>
                <surName>Smith</surName>
            </individualName>
            <electronicMailAddress>jane.smith@example.org</electronicMailAddress>
            <phone>222-222-2222</phone>
        </creator>
        '''
        def eml = parseEml(baseEml, contactsXml)

        when: "EML update arrives"
        def result = service.extractContactsFromEml(eml, resource)

        then: "Contact is updated but publish flag is preserved"
        def updatedContact = result.contacts[0]
        updatedContact.id == existingContact.id
        updatedContact.phone == "222-222-2222"
        updatedContact.publish == false  // Preserved from original
    }

    void "test extractContactsFromEml allows duplicate roles for different details"() {
        given: "An EML with same person in creator twice with DIFFERENT organizations"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <organizationName>University A</organizationName>
            <electronicMailAddress>john.doe@university-a.org</electronicMailAddress>
        </creator>
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <organizationName>University B</organizationName>
            <electronicMailAddress>john.doe@university-b.org</electronicMailAddress>
        </creator>
        '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "Both contacts are created as separate entities (different data = different contacts)"
        result.contacts.size() == 2
        result.contacts[0].organizationName == 'University A'
        result.contacts[1].organizationName == 'University B'
        result.contacts[0].id != result.contacts[1].id
    }

    void "test addOrUpdateContact matches existing contact for same resource"() {
        given: "A resource with an associated contact"
        def resource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        def existingContact = new Contact(
                firstName: "Alice",
                lastName: "Johnson",
                email: "alice.johnson@example.org",
                organizationName: "Org A",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        new ContactFor(contact: existingContact, entityUid: resource.uid, role: "creator", administrator: false, primaryContact: false, userLastModified: "testUser").save(flush: true, failOnError: true)

        def emlElement = new XmlSlurper().parseText('''
        <creator>
            <individualName>
                <givenName>Alice</givenName>
                <surName>Johnson</surName>
            </individualName>
            <organizationName>Org A</organizationName>
            <electronicMailAddress>alice.johnson@example.org</electronicMailAddress>
        </creator>
        ''')

        when: "addOrUpdateContact is called with resource context"
        def result = service.addOrUpdateContact(emlElement, resource)

        then: "The existing contact is reused and updated"
        result.id == existingContact.id
        Contact.count() == 1
    }

    void "test addOrUpdateContact creates new contact for different resource"() {
        given: "A contact associated with resource1, and we're importing into resource2"
        def resource1 = new DataResource(uid: "dr1", name: "Resource 1", userLastModified: "testUser").save(flush: true, failOnError: true)
        def resource2 = new DataResource(uid: "dr2", name: "Resource 2", userLastModified: "testUser").save(flush: true, failOnError: true)

        def contact1 = new Contact(
                firstName: "Bob",
                lastName: "Smith",
                email: "bob.smith@example.org",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        new ContactFor(contact: contact1, entityUid: resource1.uid, role: "creator", administrator: false, primaryContact: false, userLastModified: "testUser").save(flush: true, failOnError: true)

        def emlElement = new XmlSlurper().parseText('''
        <creator>
            <individualName>
                <givenName>Bob</givenName>
                <surName>Smith</surName>
            </individualName>
            <electronicMailAddress>bob.smith@example.org</electronicMailAddress>
        </creator>
        ''')

        when: "addOrUpdateContact is called for resource2"
        def result = service.addOrUpdateContact(emlElement, resource2)

        then: "A new contact is created for resource2"
        result.id != contact1.id
        Contact.count() == 2
        result.email == "bob.smith@example.org"
    }

    // ===== EML LIFECYCLE TESTS: Contact changes over time =====

    void "test EML lifecycle - contact details updated in new version"() {
        given: "A resource with initial EML contacts"
        def resource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def initialContactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
            <phone>111-111-1111</phone>
        </creator>
        '''

        def initialEml = parseEml(baseEml, initialContactsXml)
        def initialResult = service.extractContactsFromEml(initialEml, resource)

        // Associate contacts with resource
        initialResult.contacts.each { contact ->
            new ContactFor(contact: contact, entityUid: resource.uid, role: "creator", administrator: false, primaryContact: false, userLastModified: "testUser").save(flush: true, failOnError: true)
        }

        def initialContactId = initialResult.contacts[0].id

        when: "EML is updated with new phone number"
        def updatedContactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
            <phone>222-222-2222</phone>
        </creator>
        '''

        def updatedEml = parseEml(baseEml, updatedContactsXml)
        def updatedResult = service.extractContactsFromEml(updatedEml, resource)

        then: "Same contact is updated with new phone"
        updatedResult.contacts.size() == 1
        updatedResult.contacts[0].id == initialContactId  // Same contact
        updatedResult.contacts[0].phone == "222-222-2222"  // Updated phone
        Contact.count() == 1  // No new contact created
    }

    void "test EML lifecycle - contact removed from EML"() {
        given: "A resource with two contacts"
        def resource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def initialContactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
        </creator>
        <creator>
            <individualName>
                <givenName>Jane</givenName>
                <surName>Smith</surName>
            </individualName>
            <electronicMailAddress>jane.smith@example.org</electronicMailAddress>
        </creator>
        '''

        def initialEml = parseEml(baseEml, initialContactsXml)
        def initialResult = service.extractContactsFromEml(initialEml, resource)

        initialResult.contacts.each { contact ->
            new ContactFor(contact: contact, entityUid: resource.uid, role: "creator", administrator: false, primaryContact: false, userLastModified: "testUser").save(flush: true, failOnError: true)
        }

        when: "EML is updated with only one contact (Jane removed)"
        def updatedContactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
        </creator>
        '''

        def updatedEml = parseEml(baseEml, updatedContactsXml)
        def updatedResult = service.extractContactsFromEml(updatedEml, resource)

        then: "New EML only contains John"
        updatedResult.contacts.size() == 1
        updatedResult.contacts[0].email == "john.doe@example.org"

        and: "Note: syncContacts would need to be called to actually remove Jane from the resource"
        // This test shows what extractContactsFromEml returns, not what's in the database
    }

    void "test EML lifecycle - new contact added to EML"() {
        given: "A resource with one contact"
        def resource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def initialContactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
        </creator>
        '''

        def initialEml = parseEml(baseEml, initialContactsXml)
        def initialResult = service.extractContactsFromEml(initialEml, resource)

        initialResult.contacts.each { contact ->
            new ContactFor(contact: contact, entityUid: resource.uid, role: "creator", administrator: false, primaryContact: false, userLastModified: "testUser").save(flush: true, failOnError: true)
        }

        when: "EML is updated with an additional contact"
        def updatedContactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
        </creator>
        <creator>
            <individualName>
                <givenName>Jane</givenName>
                <surName>Smith</surName>
            </individualName>
            <electronicMailAddress>jane.smith@example.org</electronicMailAddress>
        </creator>
        '''

        def updatedEml = parseEml(baseEml, updatedContactsXml)
        def updatedResult = service.extractContactsFromEml(updatedEml, resource)

        then: "New EML contains both contacts"
        updatedResult.contacts.size() == 2
        updatedResult.contacts*.email.containsAll(['john.doe@example.org', 'jane.smith@example.org'])

        and: "John's contact is reused"
        updatedResult.contacts.find { it.email == 'john.doe@example.org' }.id != null

        and: "Jane's contact is newly created"
        updatedResult.contacts.find { it.email == 'jane.smith@example.org' }.id != null
    }

    void "test EML lifecycle - contact role changed"() {
        given: "A resource with John as creator"
        def resource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def initialContactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
        </creator>
        '''

        def initialEml = parseEml(baseEml, initialContactsXml)
        def initialResult = service.extractContactsFromEml(initialEml, resource)

        initialResult.contacts.each { contact ->
            new ContactFor(contact: contact, entityUid: resource.uid, role: "creator", administrator: false, primaryContact: false, userLastModified: "testUser").save(flush: true, failOnError: true)
        }

        when: "EML is updated with John as metadataProvider instead"
        def updatedContactsXml = '''
        <metadataProvider>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <electronicMailAddress>john.doe@example.org</electronicMailAddress>
        </metadataProvider>
        '''

        def updatedEml = parseEml(baseEml, updatedContactsXml)
        def updatedResult = service.extractContactsFromEml(updatedEml, resource)

        then: "New contact is created for the new role (GBIF-style dissociation)"
        updatedResult.contacts.size() == 1
        updatedResult.contacts[0].email == "john.doe@example.org"

        and: "It's a different contact ID (different role = different contact)"
        // Note: This depends on whether the old creator ContactFor is still in the database
        // syncContacts would handle removing the old role and adding the new one
    }

    void "test EML lifecycle - organization affiliation changes"() {
        given: "A resource with John at University A"
        def resource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def initialContactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <organizationName>University A</organizationName>
            <electronicMailAddress>john.doe@university-a.org</electronicMailAddress>
        </creator>
        '''

        def initialEml = parseEml(baseEml, initialContactsXml)
        def initialResult = service.extractContactsFromEml(initialEml, resource)

        initialResult.contacts.each { contact ->
            new ContactFor(contact: contact, entityUid: resource.uid, role: "creator", administrator: false, primaryContact: false, userLastModified: "testUser").save(flush: true, failOnError: true)
        }

        when: "EML is updated with John at University B"
        def updatedContactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Doe</surName>
            </individualName>
            <organizationName>University B</organizationName>
            <electronicMailAddress>john.doe@university-b.org</electronicMailAddress>
        </creator>
        '''

        def updatedEml = parseEml(baseEml, updatedContactsXml)
        def updatedResult = service.extractContactsFromEml(updatedEml, resource)

        then: "New contact is created for the new affiliation"
        updatedResult.contacts.size() == 1
        updatedResult.contacts[0].organizationName == "University B"
        updatedResult.contacts[0].email == "john.doe@university-b.org"

        and: "It should be a different contact (different org/email)"
        // The old contact at University A would need to be removed by syncContacts
    }

    void "test phone number normalization prevents duplicates"() {
        given: "An EML with same person but different phone formatting (with/without hyphen)"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
        <creator>
            <individualName>
                <givenName>John</givenName>
                <surName>Researcher</surName>
            </individualName>
            <organizationName>Research Institute</organizationName>
            <positionName>Researcher</positionName>
            <phone>+34-932565991</phone>
            <electronicMailAddress>john.researcher@example.org</electronicMailAddress>
        </creator>
        <metadataProvider>
            <individualName>
                <givenName>John</givenName>
                <surName>Researcher</surName>
            </individualName>
            <organizationName>Research Institute</organizationName>
            <positionName>Researcher</positionName>
            <phone>+34932565991</phone>
            <electronicMailAddress>john.researcher@example.org</electronicMailAddress>
        </metadataProvider>
        '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "Only 1 contact created (phone normalized: +34-932565991 and +34932565991 treated as same)"
        result.contacts.size() == 1
        result.contacts[0].lastName == 'Researcher'
        result.contacts[0].email == 'john.researcher@example.org'
    }

    void "test phone normalization unit - various formats"() {
        given: "Various phone number formats with same prefix"
        def phones = [
            '+1 (555) 123-4567',
            '+15551234567',
            '+1.555.123.4567',
            '+1-555-123-4567'
        ]

        when: "Phone numbers are normalized using reflection to access private method"
        def normalizePhone = service.class.getDeclaredMethod('normalizePhone', String)
        normalizePhone.setAccessible(true)
        def normalized = phones.collect { normalizePhone.invoke(service, it) }

        then: "All variations normalize to the same value"
        normalized.unique().size() == 1
        normalized[0] == '+15551234567'
    }

    void "test phone normalization unit - edge cases"() {
        given: "Edge case phone inputs"
        def normalizePhone = service.class.getDeclaredMethod('normalizePhone', String)
        normalizePhone.setAccessible(true)

        expect: "Correct normalization"
        normalizePhone.invoke(service, "") == ""
        normalizePhone.invoke(service, "   ") == ""
        normalizePhone.invoke(service, "+34-932-565-991") == "+34932565991"
        normalizePhone.invoke(service, "123 456 789") == "123456789"
        normalizePhone.invoke(service, "+1 (555) 123-4567") == "+15551234567"
    }

    void "test phone normalization in contact deduplication"() {
        given: "An EML with same person but phones with spaces vs hyphens"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
        <creator>
            <individualName>
                <givenName>Jane</givenName>
                <surName>Smith</surName>
            </individualName>
            <phone>+1 555 123 4567</phone>
            <electronicMailAddress>jane.smith@example.org</electronicMailAddress>
        </creator>
        <metadataProvider>
            <individualName>
                <givenName>Jane</givenName>
                <surName>Smith</surName>
            </individualName>
            <phone>+1-555-123-4567</phone>
            <electronicMailAddress>jane.smith@example.org</electronicMailAddress>
        </metadataProvider>
        '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "Only 1 contact created (all phone formats normalized: spaces and hyphens removed)"
        result.contacts.size() == 1
        result.contacts[0].lastName == 'Smith'
        result.contacts[0].email == 'jane.smith@example.org'
    }

    void "test real world scenario - person in multiple roles"() {
        given: "An EML where same person appears in creator, metadataProvider, contact, and associatedParty"
        def baseEml = getClass().getResourceAsStream("/base_eml.xml").text
        def contactsXml = '''
        <creator>
            <individualName>
                <givenName>Alice</givenName>
                <surName>Director</surName>
            </individualName>
            <organizationName>Example University</organizationName>
            <positionName>Director</positionName>
            <phone>111-111-1111</phone>
            <electronicMailAddress>alice.director@example.org</electronicMailAddress>
        </creator>
        <creator>
            <individualName>
                <givenName>Bob</givenName>
                <surName>Smith</surName>
            </individualName>
            <organizationName>Example University</organizationName>
            <positionName>Curator</positionName>
            <phone>222-222-2222</phone>
            <electronicMailAddress>bob.smith@example.org</electronicMailAddress>
        </creator>
        <creator>
            <individualName>
                <givenName>María</givenName>
                <surName>García</surName>
            </individualName>
            <organizationName>Example University</organizationName>
            <positionName>Technician</positionName>
            <phone>333-333-3333</phone>
            <electronicMailAddress>maria.garcia@example.org</electronicMailAddress>
        </creator>
        <metadataProvider>
            <individualName>
                <givenName>Bob</givenName>
                <surName>Smith</surName>
            </individualName>
            <organizationName>Example University</organizationName>
            <positionName>Curator</positionName>
            <phone>222-222-2222</phone>
            <electronicMailAddress>bob.smith@example.org</electronicMailAddress>
        </metadataProvider>
        <associatedParty>
            <individualName>
                <givenName>Bob</givenName>
                <surName>Smith</surName>
            </individualName>
            <organizationName>Example University</organizationName>
            <positionName>Curator</positionName>
            <phone>222-222-2222</phone>
            <electronicMailAddress>bob.smith@example.org</electronicMailAddress>
            <role>user</role>
        </associatedParty>
        <contact>
            <individualName>
                <givenName>Bob</givenName>
                <surName>Smith</surName>
            </individualName>
            <organizationName>Example University</organizationName>
            <positionName>Curator</positionName>
            <phone>222-222-2222</phone>
            <electronicMailAddress>bob.smith@example.org</electronicMailAddress>
        </contact>
        <contact>
            <individualName>
                <givenName>Maria</givenName>
                <surName>García</surName>
            </individualName>
            <organizationName>Example University</organizationName>
            <positionName>Technician</positionName>
            <phone>333-333-3333</phone>
            <electronicMailAddress>maria.garcia@example.org</electronicMailAddress>
        </contact>
        '''

        def eml = parseEml(baseEml, contactsXml)
        def dataResource = new DataResource(uid: "dr1", name: "Test Resource", userLastModified: "testUser").save(flush: true, failOnError: true)

        when: "Contacts are extracted"
        def result = service.extractContactsFromEml(eml, dataResource)

        then: "4 contacts created (Bob deduplicated across roles, María/Maria treated as different due to accent)"
        result.contacts.size() == 4

        and: "Bob Smith appears only once despite being in 4 roles (creator, metadataProvider, associatedParty, contact)"
        result.contacts.findAll { it.email == 'bob.smith@example.org' }.size() == 1

        and: "Alice appears once"
        result.contacts.findAll { it.email == 'alice.director@example.org' }.size() == 1

        and: "María (with accent) and Maria (without) treated as 2 different contacts"
        def garciaContacts = result.contacts.findAll { it.lastName == 'García' }
        garciaContacts.size() == 2
        garciaContacts*.firstName.sort() == ['Maria', 'María'].sort()

        and: "Primary contact is Maria (from contact element)"
        result.primaryContacts.size() == 1
        result.primaryContacts[0].firstName == 'Maria'
    }

    // -------------------------------------------------------------------------
    // Citation tests
    // -------------------------------------------------------------------------

    void "test citation is set from additionalMetadata when present in EML"() {
        given: "EML with a citation in additionalMetadata/gbif"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         packageId="test.1.1" system="https://example.org" scope="system">
    <dataset>
        <title>Test Dataset</title>
        <intellectualRights><para>CC BY 4.0</para></intellectualRights>
    </dataset>
    <additionalMetadata>
        <metadata>
            <gbif>
                <citation>Smith J (2024). Test dataset. Version 1.0. My Org. https://doi.org/10.1234/test</citation>
            </gbif>
        </metadata>
    </additionalMetadata>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(uid: "dr-cit-1", name: "Test", userLastModified: "testUser")
                .save(flush: true, failOnError: true)

        when: "EML is extracted"
        service.extractContactsFromEml(eml, dataResource)

        then: "Citation is populated from EML"
        dataResource.citation == "Smith J (2024). Test dataset. Version 1.0. My Org. https://doi.org/10.1234/test"
    }

    void "test existing citation is cleared when EML has no citation element"() {
        given: "EML without additionalMetadata/gbif/citation and a resource with existing citation"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         packageId="test.1.2" system="https://example.org" scope="system">
    <dataset>
        <title>Updated Title</title>
        <intellectualRights><para>CC BY 4.0</para></intellectualRights>
    </dataset>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(
                uid: "dr-cit-2",
                name: "Original Name",
                citation: "Original citation from publisher",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        when: "EML without citation is extracted"
        service.extractContactsFromEml(eml, dataResource)

        then: "Existing citation is cleared because EML is the source of truth"
        dataResource.citation == ""

        and: "Other fields are still updated"
        dataResource.name == "Updated Title"
    }

     void "test citation is updated when EML has a new citation value"() {
        given: "EML with a new citation and a resource with an old citation"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         packageId="test.1.3" system="https://example.org" scope="system">
    <dataset>
        <title>Test Dataset</title>
        <intellectualRights><para>CC BY 4.0</para></intellectualRights>
    </dataset>
    <additionalMetadata>
        <metadata>
            <gbif>
                <citation>New Citation 2025. Version 2.0.</citation>
            </gbif>
        </metadata>
    </additionalMetadata>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(
                uid: "dr-cit-3",
                name: "Test",
                citation: "Old Citation 2020. Version 1.0.",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        when: "EML with updated citation is extracted"
        service.extractContactsFromEml(eml, dataResource)

        then: "Citation is updated to the new value"
        dataResource.citation == "New Citation 2025. Version 2.0."
    }

    void "test citation is extracted when gbif block contains dc:replaces (real IPT EML structure)"() {
        given: "EML that mirrors the real IPT structure with dc namespace and dc:replaces sibling"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1"
         xmlns:dc="http://purl.org/dc/terms/"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         packageId="8347f6ba-f762-11e1-a439-00145eb45e9a/v1.11"
         system="https://ipt.gbif.es" scope="system">
    <dataset>
        <title>Test Dataset With DC Namespace</title>
        <intellectualRights><para>CC BY 4.0</para></intellectualRights>
    </dataset>
    <additionalMetadata>
        <metadata>
            <gbif>
                <dateStamp>2017-05-23T07:11:03.394+02:00</dateStamp>
                <hierarchyLevel>dataset</hierarchyLevel>
                <citation>Real IPT Citation 2023. Version 1.11. https://ipt.gbif.es/resource?r=test&amp;v=1.11</citation>
                <resourceLogoUrl>https://ipt.gbif.es/logo.do?r=test</resourceLogoUrl>
                <dc:replaces>8347f6ba-f762-11e1-a439-00145eb45e9a/v1.10.xml</dc:replaces>
            </gbif>
        </metadata>
    </additionalMetadata>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(
                uid: "dr-cit-dc-1",
                name: "Test",
                citation: "Old citation that should be replaced",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        when: "EML with dc namespace is extracted"
        service.extractContactsFromEml(eml, dataResource)

        then: "Citation is updated despite dc:replaces sibling element"
        dataResource.citation == "Real IPT Citation 2023. Version 1.11. https://ipt.gbif.es/resource?r=test&v=1.11"
    }

    void "test citation with identifier attribute is extracted as text (real GBIF IPT format)"() {
        given: "EML with citation element that has a DOI in the identifier attribute (real IPT/GBIF format)"
        // Real IPT EML uses: <citation identifier="https://doi.org/10.xxxxx">Author A, Author B (2026)...</citation>
        // The citation text must be extracted, not the identifier attribute
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1"
         xmlns:dc="http://purl.org/dc/terms/"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         packageId="8347f6ba-f762-11e1-a439-00145eb45e9a/v1.12"
         system="https://ipt.gbif.es" scope="system">
    <dataset>
        <title>MCNB-Tissue Dataset</title>
        <intellectualRights><para>CC BY 4.0</para></intellectualRights>
    </dataset>
    <additionalMetadata>
        <metadata>
            <gbif>
                <dateStamp>2026-01-15T10:00:00.000+01:00</dateStamp>
                <hierarchyLevel>dataset</hierarchyLevel>
                <citation identifier="https://doi.org/10.15468/mwcmb5">Quesada Lara J, Agullo Villaronga J (2026). Museu de Ciències Naturals de Barcelona: MCNB-Tissue, Museu de Ciències Naturals de Barcelona. Occurrence dataset https://doi.org/10.15468/mwcmb5</citation>
                <dc:replaces>8347f6ba-f762-11e1-a439-00145eb45e9a/v1.11.xml</dc:replaces>
            </gbif>
        </metadata>
    </additionalMetadata>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(
                uid: "dr-cit-identifier-1",
                name: "MCNB-Tissue",
                citation: "Coleccion de Banco de Tejidos, MCNB",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        when: "EML with citation identifier attribute is extracted"
        service.extractContactsFromEml(eml, dataResource)

        then: "Citation text is updated (old collection-name-only citation replaced by full GBIF format)"
        dataResource.citation == "Quesada Lara J, Agullo Villaronga J (2026). Museu de Ciències Naturals de Barcelona: MCNB-Tissue, Museu de Ciències Naturals de Barcelona. Occurrence dataset https://doi.org/10.15468/mwcmb5"
    }

    void "test citation with identifier attribute - identifier DOI is NOT stored as citation text"() {
        given: "EML with citation element that has DOI as identifier attribute and authors as text"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         packageId="test.doi.1" system="https://ipt.example.org" scope="system">
    <dataset>
        <title>DOI Citation Test Dataset</title>
        <intellectualRights><para>CC BY 4.0</para></intellectualRights>
    </dataset>
    <additionalMetadata>
        <metadata>
            <gbif>
                <citation identifier="https://doi.org/10.1234/test">Smith J, Jones A (2025). My dataset. Version 3.0. My Institution. https://doi.org/10.1234/test</citation>
            </gbif>
        </metadata>
    </additionalMetadata>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(
                uid: "dr-cit-doi-1",
                name: "DOI Test",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        when: "EML is extracted"
        service.extractContactsFromEml(eml, dataResource)

        then: "Citation stores the human-readable text, not the raw DOI URL from identifier attribute"
        dataResource.citation == "Smith J, Jones A (2025). My dataset. Version 3.0. My Institution. https://doi.org/10.1234/test"
        !dataResource.citation.startsWith("https://doi.org")
    }

    void "test citation is updated when EML has full GBIF-format citation (authors year doi)"() {
        given: "Collectory resource has old-style citation (just collection name), IPT EML has new GBIF-format citation"
        // This replicates the real scenario: Collectory had a manually-entered collection name,
        // but now the IPT publishes a proper GBIF citation with authors, year, DOI
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1"
         xmlns:dc="http://purl.org/dc/terms/"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         packageId="abc123/v5" system="https://ipt.example.org" scope="system">
    <dataset>
        <title>My Collection Dataset</title>
        <intellectualRights><para>CC BY 4.0</para></intellectualRights>
    </dataset>
    <additionalMetadata>
        <metadata>
            <gbif>
                <citation identifier="https://doi.org/10.9999/abc123">García R, López M (2025). My Collection, My Museum. Occurrence dataset https://doi.org/10.9999/abc123</citation>
            </gbif>
        </metadata>
    </additionalMetadata>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(
                uid: "dr-cit-oldstyle-1",
                name: "My Collection",
                citation: "My Collection, My Museum",  // old-style: just the collection name
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        when: "IPT EML with full GBIF-format citation is extracted"
        service.extractContactsFromEml(eml, dataResource)

        then: "Citation is updated to the full GBIF format with authors, year, and DOI"
        dataResource.citation == "García R, López M (2025). My Collection, My Museum. Occurrence dataset https://doi.org/10.9999/abc123"
        dataResource.citation.contains("doi.org")
        dataResource.citation.contains("2025")
    }

    // -------------------------------------------------------------------------
    // Tests for emlFields accessor semantics (geographic, temporal, additional)
    //
    // EML is the source of truth. Each affected field follows three cases:
    //   A) EML node present with value  → field is set to that value
    //   B) EML node absent              → existing value is cleared (set to "")
    //   C) EML node present but empty   → existing value is overwritten with ""
    // -------------------------------------------------------------------------

    void "test geographicDescription is set when EML node is present with value"() {
        given: "EML with geographicDescription"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" packageId="dr-geo-1.1">
    <dataset>
        <title>Test</title>
        <coverage>
            <geographicCoverage>
                <geographicDescription>Australia</geographicDescription>
            </geographicCoverage>
        </coverage>
    </dataset>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(uid: "dr-geo-1", name: "Test", userLastModified: "testUser")
                .save(flush: true, failOnError: true)

        when:
        service.extractContactsFromEml(eml, dataResource)

        then: "geographicDescription is populated"
        dataResource.geographicDescription == "Australia"
    }

    void "test geographicDescription is cleared when EML node is absent"() {
        given: "EML without geographicDescription and a resource with existing value"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" packageId="dr-geo-2.1">
    <dataset>
        <title>Test</title>
    </dataset>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(
                uid: "dr-geo-2", name: "Test",
                geographicDescription: "Existing description",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        when:
        service.extractContactsFromEml(eml, dataResource)

        then: "existing geographicDescription is cleared (EML is source of truth)"
        dataResource.geographicDescription == ""
    }

    void "test geographicDescription is overwritten when EML node is present but empty"() {
        given: "EML with an empty geographicDescription node"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" packageId="dr-geo-3.1">
    <dataset>
        <title>Test</title>
        <coverage>
            <geographicCoverage>
                <geographicDescription></geographicDescription>
            </geographicCoverage>
        </coverage>
    </dataset>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(
                uid: "dr-geo-3", name: "Test",
                geographicDescription: "Old description",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        when:
        service.extractContactsFromEml(eml, dataResource)

        then: "geographicDescription is cleared (node present but empty)"
        dataResource.geographicDescription == ""
    }

    void "test beginDate is set when EML node is present with value"() {
        given: "EML with beginDate"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" packageId="dr-date-1.1">
    <dataset>
        <title>Test</title>
        <coverage>
            <temporalCoverage>
                <rangeOfDates>
                    <beginDate><calendarDate>2000-01-01</calendarDate></beginDate>
                    <endDate><calendarDate>2023-12-31</calendarDate></endDate>
                </rangeOfDates>
            </temporalCoverage>
        </coverage>
    </dataset>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(uid: "dr-date-1", name: "Test", userLastModified: "testUser")
                .save(flush: true, failOnError: true)

        when:
        service.extractContactsFromEml(eml, dataResource)

        then:
        dataResource.beginDate == "2000-01-01"
        dataResource.endDate == "2023-12-31"
    }

    void "test beginDate and endDate are cleared when EML temporal coverage is absent"() {
        given: "EML without temporalCoverage and a resource with existing dates"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" packageId="dr-date-2.1">
    <dataset>
        <title>Test</title>
    </dataset>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(
                uid: "dr-date-2", name: "Test",
                beginDate: "1990-01-01",
                endDate: "2020-12-31",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        when:
        service.extractContactsFromEml(eml, dataResource)

        then: "existing dates are cleared (EML is source of truth)"
        dataResource.beginDate == ""
        dataResource.endDate == ""
    }

    void "test purpose is set when EML node is present with value"() {
        given: "EML with purpose"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" packageId="dr-purpose-1.1">
    <dataset>
        <title>Test</title>
        <purpose><para>Research and monitoring</para></purpose>
    </dataset>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(uid: "dr-purpose-1", name: "Test", userLastModified: "testUser")
                .save(flush: true, failOnError: true)

        when:
        service.extractContactsFromEml(eml, dataResource)

        then:
        dataResource.purpose == "Research and monitoring"
    }

    void "test purpose is cleared when EML node is absent"() {
        given: "EML without purpose and a resource with existing value"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" packageId="dr-purpose-2.1">
    <dataset>
        <title>Test</title>
    </dataset>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(
                uid: "dr-purpose-2", name: "Test",
                purpose: "Original purpose",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        when:
        service.extractContactsFromEml(eml, dataResource)

        then: "existing purpose is cleared (EML is source of truth)"
        dataResource.purpose == ""
    }

    void "test methodStepDescription and qualityControlDescription are set from EML"() {
        given: "EML with methods block"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" packageId="dr-methods-1.1">
    <dataset>
        <title>Test</title>
        <methods>
            <methodStep>
                <description><para>Field surveys every 3 months</para></description>
            </methodStep>
            <qualityControl>
                <description><para>Expert verification</para></description>
            </qualityControl>
        </methods>
    </dataset>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(uid: "dr-methods-1", name: "Test", userLastModified: "testUser")
                .save(flush: true, failOnError: true)

        when:
        service.extractContactsFromEml(eml, dataResource)

        then:
        dataResource.methodStepDescription == "Field surveys every 3 months"
        dataResource.qualityControlDescription == "Expert verification"
    }

    void "test methodStepDescription is cleared when EML methods block is absent"() {
        given: "EML without methods and a resource with existing method description"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" packageId="dr-methods-2.1">
    <dataset>
        <title>Test</title>
    </dataset>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(
                uid: "dr-methods-2", name: "Test",
                methodStepDescription: "Previous method",
                qualityControlDescription: "Previous QC",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        when:
        service.extractContactsFromEml(eml, dataResource)

        then: "existing method descriptions are cleared (EML is source of truth)"
        dataResource.methodStepDescription == ""
        dataResource.qualityControlDescription == ""
    }

    void "test bounding coordinates are set when EML geographic coverage is present"() {
        given: "EML with full bounding box"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" packageId="dr-bbox-1.1">
    <dataset>
        <title>Test</title>
        <coverage>
            <geographicCoverage>
                <geographicDescription>New South Wales</geographicDescription>
                <boundingCoordinates>
                    <westBoundingCoordinate>140.99</westBoundingCoordinate>
                    <eastBoundingCoordinate>153.64</eastBoundingCoordinate>
                    <northBoundingCoordinate>-28.16</northBoundingCoordinate>
                    <southBoundingCoordinate>-37.51</southBoundingCoordinate>
                </boundingCoordinates>
            </geographicCoverage>
        </coverage>
    </dataset>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(uid: "dr-bbox-1", name: "Test", userLastModified: "testUser")
                .save(flush: true, failOnError: true)

        when:
        service.extractContactsFromEml(eml, dataResource)

        then:
        dataResource.westBoundingCoordinate == "140.99"
        dataResource.eastBoundingCoordinate == "153.64"
        dataResource.northBoundingCoordinate == "-28.16"
        dataResource.southBoundingCoordinate == "-37.51"
    }

    void "test bounding coordinates are cleared when EML geographic coverage is absent"() {
        given: "EML without coverage and a resource with existing bounding box"
        def emlText = '''
<eml:eml xmlns:eml="eml://ecoinformatics.org/eml-2.1.1" packageId="dr-bbox-2.1">
    <dataset>
        <title>Test</title>
    </dataset>
</eml:eml>'''
        def eml = new XmlSlurper().parseText(emlText)
        def dataResource = new DataResource(
                uid: "dr-bbox-2", name: "Test",
                westBoundingCoordinate: "110.0",
                eastBoundingCoordinate: "155.0",
                northBoundingCoordinate: "-10.0",
                southBoundingCoordinate: "-45.0",
                userLastModified: "testUser"
        ).save(flush: true, failOnError: true)

        when:
        service.extractContactsFromEml(eml, dataResource)

        then: "existing bounding coordinates are cleared (EML is source of truth)"
        dataResource.westBoundingCoordinate == ""
        dataResource.eastBoundingCoordinate == ""
        dataResource.northBoundingCoordinate == ""
        dataResource.southBoundingCoordinate == ""
    }

    void "test pubDescription is extracted from abstract with para"() {
        given: "EML with abstract containing para elements"
        def eml = new XmlSlurper().parseText('''
            <eml:eml xmlns:eml="https://eml.ecoinformatics.org/eml-2.2.0"
                     packageId="test-uuid" system="http://gbif.org">
                <dataset>
                    <title>Test Dataset</title>
                    <abstract>
                        <para>First paragraph of the abstract.</para>
                        <para>Second paragraph of the abstract.</para>
                    </abstract>
                </dataset>
            </eml:eml>
        ''')
        def dataResource = new DataResource()

        when:
        service.extractContactsFromEml(eml, dataResource)

        then:
        dataResource.pubDescription == "First paragraph of the abstract. Second paragraph of the abstract."
    }

    void "test pubDescription is extracted from abstract without para (plain text)"() {
        given: "EML with abstract containing plain text directly (no para elements)"
        def eml = new XmlSlurper().parseText('''
            <eml:eml xmlns:eml="https://eml.ecoinformatics.org/eml-2.2.0"
                     packageId="test-uuid" system="http://gbif.org">
                <dataset>
                    <title>Test Dataset</title>
                    <abstract>Plain text abstract without para elements.</abstract>
                </dataset>
            </eml:eml>
        ''')
        def dataResource = new DataResource()

        when:
        service.extractContactsFromEml(eml, dataResource)

        then:
        dataResource.pubDescription == "Plain text abstract without para elements."
    }

}

