/**
 * Copyright (C) 2011 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */
package au.org.ala.collectory.service;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.domain.Collection;
import au.org.ala.collectory.repository.ContactForRepository;
import au.org.ala.collectory.repository.DataProviderRepository;
import au.org.ala.collectory.repository.DataResourceRepository;
import au.org.ala.collectory.repository.ExternalIdentifierRepository;
import au.org.ala.collectory.repository.LicenceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates EML (Ecological Metadata Language) XML documents for collectory entities.
 * <p>
 * Ported from the Grails EmlRenderService.groovy, using StAX XMLStreamWriter
 * for streaming XML generation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmlRenderService {

    private final AppProperties appProperties;
    private final ProviderGroupService providerGroupService;
    private final LicenceRepository licenceRepository;
    private final ContactForRepository contactForRepository;
    private final ExternalIdentifierRepository externalIdentifierRepository;
    private final DataResourceRepository dataResourceRepository;
    private final DataProviderRepository dataProviderRepository;
    private final ObjectMapper objectMapper;

    // Namespace URIs
    private static final String NS_EML = "eml://ecoinformatics.org/eml-2.1.1";
    private static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String NS_DC = "http://purl.org/dc/terms/";
    private static final String NS_D = "eml://ecoinformatics.org/dataset-2.1.0";
    private static final String SCHEMA_LOCATION =
            "eml://ecoinformatics.org/eml-2.1.1 http://rs.gbif.org/schema/eml-gbif-profile/1.1/eml-gbif-profile.xsd";

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern(DATE_PATTERN).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern(DATE_TIME_PATTERN).withZone(ZoneOffset.UTC);

    // Sentinel value used in Groovy ProviderGroup for "no info available" coordinates
    private static final BigDecimal NO_INFO_AVAILABLE = new BigDecimal("-1");

    // ──────────────────────────────────────────────────────────────────────
    // Public entry point
    // ──────────────────────────────────────────────────────────────────────

    /**
     * General entry point for any entity.
     *
     * @param entity the entity to render as EML
     * @return EML XML string
     */
    public String emlForEntity(ProviderGroup entity) {
        if (entity instanceof DataResource dr) {
            return emlForResource(dr);
        } else if (entity instanceof Collection co) {
            return emlForCollection(co);
        } else {
            return emlForOtherEntity(entity);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // emlForCollection
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Generates EML representation of a Collection.
     */
    public String emlForCollection(Collection pg) {
        try {
            StringWriter sw = new StringWriter();
            XMLStreamWriter w = createWriter(sw);

            w.writeStartDocument("UTF-8", "1.0");

            Identifiers ids = identifiers(pg);
            writeEmlRoot(w, ids);

            // <dataset>
            w.writeStartElement("dataset");

            // alternateIdentifier elements from external identifiers
            writeAlternateIdentifiers(w, pg);

            // common elements 1: title, creator, metadataProvider, associatedParty, pubDate, language, abstract
            commonElements1(w, pg);

            // keywordSet
            w.writeStartElement("keywordSet");
            for (String kw : listKeywords(pg)) {
                writeSimpleElement(w, "keyword", kw);
            }
            writeSimpleElement(w, "keywordThesaurus", "free text");
            w.writeEndElement(); // keywordSet

            // distribution
            writeDistribution(w, pg);

            // coverage
            w.writeStartElement("coverage");
            writeCollectionGeographicCoverage(w, pg);
            writeCollectionTaxonomicCoverage(w, pg);
            w.writeEndElement(); // coverage

            // contact
            writeContacts(w, pg);

            w.writeEndElement(); // dataset

            // additionalMetadata
            w.writeStartElement("additionalMetadata");
            w.writeStartElement("metadata");
            w.writeStartElement("gbif");

            commonElements2(w, pg);

            // collection element
            w.writeStartElement("collection");
            String parentId = pg.getInstitution() != null ? identifiers(pg.getInstitution()).id : "no parent";
            writeSimpleElement(w, "parentCollectionIdentifier", parentId);
            if (ids.id.startsWith("urn:lsid")) {
                writeSimpleElement(w, "collectionIdentifier", ids.id);
            } else {
                writeSimpleElement(w, "collectionIdentifier", pg.buildUri(appProperties.getServerUrl()));
            }
            writeSimpleElement(w, "collectionName", pg.getName());
            w.writeEndElement(); // collection

            if (pg.getStartDate() != null && !pg.getStartDate().isEmpty()) {
                writeSimpleElement(w, "formationPeriod", pg.getStartDate());
            }

            if (pg.getNumRecords() != -1) {
                w.writeStartElement("jgtiCuratorialUnit");
                writeSimpleElement(w, "jgtiUnitType", getCuratorialUnit(pg));
                w.writeStartElement("jgtiUnits");
                w.writeAttribute("uncertaintyMeasure", "1");
                w.writeCharacters(String.valueOf(pg.getNumRecords()));
                w.writeEndElement(); // jgtiUnits
                w.writeEndElement(); // jgtiCuratorialUnit
            }

            w.writeEndElement(); // gbif
            w.writeEndElement(); // metadata
            w.writeEndElement(); // additionalMetadata

            w.writeEndElement(); // eml:eml
            w.writeEndDocument();
            w.flush();

            return prettyPrint(sw.toString());
        } catch (XMLStreamException e) {
            log.error("Error generating EML for collection {}", pg.getUid(), e);
            throw new RuntimeException("Failed to generate EML", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // emlForResource
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Generates EML representation of a DataResource.
     */
    public String emlForResource(DataResource pg) {
        try {
            StringWriter sw = new StringWriter();
            XMLStreamWriter w = createWriter(sw);

            w.writeStartDocument("UTF-8", "1.0");

            Identifiers ids = identifiers(pg);
            writeEmlRoot(w, ids);

            // <dataset>
            w.writeStartElement("dataset");

            // alternateIdentifier elements
            writeAlternateIdentifiers(w, pg);

            // common elements 1
            commonElements1(w, pg);

            // additionalInfo
            if (hasContent(pg.getDataGeneralizations()) || hasContent(pg.getInformationWithheld())) {
                w.writeStartElement("additionalInfo");
                if (hasContent(pg.getDataGeneralizations())) {
                    writeSimpleElement(w, "para", pg.getDataGeneralizations());
                }
                if (hasContent(pg.getInformationWithheld())) {
                    writeSimpleElement(w, "para", pg.getInformationWithheld());
                }
                w.writeEndElement(); // additionalInfo
            }

            // intellectualRights
            writeIntellectualRights(w, pg);

            // distribution
            writeDistribution(w, pg);

            // coverage
            w.writeStartElement("coverage");
            writeResourceGeographicCoverage(w, pg);
            writeResourceTemporalCoverage(w, pg);
            if (hasContent(pg.getTaxonomyHints())) {
                writeTaxonomicCoverage(w, pg.getTaxonomyHints());
            }
            w.writeEndElement(); // coverage

            // purpose
            w.writeStartElement("purpose");
            writeSimpleElement(w, "para", pg.getPurpose() != null ? pg.getPurpose() : "");
            w.writeEndElement(); // purpose

            // contact
            writeContacts(w, pg);

            // methods
            w.writeStartElement("methods");
            if (hasContent(pg.getMethodStepDescription())) {
                w.writeStartElement("methodStep");
                w.writeStartElement("description");
                writeSimpleElement(w, "para", pg.getMethodStepDescription() != null ? pg.getMethodStepDescription() : "");
                w.writeEndElement(); // description
                w.writeEndElement(); // methodStep
            }
            if (hasContent(pg.getQualityControlDescription())) {
                w.writeStartElement("qualityControl");
                w.writeStartElement("description");
                writeSimpleElement(w, "para", pg.getQualityControlDescription() != null ? pg.getQualityControlDescription() : "");
                w.writeEndElement(); // description
                w.writeEndElement(); // qualityControl
            }
            w.writeEndElement(); // methods

            w.writeEndElement(); // dataset

            // additionalMetadata
            w.writeStartElement("additionalMetadata");
            w.writeStartElement("metadata");
            w.writeStartElement("gbif");

            commonElements2(w, pg);
            writeSimpleElement(w, "citation", pg.getCitation() != null ? pg.getCitation() : "");
            writeSimpleElement(w, "rights", pg.getRights() != null ? pg.getRights() : "");

            w.writeEndElement(); // gbif
            w.writeEndElement(); // metadata
            w.writeEndElement(); // additionalMetadata

            w.writeEndElement(); // eml:eml
            w.writeEndDocument();
            w.flush();

            return prettyPrint(sw.toString());
        } catch (XMLStreamException e) {
            log.error("Error generating EML for resource {}", pg.getUid(), e);
            throw new RuntimeException("Failed to generate EML", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // emlForOtherEntity
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Generates EML representation for Institution, DataProvider, or DataHub.
     */
    public String emlForOtherEntity(ProviderGroup pg) {
        try {
            StringWriter sw = new StringWriter();
            XMLStreamWriter w = createWriter(sw);

            w.writeStartDocument("UTF-8", "1.0");

            Identifiers ids = identifiers(pg);
            writeEmlRoot(w, ids);

            // <dataset>
            w.writeStartElement("dataset");

            writeAlternateIdentifiers(w, pg);
            commonElements1(w, pg);
            writeDistribution(w, pg);
            writeContacts(w, pg);

            w.writeEndElement(); // dataset

            // additionalMetadata
            w.writeStartElement("additionalMetadata");
            w.writeStartElement("metadata");
            w.writeStartElement("gbif");
            commonElements2(w, pg);
            w.writeEndElement(); // gbif
            w.writeEndElement(); // metadata
            w.writeEndElement(); // additionalMetadata

            w.writeEndElement(); // eml:eml
            w.writeEndDocument();
            w.flush();

            return prettyPrint(sw.toString());
        } catch (XMLStreamException e) {
            log.error("Error generating EML for entity {}", pg.getUid(), e);
            throw new RuntimeException("Failed to generate EML", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Common element writers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Writes common elements block 1: title, creator, metadataProvider, associatedParty,
     * pubDate, language, abstract.
     */
    private void commonElements1(XMLStreamWriter w, ProviderGroup pg) throws XMLStreamException {
        // title
        w.writeStartElement("title");
        w.writeAttribute("xmlns:lang", "en");
        w.writeCharacters(pg.getName() != null ? pg.getName() : "");
        w.writeEndElement(); // title

        // creator — the entity that created this
        ProviderGroup creator = resolveCreator(pg);
        writeOrganisation(w, "creator", creator, null);

        // metadataProvider — same as creator
        writeOrganisation(w, "metadataProvider", creator, null);

        // associatedParty — ALA
        writeAlaAssociatedParty(w, true);

        // associated parties from relationships
        if (pg instanceof DataResource dr) {
            Set<ProviderGroup> consumers = new LinkedHashSet<>();
            if (dr.getConsumerInstitutions() != null) consumers.addAll(dr.getConsumerInstitutions());
            if (dr.getConsumerCollections() != null) consumers.addAll(dr.getConsumerCollections());
            for (ProviderGroup con : consumers) {
                writeOrganisation(w, "associatedParty", con, "originator");
            }
        } else if (pg instanceof DataProvider dp) {
            Set<ProviderGroup> consumers = new LinkedHashSet<>();
            if (dp.getConsumerInstitutions() != null) consumers.addAll(dp.getConsumerInstitutions());
            if (dp.getConsumerCollections() != null) consumers.addAll(dp.getConsumerCollections());
            for (ProviderGroup con : consumers) {
                writeOrganisation(w, "associatedParty", con, "originator");
            }
        } else if (pg instanceof Institution inst) {
            // providerDataResources and providerDataProviders as 'publisher'
            List<DataResource> provResources = dataResourceRepository.findAllByConsumerInstitutionsContaining(inst);
            List<DataProvider> provProviders = dataProviderRepository.findAllByConsumerInstitutionsContaining(inst);
            for (DataResource pro : provResources) {
                writeOrganisation(w, "associatedParty", pro, "publisher");
            }
            for (DataProvider pro : provProviders) {
                writeOrganisation(w, "associatedParty", pro, "publisher");
            }
        } else if (pg instanceof Collection col) {
            // providerDataResources and providerDataProviders as 'publisher'
            List<DataResource> provResources = dataResourceRepository.findAllByConsumerCollectionsContaining(col);
            List<DataProvider> provProviders = dataProviderRepository.findAllByConsumerCollectionsContaining(col);
            for (DataResource pro : provResources) {
                writeOrganisation(w, "associatedParty", pro, "publisher");
            }
            for (DataProvider pro : provProviders) {
                writeOrganisation(w, "associatedParty", pro, "publisher");
            }
        }

        // pubDate
        String lastPub = null;
        if (pg.getLastUpdated() != null) {
            lastPub = pg.getLastUpdated().format(DATE_FORMAT);
        }
        writeSimpleElement(w, "pubDate", lastPub != null ? lastPub : "");

        // language
        writeSimpleElement(w, "language", "English");

        // abstract
        w.writeStartElement("abstract");
        String abstractText = stripFormatting(Arrays.asList(pg.getPubDescription(), pg.getTechDescription()));
        writeSimpleElement(w, "para", abstractText);
        w.writeEndElement(); // abstract
    }

    /**
     * Writes common elements block 2: dateStamp, hierarchyLevel, resourceLogoUrl.
     */
    private void commonElements2(XMLStreamWriter w, ProviderGroup pg) throws XMLStreamException {
        // dateStamp
        if (pg.getLastUpdated() != null) {
            writeSimpleElement(w, "dateStamp", pg.getLastUpdated().format(DATE_TIME_FORMAT));
        }

        // hierarchyLevel
        writeSimpleElement(w, "hierarchyLevel", "dataset");

        // resourceLogoUrl
        String logo = pg.buildLogoUrl(appProperties.getServerUrl());
        if (logo != null) {
            writeSimpleElement(w, "resourceLogoUrl", logo);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Organisation / Contact writers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Writes an organisation element (creator, metadataProvider, associatedParty, etc.).
     */
    private void writeOrganisation(XMLStreamWriter w, String tag, ProviderGroup pg, String role)
            throws XMLStreamException {
        w.writeStartElement(tag);

        writeSimpleElement(w, "organizationName", pg.getName() != null ? pg.getName() : "");

        Address address = providerGroupService.resolveAddress(pg);
        if (address != null && !isAddressEmpty(address)) {
            w.writeStartElement("address");
            writeOptionalElement(w, "deliveryPoint", address.getStreet());
            writeOptionalElement(w, "city", address.getCity());
            writeOptionalElement(w, "administrativeArea", address.getState());
            writeOptionalElement(w, "postalCode", address.getPostcode());
            writeOptionalElement(w, "country", address.getCountry());
            w.writeEndElement(); // address
        }

        writeOptionalElement(w, "phone", pg.getPhone());
        writeOptionalElement(w, "electronicMailAddress", pg.getEmail());
        writeOptionalElement(w, "onlineUrl", pg.getWebsiteUrl());

        if (role != null) {
            writeSimpleElement(w, "role", role);
        }

        w.writeEndElement(); // tag
    }

    /**
     * Writes the ALA as an associated party element.
     */
    private void writeAlaAssociatedParty(XMLStreamWriter w, boolean withRole) throws XMLStreamException {
        AppProperties.Eml eml = appProperties.getEml();

        w.writeStartElement("associatedParty");
        writeSimpleElement(w, "organizationName", eml.getOrganizationName());

        w.writeStartElement("address");
        writeOptionalElement(w, "deliveryPoint", eml.getDeliveryPoint());
        writeOptionalElement(w, "city", eml.getCity());
        writeOptionalElement(w, "administrativeArea", eml.getAdministrativeArea());
        writeOptionalElement(w, "postalCode", eml.getPostalCode());
        writeOptionalElement(w, "country", eml.getCountry());
        w.writeEndElement(); // address

        writeOptionalElement(w, "electronicMailAddress", eml.getElectronicMailAddress());

        if (withRole) {
            writeSimpleElement(w, "role", "distributor");
        }

        w.writeEndElement(); // associatedParty
    }

    /**
     * Writes the primary contact element.
     */
    private void writeContacts(XMLStreamWriter w, ProviderGroup pg) throws XMLStreamException {
        ContactFor cnt = inheritPrimaryContact(pg);
        if (cnt != null && cnt.getContact() != null) {
            w.writeStartElement("contact");
            Contact contact = cnt.getContact();

            if (hasContent(contact.getFirstName()) || hasContent(contact.getLastName())) {
                w.writeStartElement("individualName");
                if (hasContent(contact.getFirstName()) && hasContent(contact.getLastName())) {
                    writeSimpleElement(w, "givenName", contact.getFirstName() != null ? contact.getFirstName() : "");
                    writeSimpleElement(w, "surName", contact.getLastName() != null ? contact.getLastName() : "");
                } else {
                    writeSimpleElement(w, "surName",
                            hasContent(contact.getFirstName()) ? contact.getFirstName() : " ");
                }
                w.writeEndElement(); // individualName
            }

            if (hasContent(cnt.getRole())) {
                writeSimpleElement(w, "positionName", cnt.getRole());
            }
            if (hasContent(contact.getPhone())) {
                writeSimpleElement(w, "phone", contact.getPhone());
            }
            if (hasContent(contact.getEmail())) {
                writeSimpleElement(w, "electronicMailAddress", contact.getEmail());
            }

            w.writeEndElement(); // contact
        } else {
            // Last resort: use ALA as contact
            AppProperties.Eml eml = appProperties.getEml();
            w.writeStartElement("contact");
            writeSimpleElement(w, "organizationName", eml.getOrganizationName());
            w.writeStartElement("address");
            writeOptionalElement(w, "deliveryPoint", eml.getDeliveryPoint());
            writeOptionalElement(w, "city", eml.getCity());
            writeOptionalElement(w, "administrativeArea", eml.getAdministrativeArea());
            writeOptionalElement(w, "postalCode", eml.getPostalCode());
            writeOptionalElement(w, "country", eml.getCountry());
            w.writeEndElement(); // address
            w.writeEndElement(); // contact
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Coverage writers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Writes geographic coverage for a Collection.
     */
    private void writeCollectionGeographicCoverage(XMLStreamWriter w, Collection pg) throws XMLStreamException {
        boolean hasBoundingBox =
                pg.getEastCoordinate() != null && pg.getEastCoordinate().compareTo(NO_INFO_AVAILABLE) != 0 &&
                pg.getWestCoordinate() != null && pg.getWestCoordinate().compareTo(NO_INFO_AVAILABLE) != 0 &&
                pg.getNorthCoordinate() != null && pg.getNorthCoordinate().compareTo(NO_INFO_AVAILABLE) != 0 &&
                pg.getSouthCoordinate() != null && pg.getSouthCoordinate().compareTo(NO_INFO_AVAILABLE) != 0;

        if (hasContent(pg.getGeographicDescription()) || hasBoundingBox) {
            w.writeStartElement("geographicCoverage");

            if (hasContent(pg.getGeographicDescription())) {
                writeSimpleElement(w, "geographicDescription", pg.getGeographicDescription());
            }

            if (hasBoundingBox) {
                w.writeStartElement("boundingCoordinates");
                writeSimpleElement(w, "westBoundingCoordinate", pg.getWestCoordinate().toPlainString());
                writeSimpleElement(w, "eastBoundingCoordinate", pg.getEastCoordinate().toPlainString());
                writeSimpleElement(w, "northBoundingCoordinate", pg.getNorthCoordinate().toPlainString());
                writeSimpleElement(w, "southBoundingCoordinate", pg.getSouthCoordinate().toPlainString());
                w.writeEndElement(); // boundingCoordinates
            }

            w.writeEndElement(); // geographicCoverage
        }
    }

    /**
     * Writes taxonomic coverage for a Collection using kingdomCoverage and taxonomyHints.
     */
    private void writeCollectionTaxonomicCoverage(XMLStreamWriter w, Collection pg) throws XMLStreamException {
        w.writeStartElement("taxonomicCoverage");

        if (hasContent(pg.getFocus())) {
            writeSimpleElement(w, "generalTaxonomicCoverage", pg.getFocus());
        }

        List<Map<String, String>> ranks = new ArrayList<>();

        // Add kingdoms
        if (hasContent(pg.getKingdomCoverage())) {
            for (String kingdom : listKingdoms(pg)) {
                ranks.add(Map.of("rank", "kingdom", "name", kingdom));
            }
        }

        // Add taxonomy hints (avoiding duplicates)
        if (hasContent(pg.getTaxonomyHints())) {
            for (Map<String, String> taxon : listTaxonomyHints(pg)) {
                String tRank = taxon.getOrDefault("rank", "").toLowerCase();
                String tName = taxon.getOrDefault("name", "").toLowerCase();
                boolean exists = ranks.stream().anyMatch(r ->
                        r.getOrDefault("rank", "").equalsIgnoreCase(tRank) &&
                        r.getOrDefault("name", "").equalsIgnoreCase(tName));
                if (!exists) {
                    ranks.add(taxon);
                }
            }
        }

        for (Map<String, String> rank : ranks) {
            w.writeStartElement("taxonomicClassification");
            writeSimpleElement(w, "taxonRankName", rank.getOrDefault("rank", "").toLowerCase());
            writeSimpleElement(w, "taxonRankValue", rank.getOrDefault("name", "").toLowerCase());
            w.writeEndElement(); // taxonomicClassification
        }

        w.writeEndElement(); // taxonomicCoverage
    }

    /**
     * Writes geographic coverage for a DataResource.
     */
    private void writeResourceGeographicCoverage(XMLStreamWriter w, DataResource pg) throws XMLStreamException {
        if (hasContent(pg.getGeographicDescription()) && hasContent(pg.getWestBoundingCoordinate())) {
            w.writeStartElement("geographicCoverage");
            writeSimpleElement(w, "geographicDescription", pg.getGeographicDescription());
            if (hasContent(pg.getWestBoundingCoordinate())) {
                w.writeStartElement("boundingCoordinates");
                writeSimpleElement(w, "westBoundingCoordinate", pg.getWestBoundingCoordinate());
                writeSimpleElement(w, "eastBoundingCoordinate", pg.getEastBoundingCoordinate());
                writeSimpleElement(w, "northBoundingCoordinate", pg.getNorthBoundingCoordinate());
                writeSimpleElement(w, "southBoundingCoordinate", pg.getSouthBoundingCoordinate());
                w.writeEndElement(); // boundingCoordinates
            }
            w.writeEndElement(); // geographicCoverage
        }
    }

    /**
     * Writes temporal coverage for a DataResource.
     */
    private void writeResourceTemporalCoverage(XMLStreamWriter w, DataResource pg) throws XMLStreamException {
        if (hasContent(pg.getBeginDate()) && hasContent(pg.getEndDate())) {
            w.writeStartElement("temporalCoverage");
            w.writeStartElement("rangeOfDates");

            w.writeStartElement("beginDate");
            writeSimpleElement(w, "calendarDate", pg.getBeginDate());
            w.writeEndElement(); // beginDate

            w.writeStartElement("endDate");
            writeSimpleElement(w, "calendarDate", pg.getEndDate());
            w.writeEndElement(); // endDate

            w.writeEndElement(); // rangeOfDates
            w.writeEndElement(); // temporalCoverage
        }
    }

    /**
     * Writes taxonomic coverage from a taxonomy hints JSON string (used by DataResource).
     */
    private void writeTaxonomicCoverage(XMLStreamWriter w, String hints) throws XMLStreamException {
        try {
            Map<String, Object> json = objectMapper.readValue(hints, new TypeReference<>() {});

            w.writeStartElement("taxonomicCoverage");

            // generalTaxonomicCoverage from "range"
            @SuppressWarnings("unchecked")
            List<String> rangeList = (List<String>) json.get("range");
            if (rangeList != null && !rangeList.isEmpty()) {
                String rangeText = rangeList.stream()
                        .map(r -> r.replace("\"", "").trim())
                        .collect(Collectors.joining("; "));
                writeSimpleElement(w, "generalTaxonomicCoverage", rangeText);
            }

            // taxonomicClassification from "coverage"
            @SuppressWarnings("unchecked")
            List<Map<String, String>> coverageList = (List<Map<String, String>>) json.get("coverage");
            if (coverageList != null) {
                for (Map<String, String> entry : coverageList) {
                    for (Map.Entry<String, String> kv : entry.entrySet()) {
                        w.writeStartElement("taxonomicClassification");
                        writeSimpleElement(w, "taxonRankName", kv.getKey());
                        writeSimpleElement(w, "taxonRankValue", kv.getValue());
                        w.writeEndElement(); // taxonomicClassification
                    }
                }
            }

            w.writeEndElement(); // taxonomicCoverage
        } catch (Exception e) {
            log.warn("Failed to parse taxonomy hints JSON: {}", e.getMessage());
            // Write an empty taxonomicCoverage element
            w.writeStartElement("taxonomicCoverage");
            w.writeEndElement();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Intellectual Rights (DataResource)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Writes intellectual rights for a DataResource, including licence info.
     */
    private void writeIntellectualRights(XMLStreamWriter w, DataResource pg) throws XMLStreamException {
        List<Licence> licences = findLicences(pg);

        w.writeStartElement("intellectualRights");

        if (hasContent(pg.getRights()) || hasContent(pg.getCitation()) || !licences.isEmpty()) {
            w.writeStartElement("para");

            if (hasContent(pg.getRights())) {
                w.writeCharacters(pg.getRights());
            }
            if (hasContent(pg.getRights()) && hasContent(pg.getCitation())) {
                w.writeCharacters(" ");
            }
            if (hasContent(pg.getCitation())) {
                w.writeCharacters(pg.getCitation());
            }

            for (Licence lic : licences) {
                w.writeCharacters(" ");
                w.writeStartElement("ulink");
                w.writeAttribute("url", lic.getUrl());
                w.writeStartElement("citetitle");

                w.writeCharacters(lic.getName());
                if (hasContent(lic.getAcronym())) {
                    w.writeCharacters(" (");
                    w.writeCharacters(lic.getAcronym());
                    if (hasContent(lic.getLicenceVersion())) {
                        w.writeCharacters(" ");
                        w.writeCharacters(lic.getLicenceVersion());
                    }
                    w.writeCharacters(")");
                }

                w.writeEndElement(); // citetitle
                w.writeEndElement(); // ulink
            }

            w.writeEndElement(); // para
        }

        w.writeEndElement(); // intellectualRights
    }

    // ──────────────────────────────────────────────────────────────────────
    // Identifiers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Extracts identifiers for the entity. Uses LSID as primary if available.
     */
    private Identifiers identifiers(ProviderGroup pg) {
        String serverUrl = appProperties.getServerUrl();
        String id;
        String altId = "";

        if (pg.getGuid() != null && pg.getGuid().startsWith("urn:lsid")) {
            id = pg.getGuid();
            altId = serverUrl + "/public/show/" + pg.getUid();
        } else {
            id = serverUrl + "/public/show/" + pg.getUid();
        }

        String uuid = UUID.nameUUIDFromBytes(id.getBytes()).toString();
        String packageId = uuid + "/v" + pg.getVersion();

        return new Identifiers(id, packageId, altId, uuid);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Text processing
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Strips formatting from a list of text items, joining with newlines.
     */
    String stripFormatting(List<String> items) {
        return items.stream()
                .map(item -> item != null ? removeMarkup(handleLinks(item)) : "")
                .collect(Collectors.joining("\n"))
                .trim();
    }

    /**
     * Removes wiki-style markup: _italic_ and +bold+.
     */
    String removeMarkup(String str) {
        if (str == null) return null;
        // Remove italic markup: _text_
        str = str.replaceAll("_([^\\r\\n_]*)_", "$1");
        // Remove bold markup: +text+
        str = str.replaceAll("\\+([^\\r\\n+]*)\\+", "$1");
        return str;
    }

    /**
     * Transforms wiki-style link markup [url name] to name (url).
     */
    String handleLinks(String str) {
        if (str == null) return null;
        Pattern urlMatch = Pattern.compile("\\[(https?:\\S*)\\b ([^\\]]*)]");
        Matcher m = urlMatch.matcher(str);
        return m.replaceAll("$1 ($2)");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Curatorial unit
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Determines the curatorial unit type for a Collection based on its collectionType.
     */
    String getCuratorialUnit(Collection pg) {
        String types = pg.getCollectionType();
        if (types == null) return "specimens";

        if (types.contains("preserved")) {
            return "specimens";
        } else if (types.contains("cellcultures")) {
            return "cultures";
        } else if (types.contains("genetic")) {
            return "samples";
        } else {
            return "specimens"; // default
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Domain method delegates
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Resolves the creator (the entity that created this one).
     * For DataResource: its dataProvider or institution.
     * For Collection: its institution.
     * For others: itself.
     */
    private ProviderGroup resolveCreator(ProviderGroup pg) {
        if (pg instanceof DataResource dr) {
            if (dr.getDataProvider() != null) return dr.getDataProvider();
            if (dr.getInstitution() != null) return dr.getInstitution();
        } else if (pg instanceof Collection co) {
            if (co.getInstitution() != null) return co.getInstitution();
        }
        // Fall back to itself
        return pg;
    }

    /**
     * Inherits the primary contact for an entity by looking up ContactFor records.
     */
    private ContactFor inheritPrimaryContact(ProviderGroup pg) {
        List<ContactFor> contacts = contactForRepository.findAllByEntityUid(pg.getUid());

        // Find primary contact
        Optional<ContactFor> primary = contacts.stream()
                .filter(ContactFor::isPrimaryContact)
                .findFirst();
        if (primary.isPresent()) return primary.get();

        // If no primary, try the creator entity
        ProviderGroup creator = resolveCreator(pg);
        if (creator != null && !creator.getUid().equals(pg.getUid())) {
            List<ContactFor> creatorContacts = contactForRepository.findAllByEntityUid(creator.getUid());
            Optional<ContactFor> creatorPrimary = creatorContacts.stream()
                    .filter(ContactFor::isPrimaryContact)
                    .findFirst();
            if (creatorPrimary.isPresent()) return creatorPrimary.get();
        }

        // Return first available contact
        return contacts.isEmpty() ? null : contacts.get(0);
    }

    /**
     * Lists keywords from the entity's keywords JSON string.
     */
    private List<String> listKeywords(ProviderGroup pg) {
        if (!hasContent(pg.getKeywords())) return List.of();
        try {
            return objectMapper.readValue(pg.getKeywords(), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse keywords JSON for {}: {}", pg.getUid(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Lists kingdoms from the collection's kingdomCoverage JSON string.
     */
    private List<String> listKingdoms(Collection pg) {
        if (!hasContent(pg.getKingdomCoverage())) return List.of();
        try {
            return objectMapper.readValue(pg.getKingdomCoverage(), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse kingdoms JSON for {}: {}", pg.getUid(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Lists taxonomy hints from the entity's taxonomyHints JSON string.
     * Returns a list of maps with "rank" and "name" keys.
     */
    private List<Map<String, String>> listTaxonomyHints(ProviderGroup pg) {
        if (!hasContent(pg.getTaxonomyHints())) return List.of();
        try {
            // taxonomyHints JSON can be either a map with "coverage" key containing list of {rank:name}
            // or a direct list of {rank, name} maps
            Map<String, Object> json = objectMapper.readValue(pg.getTaxonomyHints(), new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, String>> coverage = (List<Map<String, String>>) json.get("coverage");
            if (coverage != null) {
                // Convert from {rank: name} entries to {rank: rank, name: name} maps
                List<Map<String, String>> result = new ArrayList<>();
                for (Map<String, String> entry : coverage) {
                    for (Map.Entry<String, String> kv : entry.entrySet()) {
                        result.add(Map.of("rank", kv.getKey(), "name", kv.getValue()));
                    }
                }
                return result;
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to parse taxonomy hints JSON for {}: {}", pg.getUid(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Gets external identifiers for an entity.
     */
    private Set<ExternalIdentifier> getExternalIdentifiers(ProviderGroup pg) {
        if (pg instanceof DataResource dr) {
            return dr.getExternalIdentifiers() != null ? dr.getExternalIdentifiers() : Set.of();
        } else if (pg instanceof Collection co) {
            return co.getExternalIdentifiers() != null ? co.getExternalIdentifiers() : Set.of();
        } else if (pg instanceof Institution inst) {
            return inst.getExternalIdentifiers() != null ? inst.getExternalIdentifiers() : Set.of();
        } else if (pg instanceof DataProvider dp) {
            return dp.getExternalIdentifiers() != null ? dp.getExternalIdentifiers() : Set.of();
        } else if (pg instanceof DataHub dh) {
            return dh.getExternalIdentifiers() != null ? dh.getExternalIdentifiers() : Set.of();
        }
        // Fallback: look up from repository
        List<ExternalIdentifier> extIds = externalIdentifierRepository.findAllByEntityUid(pg.getUid());
        return new LinkedHashSet<>(extIds);
    }

    /**
     * Finds licences matching the DataResource's licenseType and optionally licenseVersion.
     */
    private List<Licence> findLicences(DataResource pg) {
        if (!hasContent(pg.getLicenseType())) return List.of();

        Optional<Licence> licence = licenceRepository.findByAcronym(pg.getLicenseType());
        if (licence.isEmpty()) return List.of();

        Licence lic = licence.get();
        // If a version is specified, only return if it matches
        if (hasContent(pg.getLicenseVersion()) &&
                !pg.getLicenseVersion().equals(lic.getLicenceVersion())) {
            return List.of();
        }
        return List.of(lic);
    }

    // ──────────────────────────────────────────────────────────────────────
    // XML writing helpers
    // ──────────────────────────────────────────────────────────────────────

    private XMLStreamWriter createWriter(StringWriter sw) throws XMLStreamException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        return factory.createXMLStreamWriter(sw);
    }

    /**
     * Writes the root eml:eml element with all namespace attributes.
     */
    private void writeEmlRoot(XMLStreamWriter w, Identifiers ids) throws XMLStreamException {
        w.writeStartElement("eml", "eml", NS_EML);
        w.writeNamespace("eml", NS_EML);
        w.writeNamespace("d", NS_D);
        w.writeNamespace("xsi", NS_XSI);
        w.writeNamespace("dc", NS_DC);
        w.writeAttribute(NS_XSI, "schemaLocation", SCHEMA_LOCATION);
        w.writeAttribute("system", "ALA-Registry");
        w.writeAttribute("scope", "system");
        w.writeAttribute("xml:lang", "en");
        w.writeAttribute("packageId", ids.packageId);
    }

    /**
     * Writes alternateIdentifier elements from external identifiers.
     */
    private void writeAlternateIdentifiers(XMLStreamWriter w, ProviderGroup pg) throws XMLStreamException {
        Set<ExternalIdentifier> extIds = getExternalIdentifiers(pg);
        for (ExternalIdentifier ext : extIds) {
            writeSimpleElement(w, "alternateIdentifier", ext.getIdentifier());
        }
    }

    /**
     * Writes the distribution element.
     */
    private void writeDistribution(XMLStreamWriter w, ProviderGroup pg) throws XMLStreamException {
        w.writeStartElement("distribution");
        w.writeStartElement("online");
        w.writeStartElement("url");
        w.writeAttribute("function", "information");
        w.writeCharacters(appProperties.getServerUrl() + "/public/show/" + pg.getUid());
        w.writeEndElement(); // url
        w.writeEndElement(); // online
        w.writeEndElement(); // distribution
    }

    /**
     * Writes a simple text element. Always writes the element, even if value is empty.
     */
    private void writeSimpleElement(XMLStreamWriter w, String name, String value) throws XMLStreamException {
        w.writeStartElement(name);
        w.writeCharacters(value != null ? value : "");
        w.writeEndElement();
    }

    /**
     * Writes a text element only if value is non-null and non-empty.
     */
    private void writeOptionalElement(XMLStreamWriter w, String name, String value) throws XMLStreamException {
        if (hasContent(value)) {
            writeSimpleElement(w, name, value);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Pretty-printing
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Pretty-prints the XML string using a Transformer with indentation.
     */
    private String prettyPrint(String xml) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            StreamSource source = new StreamSource(new StringReader(xml));
            StringWriter sw = new StringWriter();
            StreamResult result = new StreamResult(sw);
            transformer.transform(source, result);
            return sw.toString();
        } catch (Exception e) {
            log.warn("Failed to pretty-print EML XML, returning raw", e);
            return xml;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility methods
    // ──────────────────────────────────────────────────────────────────────

    private static boolean hasContent(String s) {
        return s != null && !s.isEmpty();
    }

    private static boolean isAddressEmpty(Address address) {
        return !hasContent(address.getStreet()) &&
               !hasContent(address.getCity()) &&
               !hasContent(address.getState()) &&
               !hasContent(address.getPostcode()) &&
               !hasContent(address.getCountry());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Inner value class for identifiers
    // ──────────────────────────────────────────────────────────────────────

    private record Identifiers(String id, String packageId, String altId, String uuid) {}
}
