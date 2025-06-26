package au.org.ala.collectory

import au.org.ala.web.AuthService
import au.org.ala.ws.security.client.AlaAuthClient
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.request.RequestContextHolder

import javax.servlet.http.HttpServletRequest

class CollectoryAuthService{
    static transactional = false
    def grailsApplication
    def providerGroupService
    AuthService authService

    @Autowired(required = false)
    AlaAuthClient alaAuthClient

    /**
     * todo check if it complies with the new security plugin
     * @return
     */
    String username() {
        //both work
        //def usernamea = authService.getDisplayName()
        def request = getRequest()
        def profiles = request?.profileManager?.context?.request?.profiles
        def username = (profiles && !profiles.isEmpty()) ? profiles[0]?.attributes?.get('username')?.toString() : null
        return (username) ? username : 'not available'
    }

    String userEmail() {
        String email = authService.getEmail()
        return email
    }

    /**
     * todo check if it complies with the new security plugin
     * @return
     */
    def isAdmin() {
         def request = getRequest()
        return request?.isUserInRole(grailsApplication.config.ROLE_ADMIN as String)
    }

    /**
     * Checks if the user has the specified role and/or scope if it is a M2M request.
     * @param request
     * @param role
     * @param scope
     */
    def checkPermissions(String role, String scope) {
        def request = getRequest()
        return request?.isUserInRole(role) || request.isUserInRole(scope)
    }

    protected boolean userInRole(role) {
        def roleFlag = false
        if(!grailsApplication.config.security.oidc.enabled.toBoolean()) {
            roleFlag = true
        }

        return roleFlag || isAdmin()
    }

    /**
     * Returns a list of entities that the specified user is authorised to edit.
     *
     * Note that more than one contact may correspond to the user's email address. In this
     * case, the result is a union of the lists for each contact.
     *
     * @param email
     * @return a map holding entities, a list of their uids and the latest modified date
     */
    def authorisedForUser(String email) {
        def contacts = Contact.findAllByEmail(email)
        switch (contacts.size()) {
            case 0: return [sorted: [], keys: [], latestMod: null]
            case 1: return authorisedForUser(contacts[0])
            default:
                def result = [sorted: [], keys: [], latestMod: null]
                contacts.each {
                    def oneResult = authorisedForUser(it)
                    result.sorted += oneResult.sorted
                    result.keys += oneResult.keys
                    if (oneResult.latestMod > result.latestMod) { result.latestMod = oneResult.latestMod }
                }
                return result
        }
    }

    /**
     * Returns a list of entities that the specified contact is authorised to edit.
     *
     * @param contact
     * @return a map holding entities, a list of their uids and the latest modified date
     */
    def authorisedForUser(Contact contact) {
        // get list of contact relationships
        def latestMod = null
        def entities = [:]  // map by uid to remove duplicates
        ContactFor.findAllByContact(contact).each {
            if (it.administrator) {
                def pg = providerGroupService._get(it.entityUid)
                if (pg) {
                    entities.put it.entityUid, [uid: pg.uid, name: pg.name]
                    if (it.dateLastModified > latestMod) { latestMod = it.dateLastModified }
                }
                // add children
                pg.children().each { child ->
                    // children() now seems to return some internal class resources
                    // so make sure they are PGs
                    if (child instanceof ProviderGroup) {
                        def ch = providerGroupService._get(child.uid)
                        if (ch) {
                            entities.put ch.uid, [uid: ch.uid, name: ch.name]
                        }
                    }
                }
            }
        }
        return [sorted: entities.values().sort { it.name }, keys:entities.keySet().sort(), latestMod: latestMod]
    }

    private HttpServletRequest getRequest() {
        def webRequest = RequestContextHolder.currentRequestAttributes() as GrailsWebRequest
        return webRequest.getCurrentRequest()
    }
}
