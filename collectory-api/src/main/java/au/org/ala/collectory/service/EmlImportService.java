package au.org.ala.collectory.service;

import au.org.ala.collectory.domain.Contact;
import au.org.ala.collectory.domain.ContactFor;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.domain.Licence;
import au.org.ala.collectory.repository.ContactForRepository;
import au.org.ala.collectory.repository.ContactRepository;
import au.org.ala.collectory.repository.LicenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmlImportService {

    private final ContactRepository contactRepository;
    private final ContactForRepository contactForRepository;
    private final LicenceRepository licenceRepository;
    private final CollectoryAuthService collectoryAuthService;

    /**
     * Normalize phone number by removing formatting characters.
     */
    private String normalizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return "";
        return phone.trim().replaceAll("[\\s\\-().]", "").toLowerCase();
    }

    /**
     * Collect text content from multiple 'para' child elements.
     */
    private String collectParas(Element parent) {
        if (parent == null) return null;
        NodeList paras = parent.getElementsByTagName("para");
        if (paras.getLength() == 0) {
            // Try direct text content
            String text = parent.getTextContent();
            return text != null ? text.trim() : null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paras.getLength(); i++) {
            String text = paras.item(i).getTextContent();
            if (text != null) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(text.trim());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Get text content of a child element by tag name.
     */
    private String getChildText(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            return (text != null && !text.trim().isEmpty()) ? text.trim() : null;
        }
        return null;
    }

    /**
     * Get first child element by tag name.
     */
    private Element getChildElement(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    /**
     * Get direct child elements (not nested).
     */
    private List<Element> getDirectChildElements(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        if (parent == null) return result;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && el.getTagName().equals(tagName)) {
                result.add(el);
            }
        }
        return result;
    }

    /**
     * Extract EML fields and populate DataResource properties.
     */
    public void extractEmlFields(Document doc, DataResource dataResource) {
        Element root = doc.getDocumentElement();
        Element dataset = getChildElement(root, "dataset");
        if (dataset == null) return;

        // guid (packageId attribute)
        String packageId = root.getAttribute("packageId");
        if (packageId != null && !packageId.isEmpty()) {
            dataResource.setGuid(packageId);
        }

        // name
        String title = getChildText(dataset, "title");
        if (title != null) dataResource.setName(title);

        // pubDescription
        Element abstractEl = getChildElement(dataset, "abstract");
        String pubDesc = collectParas(abstractEl);
        if (pubDesc != null) dataResource.setPubDescription(pubDesc);

        // contact email and phone
        List<Element> contactElements = getDirectChildElements(dataset, "contact");
        if (!contactElements.isEmpty()) {
            Element firstContact = contactElements.get(0);
            String email = getChildText(firstContact, "electronicMailAddress");
            if (email != null) dataResource.setEmail(email);
            String phone = getChildText(firstContact, "phone");
            if (phone != null) dataResource.setPhone(phone);

            // state from contact address
            Element address = getChildElement(firstContact, "address");
            if (address != null) {
                String adminArea = getChildText(address, "administrativeArea");
                if (adminArea != null && !adminArea.isEmpty()) {
                    dataResource.setState(adminArea);
                }
            }
        }

        // rights
        Element intellectualRights = getChildElement(dataset, "intellectualRights");
        String rights = collectParas(intellectualRights);
        if (rights != null) dataResource.setRights(rights);

        // citation
        Element additionalMetadata = getChildElement(root, "additionalMetadata");
        if (additionalMetadata != null) {
            Element metadata = getChildElement(additionalMetadata, "metadata");
            if (metadata != null) {
                Element gbif = getChildElement(metadata, "gbif");
                if (gbif != null) {
                    String citation = getChildText(gbif, "citation");
                    if (citation != null) dataResource.setCitation(citation);
                }
            }
        }

        // geographic coverage
        Element coverage = getChildElement(dataset, "coverage");
        if (coverage != null) {
            Element geoCov = getChildElement(coverage, "geographicCoverage");
            if (geoCov != null) {
                String geoDesc = getChildText(geoCov, "geographicDescription");
                if (geoDesc != null) dataResource.setGeographicDescription(geoDesc);

                Element bounds = getChildElement(geoCov, "boundingCoordinates");
                if (bounds != null) {
                    String north = getChildText(bounds, "northBoundingCoordinate");
                    String south = getChildText(bounds, "southBoundingCoordinate");
                    String east = getChildText(bounds, "eastBoundingCoordinate");
                    String west = getChildText(bounds, "westBoundingCoordinate");
                    if (north != null) dataResource.setNorthBoundingCoordinate(north);
                    if (south != null) dataResource.setSouthBoundingCoordinate(south);
                    if (east != null) dataResource.setEastBoundingCoordinate(east);
                    if (west != null) dataResource.setWestBoundingCoordinate(west);
                }
            }

            // temporal coverage
            Element tempCov = getChildElement(coverage, "temporalCoverage");
            if (tempCov != null) {
                Element range = getChildElement(tempCov, "rangeOfDates");
                if (range != null) {
                    Element beginEl = getChildElement(range, "beginDate");
                    Element endEl = getChildElement(range, "endDate");
                    if (beginEl != null) {
                        String beginDate = getChildText(beginEl, "calendarDate");
                        if (beginDate != null) dataResource.setBeginDate(beginDate);
                    }
                    if (endEl != null) {
                        String endDate = getChildText(endEl, "calendarDate");
                        if (endDate != null) dataResource.setEndDate(endDate);
                    }
                }
            }
        }

        // purpose
        Element purpose = getChildElement(dataset, "purpose");
        if (purpose != null) {
            String purposeText = getChildText(purpose, "para");
            if (purposeText != null) dataResource.setPurpose(purposeText);
        }

        // methods
        Element methods = getChildElement(dataset, "methods");
        if (methods != null) {
            Element methodStep = getChildElement(methods, "methodStep");
            if (methodStep != null) {
                Element desc = getChildElement(methodStep, "description");
                if (desc != null) {
                    String methodDesc = getChildText(desc, "para");
                    if (methodDesc != null) dataResource.setMethodStepDescription(methodDesc);
                }
            }
            Element qualityControl = getChildElement(methods, "qualityControl");
            if (qualityControl != null) {
                Element desc = getChildElement(qualityControl, "description");
                if (desc != null) {
                    String qcDesc = getChildText(desc, "para");
                    if (qcDesc != null) dataResource.setQualityControlDescription(qcDesc);
                }
            }
        }

        // gbifDoi from alternateIdentifier
        List<Element> altIds = getDirectChildElements(dataset, "alternateIdentifier");
        for (Element altId : altIds) {
            String id = altId.getTextContent();
            if (id != null && id.startsWith("doi")) {
                dataResource.setGbifDoi(id);
                break;
            }
        }

        // licence
        Map<String, String> licenceInfo = getLicence(doc);
        if (licenceInfo.get("licenseType") != null && !licenceInfo.get("licenseType").isEmpty()) {
            dataResource.setLicenseType(licenceInfo.get("licenseType"));
        }
        if (licenceInfo.get("licenseVersion") != null && !licenceInfo.get("licenseVersion").isEmpty()) {
            dataResource.setLicenseVersion(licenceInfo.get("licenseVersion"));
        }
    }

    /**
     * Match licence from EML intellectual rights against known Licence entities.
     * Tries matching by acronym first, then by URL (with http/https fallback).
     */
    public Map<String, String> getLicence(Document doc) {
        Map<String, String> licenceInfo = new HashMap<>();
        licenceInfo.put("licenseType", "");
        licenceInfo.put("licenseVersion", "");

        Element dataset = getChildElement(doc.getDocumentElement(), "dataset");
        if (dataset == null) return licenceInfo;

        Element intellectualRights = getChildElement(dataset, "intellectualRights");
        String rights = collectParas(intellectualRights);

        Licence matchedLicence = null;
        if (rights != null) {
            matchedLicence = licenceRepository.findByAcronym(rights).orElse(null);
        }

        if (matchedLicence == null && intellectualRights != null) {
            Element ulink = getChildElement(intellectualRights, "ulink");
            if (ulink != null) {
                String licenceUrl = ulink.getAttribute("url");
                if (licenceUrl != null && !licenceUrl.isEmpty()) {
                    matchedLicence = licenceRepository.findByUrl(licenceUrl).orElse(null);
                    if (matchedLicence == null) {
                        // Try switching http/https
                        if (licenceUrl.contains("http://")) {
                            matchedLicence = licenceRepository.findByUrl(licenceUrl.replace("http://", "https://")).orElse(null);
                        } else {
                            matchedLicence = licenceRepository.findByUrl(licenceUrl.replace("https://", "http://")).orElse(null);
                        }
                    }
                }
            }
        }

        if (matchedLicence != null) {
            licenceInfo.put("licenseType", matchedLicence.getAcronym());
            licenceInfo.put("licenseVersion", matchedLicence.getLicenceVersion());
        }

        return licenceInfo;
    }

    /**
     * Extract contacts from EML input stream and populate DataResource.
     *
     * @param emlInputStream the input stream of the eml.xml file
     * @param dataResource   the data resource being imported
     * @return a map containing "contacts" and "primaryContacts" lists
     */
    @Transactional
    public Map<String, Object> extractContactsFromEml(InputStream emlInputStream, DataResource dataResource) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(emlInputStream);

            return extractContactsFromEml(doc, dataResource);
        } catch (Exception e) {
            log.error("Error parsing EML: {}", e.getMessage(), e);
            return Map.of("contacts", List.of(), "primaryContacts", List.of());
        }
    }

    /**
     * Extract contacts from parsed EML document.
     * Processes creator, contact, metadataProvider, and associatedParty elements,
     * deduplicating by a composite key of name/email/phone/org/position.
     */
    @Transactional
    public Map<String, Object> extractContactsFromEml(Document doc, DataResource dataResource) {
        List<Contact> contacts = new ArrayList<>();
        List<Contact> primaryContacts = new ArrayList<>();
        Set<String> processedContactKeys = new HashSet<>();

        // Extract fields
        extractEmlFields(doc, dataResource);

        Element dataset = getChildElement(doc.getDocumentElement(), "dataset");
        if (dataset == null) return Map.of("contacts", contacts, "primaryContacts", primaryContacts);

        // Process creator
        for (Element creator : getDirectChildElements(dataset, "creator")) {
            addContact(creator, dataResource, contacts, processedContactKeys);
        }

        // Process contact (primary)
        for (Element contact : getDirectChildElements(dataset, "contact")) {
            Contact c = addContact(contact, dataResource, contacts, processedContactKeys);
            if (c != null && !primaryContacts.contains(c)) {
                primaryContacts.add(c);
            }
        }

        // Process metadataProvider
        for (Element mp : getDirectChildElements(dataset, "metadataProvider")) {
            addContact(mp, dataResource, contacts, processedContactKeys);
        }

        // Process associatedParty
        for (Element ap : getDirectChildElements(dataset, "associatedParty")) {
            addContact(ap, dataResource, contacts, processedContactKeys);
        }

        return Map.of("contacts", contacts, "primaryContacts", primaryContacts);
    }

    private Contact addContact(Element provider, DataResource dataResource, List<Contact> contacts, Set<String> processedKeys) {
        Element individualName = getChildElement(provider, "individualName");
        String firstName = getChildText(individualName, "givenName");
        String lastName = getChildText(individualName, "surName");
        String email = getChildText(provider, "electronicMailAddress");
        String phone = getChildText(provider, "phone");
        String org = getChildText(provider, "organizationName");
        String position = getChildText(provider, "positionName");

        boolean hasSurName = lastName != null && !lastName.isEmpty();
        boolean hasOrg = org != null && !org.isEmpty();
        boolean hasPosition = position != null && !position.isEmpty();

        if (!hasSurName && !hasOrg && !hasPosition) return null;

        String normalizedPhone = normalizePhone(phone != null ? phone : "");
        String contactKey = String.format("%s|%s|%s|%s|%s|%s",
                firstName != null ? firstName : "",
                lastName != null ? lastName : "",
                email != null ? email : "",
                normalizedPhone,
                org != null ? org : "",
                position != null ? position : "").toLowerCase();

        if (processedKeys.contains(contactKey)) {
            // Return existing contact
            return contacts.stream()
                    .filter(c -> {
                        String cKey = String.format("%s|%s|%s|%s|%s|%s",
                                c.getFirstName() != null ? c.getFirstName() : "",
                                c.getLastName() != null ? c.getLastName() : "",
                                c.getEmail() != null ? c.getEmail() : "",
                                normalizePhone(c.getPhone() != null ? c.getPhone() : ""),
                                c.getOrganizationName() != null ? c.getOrganizationName() : "",
                                c.getPositionName() != null ? c.getPositionName() : "").toLowerCase();
                        return cKey.equals(contactKey);
                    })
                    .findFirst()
                    .orElse(null);
        }

        Contact contact = addOrUpdateContact(firstName, lastName, email, phone, org, position, null, dataResource);
        if (contact != null) {
            contacts.add(contact);
            processedKeys.add(contactKey);
        }
        return contact;
    }

    /**
     * Find or create a Contact entity scoped to a DataResource.
     * If a matching contact already exists for the DataResource, it is updated;
     * otherwise a new Contact is created and persisted.
     */
    @Transactional
    public Contact addOrUpdateContact(String firstName, String lastName, String email, String phone,
                                       String organizationName, String positionName, String userId,
                                       DataResource dataResource) {
        boolean hasSurName = lastName != null && !lastName.isEmpty();
        boolean hasOrg = organizationName != null && !organizationName.isEmpty();
        boolean hasPosition = positionName != null && !positionName.isEmpty();

        if (!hasSurName && !hasOrg && !hasPosition) return null;

        Contact contact = null;

        // Try to find existing contact associated with this DataResource
        if (dataResource != null && dataResource.getUid() != null) {
            List<ContactFor> existingContactFors = contactForRepository.findAllByEntityUid(dataResource.getUid());
            for (ContactFor cf : existingContactFors) {
                Contact candidate = cf.getContact();
                boolean matches = true;

                if (hasSurName && !Objects.equals(lastName, candidate.getLastName())) matches = false;
                if (matches && firstName != null && !Objects.equals(firstName, candidate.getFirstName())) matches = false;
                if (matches && email != null && candidate.getEmail() != null && !Objects.equals(email, candidate.getEmail())) matches = false;
                if (matches && organizationName != null && candidate.getOrganizationName() != null &&
                        !organizationName.equalsIgnoreCase(candidate.getOrganizationName())) matches = false;
                if (matches && positionName != null && candidate.getPositionName() != null &&
                        !positionName.equalsIgnoreCase(candidate.getPositionName())) matches = false;

                if (matches) {
                    contact = candidate;
                    break;
                }
            }
        }

        if (contact == null) {
            contact = new Contact();
            contact.setFirstName(firstName);
            contact.setLastName(lastName);
            contact.setOrganizationName(organizationName);
            contact.setPositionName(positionName);
            contact.setUserId(userId);
            contact.setEmail(email);
            contact.setPhone(phone);
            contact.setPublish(true);
            contact.setUserLastModified(collectoryAuthService.username());
            contact = contactRepository.saveAndFlush(contact);
        } else {
            boolean updated = false;
            if (phone != null && !phone.equals(contact.getPhone())) { contact.setPhone(phone); updated = true; }
            if (email != null && !email.equals(contact.getEmail())) { contact.setEmail(email); updated = true; }
            if (firstName != null && !firstName.equals(contact.getFirstName())) { contact.setFirstName(firstName); updated = true; }
            if (lastName != null && !lastName.equals(contact.getLastName())) { contact.setLastName(lastName); updated = true; }
            if (organizationName != null && !organizationName.equals(contact.getOrganizationName())) { contact.setOrganizationName(organizationName); updated = true; }
            if (positionName != null && !positionName.equals(contact.getPositionName())) { contact.setPositionName(positionName); updated = true; }

            if (updated) {
                contact.setUserLastModified(collectoryAuthService.username());
                contact = contactRepository.saveAndFlush(contact);
            }
        }

        return contact;
    }
}
