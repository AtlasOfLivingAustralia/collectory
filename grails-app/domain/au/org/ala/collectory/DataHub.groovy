package au.org.ala.collectory

import grails.converters.JSON

class DataHub implements ProviderGroup, Serializable {

    static final String ENTITY_TYPE = 'DataHub'
    static final String ENTITY_PREFIX = 'dh'

    static auditable = [ignore: ['version','dateCreated','lastUpdated','userLastModified']]

    String memberInstitutions = '[]'       // json list of uids of member institutions
    String memberCollections = '[]'        // json list of uids of member collections
    String memberDataResources = '[]'      // json list of uids of member data resources
    String members = '[]'                  // non-overlapping json list of uids of member institutions and collections
                                           //  (suitable for identifying a unique list of occurrence records)

    static hasMany = [externalIdentifiers: ExternalIdentifier]

    static constraints = {
        guid(nullable:true, maxSize:256)
        uid(blank:false, maxSize:20)
        name(blank:false, maxSize:1024)
        acronym(nullable:true, maxSize:45)
        pubShortDescription(nullable:true, maxSize:100)
        pubDescription(nullable:true)
        techDescription(nullable:true)
        focus(nullable:true)
        address(nullable:true)
        latitude(nullable:true)
        longitude(nullable:true)
        altitude(nullable:true)
        state(nullable:true, maxSize:45)
         websiteUrl(nullable:true, maxSize:256, validator: { val, obj ->
             if (val && !val.toString().startsWith('http://') && !val.toString().startsWith('https://')) {
                 return 'providerGroup.websiteUrl.invalid'
             }
         })
        logoRef(nullable:true)
        imageRef(nullable:true)
        email(nullable:true, maxSize:256)
        phone(nullable:true, maxSize:200)
        notes(nullable:true)
        networkMembership(nullable:true, maxSize:256)
        attributions(nullable:true, maxSize:256)
        taxonomyHints(nullable:true)
        keywords(nullable:true)
        gbifRegistryKey(nullable:true, maxSize:36)

        memberCollections(nullable:true, maxSize:4096)
        memberInstitutions(nullable:true, maxSize:4096)
        memberDataResources(nullable:true, maxSize:4096)
        members(nullable:true, maxSize:4096)
    }

    static mapping = {
        uid index:'uid_idx'
        pubShortDescription type: "text"
        pubDescription type: "text"
        techDescription type: "text"
        focus type: "text"
        taxonomyHints type: "text"
        notes type: "text"
        networkMembership type: "text"
        memberCollections  type: "text"
        memberInstitutions  type: "text"
        memberDataResources  type: "text"
        members  type: "text"
    }

    static transients =  ['collectionMember', 'institutionMember', 'dataResourceMember']

    boolean canBeMapped() {
        return false;
    }

    /**
     * Returns a summary of the data provider including:
     * - id
     * - name
     * - acronym
     * - lsid if available
     * - description
     * - provider codes for matching with biocache records
     *
     * @return CollectionSummary
     */
    ProviderGroupSummary buildSummary() {
        ProviderGroupSummary dps = init(new ProviderGroupSummary())
        //cs.derivedInstCodes = getListOfInstitutionCodesForLookup()
        //cs.derivedCollCodes = getListOfCollectionCodesForLookup()
        return dps
    }

    def listMembers() {
        return members ? JSON.parse(members).collect {it} : []
    }

    def listMemberInstitutions() {
        if (!memberInstitutions) { return []}
        JSON.parse(memberInstitutions).collect {
            def pg = Institution.findByUid(it)
            if (pg) {
                [uid: it, name: pg?.name, uri: pg.buildUri()]
            } else {
                [uid: it, name: 'institution missing']
            }
        }.sort { it.name }
    }

    def listMemberCollections() {
        if (!memberCollections) { return []}
        JSON.parse(memberCollections).collect {
            log.info "it = ${it}"
            def pg = Collection.findByUid(it)
            log.info "pg = ${pg}"
            if (pg) {
                [uid: it, name: pg?.name, uri: pg.buildUri()]
            } else {
                [uid: it, name: 'collection missing']
            }
        }.sort { it.name }
    }

    def listMemberDataResources() {
        if (!memberDataResources) { return []}
        JSON.parse(memberDataResources).collect {
            def pg = DataResource.findByUid(it)
            if (pg) {
                [uid: it, name: pg?.name, uri: pg.buildUri()]
            } else {
                [uid: it, name: 'data resource missing']
            }
        }.sort { it.name }
    }

    def isCollectionMember(String uid) {
        return JSON.parse(memberCollections).contains(uid)
    }

    def isInstitutionMember(String uid) {
        return JSON.parse(memberInstitutions).contains(uid)
    }

    def isDataResourceMember(String uid) {
        return JSON.parse(memberDataResources).contains(uid)
    }

    def isMember(String uid) {
        return isDataResourceMember(uid) ||
               isInstitutionMember(uid) ||
               isCollectionMember(uid)
    }

    long dbId() {
        return id;
    }

    String entityType() {
        return ENTITY_TYPE;
    }

}
