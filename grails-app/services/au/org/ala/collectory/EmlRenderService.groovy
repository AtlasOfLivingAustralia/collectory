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
package au.org.ala.collectory

import groovy.json.JsonSlurper
import groovy.json.JsonException
import groovy.json.JsonParserType
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import java.text.SimpleDateFormat
import java.text.DateFormat

class EmlRenderService {

    static transactional = true
    def providerGroupService
    def grailsApplication
    def ns = [eml:"eml://ecoinformatics.org/eml-2.1.1",
            xsi:"http://www.w3.org/2001/XMLSchema-instance",
            dc:"http://purl.org/dc/terms/"]

    def emlNs = ['xmlns:d':"eml://ecoinformatics.org/dataset-2.1.0",
            'xsi:schemaLocation':"eml://ecoinformatics.org/eml-2.1.1 http://rs.gbif.org/schema/eml-gbif-profile/1.1/eml-gbif-profile.xsd",
            'system':"ALA-Registry",
            'scope':"system",
            'xml:lang':"en"]

    def namespaces = [
            'xmlns:d':"eml://ecoinformatics.org/dataset-2.1.0",
            'xmlns:eml':"eml://ecoinformatics.org/eml-2.1.1",
            'xmlns:xsi':"http://www.w3.org/2001/XMLSchema-instance",
            'xmlns:dc':"http://purl.org/dc/terms/",
            'xsi:schemaLocation':"eml://ecoinformatics.org/eml-2.1.1 http://rs.gbif.org/schema/eml-gbif-profile/1.1/eml-gbif-profile.xsd",
            'system':"ALA-Registry",
            'scope':"system",
            'xml:lang':"en"
    ]

    final static String DATE_PATTERN = "yyyy-MM-dd";
    final static String DATE_TIME_PATTERN = "yyyy-MM-dd'T'hh:mm:ss";

    /**
      * DateFormat to be used to format dates
      */
    final static DateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN)
    static {
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    final static DateFormat dateTimeFormat = new SimpleDateFormat(DATE_TIME_PATTERN)
    static {
        dateTimeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * General entry point for any entity.
     *
     * @param entity
     * @return eml for the entity
     */
    String emlForEntity(entity) {
        if (entity instanceof DataResource) {
            return emlForResource(entity)
        }
        else if (entity instanceof Collection) {
            return emlForCollection(entity)
        }
        else {
            return emlForOtherEntity(entity)
        }
    }

    /**
     * Binds the elements that are common to all entities
     *
     * <title/>
     * <creator/>
     * <metadataProvider/>
     * <associatedParty/>  (ALA)
     * <pubDate/>
     * <language/>
     * <abstract/>
     *
     * @param builder
     * @param pg the entity
     */
    def commonElements1(builder, ProviderGroup pg) {

        /* title */
        builder.title('xmlns:lang':'en', pg.name)

        /* creator */
        def crt = pg.createdBy()
        organisation(builder, 'creator', crt, null, pg)

        /* metadata provider */
        // always the same as creator
        organisation(builder, 'metadataProvider', crt, null, pg)

        /* associated parties */
        builder.associatedParty(ala(true))
        if (pg instanceof DataResource || pg instanceof DataProvider) {
            (pg.consumerInstitutions + pg.consumerCollections).each { con ->
                organisation(builder, 'associatedParty', con, 'originator')
            }
        }
        if (pg instanceof Institution || pg instanceof Collection) {
            (pg.providerDataResources + pg.providerDataProviders).each { pro ->
                organisation(builder, 'associatedParty', pro, 'publisher')
            }
        }

        /* pub date */
        def lastPub = pg.lastUpdated
        if (lastPub) {
          lastPub = lastPub.toString()[0..9]
        }
        builder.pubDate lastPub

        /* language */
        builder.language "English"

        /* abstract */
        builder.'abstract'() {
            builder.para stripFormatting([pg.pubDescription, pg.techDescription])
        }
    }

    /**
     * Binds the additional metadata elements that are common to all entities
     *
     * <dateStamp/>
     * <metadataLanguage/>
     * <hierarchyLevel/>
     * <resourceLogoUrl/>
     *
     * @param builder
     * @param pg the entity
     */
    def commonElements2(builder, ProviderGroup pg) {

        /* dateStamp */
        builder.dateStamp dateTimeFormat.format(pg.lastUpdated)

        /* hierarchyLevel */
        builder.hierarchyLevel 'dataset'

        /* resourceLogoUrl */
        def logo = pg.buildLogoUrl()
        if (logo) {
            builder.resourceLogoUrl logo
        }
    }

    def organisation(builder, tag, ProviderGroup pg, role, ProviderGroup contactSourcePg = null) {
        builder."${tag}"() {
            def source = contactSourcePg ?: pg
            def primaryContact = source.inheritPrimaryContact()
            if (primaryContact?.contact?.firstName?.trim() || primaryContact?.contact?.lastName?.trim()) {
                builder.individualName {
                    if(primaryContact.contact.firstName?.trim() && primaryContact.contact.lastName?.trim()){
                        builder.givenName(primaryContact.contact.firstName.trim())
                        builder.surName(primaryContact.contact.lastName.trim())
                    } else if (primaryContact.contact.lastName?.trim()) {
                        builder.surName(primaryContact.contact.lastName.trim())
                    } else {
                        builder.surName(primaryContact.contact.firstName.trim())
                    }
                }
            }
            if (primaryContact?.contact?.organizationName?.trim()) {
                builder.organizationName(primaryContact.contact.organizationName.trim())
            } else if (pg.name?.trim()) { 
                builder.organizationName(pg.name.trim()) 
            }
            
            if (primaryContact?.contact?.positionName?.trim()) {
                builder.positionName(primaryContact.contact.positionName.trim())
            } else if (primaryContact?.role?.trim()) { 
                builder.positionName(primaryContact.role.trim()) 
            }

            def address = providerGroupService.resolveAddress(pg) ?: providerGroupService.resolveAddress(source)
            if (address && !address.isEmpty()) {
                builder.address {
                    if (address.street?.trim()) { deliveryPoint address.street.trim() }
                    if (address.city?.trim()) { city address.city.trim() }
                    if (address.state?.trim()) { administrativeArea address.state.trim() }
                    if (address.postcode?.trim()) { postalCode address.postcode.trim() }
                    if (address.country?.trim()) { country address.country.trim() }
                }

            }
            if (primaryContact?.contact?.phone?.trim()) {
                builder.phone(primaryContact.contact.phone.trim())
            } else if (pg.phone?.trim()) { 
                builder.phone(pg.phone.trim()) 
            }
            
            if (primaryContact?.contact?.email?.trim()) {
                builder.electronicMailAddress(primaryContact.contact.email.trim())
            } else if (pg.email?.trim()) { 
                builder.electronicMailAddress(pg.email.trim()) 
            }

            if (primaryContact?.contact?.userId?.trim()) {
                def uId = primaryContact.contact.userId.trim()
                if (uId.startsWith("http")) {
                    // Extract directory from URL if it exists, otherwise use a default
                    int lastSlash = uId.lastIndexOf('/')
                    if (lastSlash > 0) {
                        builder.userId(directory: uId.substring(0, lastSlash + 1), uId.substring(lastSlash + 1))
                    } else {
                        builder.userId(uId)
                    }
                } else {
                    builder.userId(directory: "https://orcid.org/", uId)
                }
            }

            if (pg.websiteUrl?.trim()) { builder.onlineUrl(pg.websiteUrl.trim()) }
            
            if (role?.trim()) {
                builder.role role.trim()
            }
        }
    }

    /**
     * Binds the primary contact.
     *
     * @param builder
     * @param pg the entity
     */
    def contacts(builder, pg) {
        def cnt = pg.inheritPrimaryContact()
        if (cnt) {
            builder.contact {
                if (cnt.contact.firstName?.trim() || cnt.contact.lastName?.trim()) {
                    builder.individualName {
                        if(cnt.contact.firstName?.trim() && cnt.contact.lastName?.trim()){
                            builder.givenName(cnt.contact.firstName.trim())
                            builder.surName(cnt.contact.lastName.trim())
                        } else if (cnt.contact.lastName?.trim()) {
                            builder.surName(cnt.contact.lastName.trim())
                        } else {
                            builder.surName(cnt.contact.firstName.trim())
                        }
                    }
                }
                if (cnt.contact.organizationName?.trim()) { builder.organizationName(cnt.contact.organizationName.trim()) }
                if (cnt.contact.positionName?.trim()) { 
                    builder.positionName(cnt.contact.positionName.trim()) 
                } else if (cnt.role?.trim()) { 
                    builder.positionName(cnt.role.trim()) 
                }
                
                def address = providerGroupService.resolveAddress(pg)
                if (address && !address.isEmpty()) {
                    builder.address {
                        if (address.street?.trim()) { deliveryPoint address.street.trim() }
                        if (address.city?.trim()) { city address.city.trim() }
                        if (address.state?.trim()) { administrativeArea address.state.trim() }
                        if (address.postcode?.trim()) { postalCode address.postcode.trim() }
                        if (address.country?.trim()) { country address.country.trim() }
                    }
                }

                if (cnt.contact.phone?.trim()) { builder.phone(cnt.contact.phone.trim()) }
                if (cnt.contact.email?.trim()) { builder.electronicMailAddress(cnt.contact.email.trim()) }
                
                if (cnt.contact.userId?.trim()) {
                    def uId = cnt.contact.userId.trim()
                    if (uId.startsWith("http")) {
                        int lastSlash = uId.lastIndexOf('/')
                        if (lastSlash > 0) {
                            builder.userId(directory: uId.substring(0, lastSlash + 1), uId.substring(lastSlash + 1))
                        } else {
                            builder.userId(uId)
                        }
                    } else {
                        builder.userId(directory: "https://orcid.org/", uId)
                    }
                }
            }
        } else {
            // last resort
            builder.contact(ala(false))
        }

    }

    /**
     * Extracts identifiers. Uses LSID as primary if available. Builds packageId and namespace.
     *
     * @param pg
     * @return id, packageId, alt id, uuid, and eml namespace
     */
    def identifiers(pg) {
        def id = ""
        def altId = ""
        if (pg.guid?.startsWith('urn:lsid')) {
            id = pg.guid
            altId = grailsApplication.config.grails.serverURL + "/public/show/" + pg.uid
        } else {
            id = grailsApplication.config.grails.serverURL + "/public/show/" + pg.uid
        }
        def uuid = UUID.nameUUIDFromBytes(id as byte[]).toString()
        def packageId = uuid + "/v" + pg.version
        def nsToUse = [:]
        nsToUse << namespaces
        nsToUse << [packageId: packageId]
        return [id:id, packageId: packageId, altId:altId, uuid: uuid, ns: nsToUse]
    }

    /**
     * Generates EML representation of the collection.
     *
     * @param pg the collection
     */
    String emlForCollection(Collection pg) {
        def markupBuilder = new StreamingMarkupBuilder()
        markupBuilder.encoding = 'UTF-8'
        markupBuilder.useDoubleQuotes = true

        def eml = markupBuilder.bind { builder ->
            mkp.xmlDeclaration()
            namespaces << ns

            def ids = identifiers(pg)

            'eml:eml'(ids.ns) {
                dataset() {
                    /* External identifiers will be converted to alternative identifiers */
                    pg.externalIdentifiers.each { ext ->
                        alternateIdentifier {
                            // Typical EML pattern: a value + an optional type attribute
                            mkp.yield ext.identifier
                        }
                    }

                    /* title, creator, metadataProvider, associatedParty, pubDate, language, abstract */
                    commonElements1 builder, pg

                    /* keywords */
                    keywordSet() {
                        pg.listKeywords().each {
                            keyword it
                        }
                        keywordThesaurus 'free text'
                    }

                    /* distribution */
                    distribution {
                        online {
                          url('function':'information',"${grailsApplication.config.grails.serverURL}/public/show/" + pg.uid)
                        }
                    }

                    /* coverage */
                    coverage() {
                        /* geographic */
                        def hasBoundingBox = pg.eastCoordinate != ProviderGroup.NO_INFO_AVAILABLE &&
                            pg.westCoordinate != ProviderGroup.NO_INFO_AVAILABLE &&
                            pg.northCoordinate != ProviderGroup.NO_INFO_AVAILABLE &&
                            pg.southCoordinate != ProviderGroup.NO_INFO_AVAILABLE

                        if (pg.geographicDescription || hasBoundingBox) {
                            geographicCoverage() {
                                if (pg.geographicDescription) {
                                    geographicDescription pg.geographicDescription
                                }
                                // must have all bounds
                                if (hasBoundingBox) {
                                    boundingCoordinates() {
                                        westBoundingCoordinate pg.westCoordinate
                                        eastBoundingCoordinate pg.eastCoordinate
                                        northBoundingCoordinate pg.northCoordinate
                                        southBoundingCoordinate pg.southCoordinate
                                    }
                                }
                            }
                        }

                        /* temporal */
                        // no relevant data (start/end dates apply to the collection not the span of specimens

                        /* taxonomic */
                        // use taxonomic hints for now
                        taxonomicCoverage() {
                            if (pg.focus) {
                                generalTaxonomicCoverage pg.focus
                            }
                            def ranks = []
                            if (pg.kingdomCoverage) {
                                pg.listKingdoms().each { kingdom ->
                                    ranks << [rank: 'kingdom',
                                             name: kingdom]
                                }
                            }
                            if (pg.taxonomyHints) {
                                pg.listTaxonomyHints().each { taxon ->
                                    // hints may be at kingdom level and potentially duplicate the explicit kingdoms
                                    def exists = ranks.find { i ->
                                        i.rank.toLowerCase() == taxon.rank.toLowerCase() &&
                                        i.name.toLowerCase() == taxon.name.toLowerCase()
                                    }
                                    // if it's not already there - add it
                                    if (!exists) {
                                        ranks << taxon
                                    }
                                }
                            }
                            if (ranks) {
                                ranks.each { rank ->
                                    taxonomicClassification() {
                                        taxonRankName rank.rank.toLowerCase()
                                        taxonRankValue rank.name.toLowerCase()
                                    }
                                }
                            }
                        }
                    }

                    contacts builder, pg

                }

                additionalMetadata() {
                    metadata() {
                        gbif() {

                            /* dateStamp, metadataLanguage, hierarchyLevel, resourceLogoUrl */
                            commonElements2 builder, pg

                            /* collection */
                            collection() {

                                parentCollectionIdentifier pg.institution ? identifiers(pg).id : 'no parent'

                                if (ids.id.startsWith('urn:lsid')) {
                                    collectionIdentifier ids.id
                                }
                                else {
                                    collectionIdentifier pg.buildUri()
                                }

                                collectionName pg.name
                            }

                            if (pg.startDate) {
                                formationPeriod pg.startDate
                            }

                            if (pg.numRecords != -1) {
                                jgtiCuratorialUnit() {
                                    jgtiUnitType getCuratorialUnit(pg)
                                    jgtiUnits(uncertaintyMeasure:1, pg.numRecords)
                                }
                            }
                        }
                    }
                }

            }
        }

        //return eml.toString()  // for production usage
        return XmlUtil.serialize(eml) // pretty-printed for development
    }

    /**
     * Generates EML representation of an entity.
     *
     * @param pg the entity
     */
    String emlForOtherEntity(ProviderGroup pg) {

        def markupBuilder = new StreamingMarkupBuilder()
        markupBuilder.encoding = 'UTF-8'
        markupBuilder.useDoubleQuotes = true

        def eml = markupBuilder.bind { builder ->
            mkp.xmlDeclaration()
            namespaces << ns

            def ids = identifiers(pg)

            'eml:eml'(ids.ns) {
                dataset() {

                    /* External identifiers will be converted to alternative identifiers */
                    pg.externalIdentifiers.each { ext ->
                        alternateIdentifier {
                            // Typical EML pattern: a value + an optional type attribute
                            mkp.yield ext.identifier
                        }
                    }

                    /* title, creator, metadataProvider, associatedParty, pubDate, language, abstract */
                    commonElements1 builder, pg

                    /* distribution */
                    distribution {
                        online {
                          url('function':'information',"${grailsApplication.config.grails.serverURL}/public/show/" + pg.uid)
                        }
                    }

                    contacts builder, pg

                }

                additionalMetadata() {
                    metadata() {
                        gbif() {
                            /* dateStamp, metadataLanguage, hierarchyLevel, resourceLogoUrl */
                            commonElements2 builder, pg
                        }
                    }
                }
            }
        }

        //return eml.toString()  // for production usage
        return XmlUtil.serialize(eml) // pretty-printed for development
    }

    /**
     * Generates EML representation of the resource.
     *
     * @param pg the data resource
     */
    String emlForResource(DataResource pg) {

        def writer = new StringWriter()
        writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        def xml = new groovy.xml.MarkupBuilder(writer)
        xml.setDoubleQuotes(true)
        def dp = pg.dataProvider
        def licence = Licence.where {
            acronym == pg.licenseType
            if (pg.licenseVersion != null) {
                licenceVersion == pg.licenseVersion
            }
        }.list()
        def ids = identifiers(pg)
//        def namespaces = [:]
//        namespaces.putAll(ns)
//        namespaces.putAll(emlNs)

        xml."eml:eml"(ids.ns) {
            dataset {
                /* External identifiers will be converted to alternative identifiers */
                pg.externalIdentifiers.each { ext ->
                    alternateIdentifier {
                        // Typical EML pattern: a value + an optional type attribute
                        mkp.yield ext.identifier
                    }
                }

                /* title, creator, metadataProvider, associatedParty, pubDate, language, abstract */
                commonElements1 xml, pg

                /* additional info */
                if (pg.dataGeneralizations || pg.informationWithheld) {
                    additionalInfo() {
                        if (pg.dataGeneralizations) {
                            para pg.dataGeneralizations
                        }

                        if (pg.informationWithheld) {
                            para pg.informationWithheld
                        }
                    }
                }

                /* intellectual rights */
                if (pg.rights || pg.citation || licence) {
                    intellectualRights {
                        para (){
                            mkp.yield pg.rights?:''
                            if (pg.rights && pg.citation){
                                mkp.yield " "
                            }
                            mkp.yield pg.citation?:''
                            if (licence) {
                                def lic = licence.first()
                                mkp.yield " "
                                ulink(url: lic.url) {
                                    citetitle() {
                                        mkp.yield lic.name
                                        if (lic.acronym) {
                                            mkp.yield " ("
                                            mkp.yield lic.acronym
                                            if (lic.licenceVersion) {
                                                mkp.yield " "
                                                mkp.yield lic.licenceVersion
                                            }
                                            mkp.yield ")"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                /* distribution */
                distribution {
                    online {
                        url('function':'information',"${grailsApplication.config.grails.serverURL}/public/show/" + pg.uid)
                    }
                }

                if ((pg.geographicDescription && pg.westBoundingCoordinate) || (pg.beginDate && pg.endDate) || pg.taxonomyHints) {
                    coverage {
                        if (pg.geographicDescription && pg.westBoundingCoordinate) {
                            geographicCoverage {
                                geographicDescription pg.geographicDescription
                                if(pg.westBoundingCoordinate) {
                                    boundingCoordinates {
                                        westBoundingCoordinate pg.westBoundingCoordinate
                                        eastBoundingCoordinate pg.eastBoundingCoordinate
                                        northBoundingCoordinate pg.northBoundingCoordinate
                                        southBoundingCoordinate pg.southBoundingCoordinate
                                    }
                                }
                            }
                        }
                        if (pg.beginDate && pg.endDate) {
                            temporalCoverage {
                                rangeOfDates {
                                    beginDate {
                                        calendarDate pg.beginDate
                                    }
                                    endDate {
                                        calendarDate pg.endDate
                                    }
                                }
                            }
                        }

                        if (pg.taxonomyHints) {
                            mkp.yieldUnescaped getTaxonomicCoverage(pg.taxonomyHints)
                        }
                    }
                }

                purpose {
                    para pg.purpose?:''
                }

                contacts xml, pg

                if (pg.methodStepDescription || pg.qualityControlDescription) {
                    methods {
                        if (pg.methodStepDescription) {
                            methodStep {
                                description {
                                    para pg.methodStepDescription?:''
                                }
                            }
                        }
                        if (pg.qualityControlDescription) {
                            qualityControl {
                                description {
                                    para pg.qualityControlDescription?:''
                                }
                            }
                        }
                    }
                }
            }

            additionalMetadata() {
                metadata() {
                    gbif() {
                        /* dateStamp, metadataLanguage, hierarchyLevel, resourceLogoUrl */
                        commonElements2 xml, pg
                        if (pg.citation?.trim()) { citation pg.citation.trim() }
                    }
                }
            }
        }

        //return eml.toString()  // for production usage
        writer.toString()// pretty-printed for development
    }

    def addIf = { value, tag ->
        { it ->
            if (value)
                "${tag}"(value)
        }
    }

    def addAddress = { ad ->
        address {
            addIf(ad.street, 'deliveryPoint' )
            addIf(ad.city, 'city' )
            addIf(ad.state, 'administrativeArea' )
            addIf(ad.postcode, 'postalCode' )
            addIf(ad.country, 'country' )
        }
    }

    /**
     * Inject ALA as an agentType or agentTypeWithRole
     * @param boolean if true will include role
     */
    def ala = { withRole ->
        { it ->
            def orgName = grailsApplication.config.eml.organizationName?.toString()?.trim()
            if (orgName) { organizationName orgName }
            address {
                def dp = grailsApplication.config.eml.deliveryPoint?.toString()?.trim()
                if (dp) { deliveryPoint dp }
                def cty = grailsApplication.config.eml.city?.toString()?.trim()
                if (cty) { city cty }
                def aa = grailsApplication.config.eml.administrativeArea?.toString()?.trim()
                if (aa) { administrativeArea aa }
                def pc = grailsApplication.config.eml.postalCode?.toString()?.trim()
                if (pc) { postalCode pc }
                def ctr = grailsApplication.config.eml.country?.toString()?.trim()
                if (ctr) { country ctr }
            }
            def email = grailsApplication.config.eml.electronicMailAddress?.toString()?.trim()
            if (email) { electronicMailAddress email }
            if (withRole) {
                role "distributor"
            }
        }
    }

    def stripFormatting(List items) {
        items.collect {
            if (it) {
                removeMarkup(handleLinks(it))
            } else {
                ''
            }
        }.join('\n').trim()
    }

    def removeMarkup(str) {
        if (str) {
            def italicMarkup = /_([^\r\n_]*)_/
            str = str.replaceAll(italicMarkup) {match, group -> group}
            def boldMarkup = /\+([^\r\n+]*)\+/
            str = str.replaceAll(boldMarkup) {match, group -> group}
        }
        return str
    }

    /**
     * Outputs str as content of the specified tag with bold markup (+xxx+) output as emphasis.
     *
     * @param builder
     * @param tag
     * @param str
     */
    def docBookEmphasis(builder, String tag, String str) {
        // docbook has no tag for italics so treat both italics and bold as emphasis
        builder."${tag}"() {
            def em = ""
            def inEm = false
            str.each { ch ->
                if (ch == '+') {
                    if (inEm) {
                        // end of emphasis span
                        builder.emphasis em
                        em = ""
                        inEm = false
                    } else {
                        // start emphasis span
                        inEm = true
                    }
                } else {
                    if (inEm) {
                        // add to span
                        em += ch
                    } else {
                        // just output
                        mkp.yield ch
                    }
                }
            }
        }
    }

    /**
     * Transforms wiki style link markup ([url name]) to name (url)
     * @param str
     * @return
     */
    def handleLinks(str) {
        if (str) {
            def urlMatch = /\[(https?:\S*)\b ([^\]]*)\]/   // [(http + s(optional) + : + text to next word boundary + space + all text until next ]
            str = str.replaceAll(urlMatch) {s1, s2, s3 ->
                "${s2} (${s3})"
            }
        }
        return str
    }

    def getCuratorialUnit(Collection pg) {
        def types = pg.collectionType
        if (types =~ "preserved") {
            return "specimens"
        }
        else if (types =~ "cellcultures") {
            return "cultures"
        }
        else if (types =~ "genetic") {
            return "samples"
        }
        else {
            return "specimens"  // default
        }
    }

    def getTaxonomicCoverage(String hints) {
        def json
        try {
            json = new JsonSlurper(type: JsonParserType.LAX).parseText(hints)
        } catch (JsonException ignored) {
            json = new JsonSlurper().parseText(hints)
        }
        def range = json?.range ?: []
        def coverage = json?.coverage ?: []
        def builder = new StreamingMarkupBuilder()

        return builder.bind {
            taxonomicCoverage {
                generalTaxonomicCoverage {
                    // yield the joined string properly
                    mkp.yield range.collect { it.replaceAll('"','').trim() }.join("; ")
                }
                coverage.each { entry ->
                    entry.each { rank, value ->
                        taxonomicClassification {
                            taxonRankName(rank)
                            taxonRankValue(value)
                        }
                    }
                }
            }
        }
    }
}
