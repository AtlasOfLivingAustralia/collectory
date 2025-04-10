package au.org.ala.collectory

import au.org.ala.ws.security.ApiKeyClient
import au.org.ala.ws.security.CheckApiKeyResult
import au.org.ala.ws.security.JwtProperties
import au.org.ala.ws.security.client.AlaAuthClient
import au.org.ala.ws.service.WebService
import grails.web.servlet.mvc.GrailsParameterMap
import org.pac4j.core.config.Config
import org.pac4j.core.context.WebContext
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.context.session.SessionStoreFactory
import org.pac4j.core.credentials.Credentials
import org.pac4j.core.profile.ProfileManager
import org.pac4j.core.profile.UserProfile
import org.pac4j.jee.context.JEEContextFactory
import org.pac4j.oidc.credentials.OidcCredentials
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.request.RequestContextHolder
import retrofit2.Call
import retrofit2.Response

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class CollectoryAuthService{
    static transactional = false
    def grailsApplication
    def authService
    def providerGroupService
    def apiKeyClient

    @Autowired
    JwtProperties jwtProperties
    @Autowired(required = false)
    Config config
    @Autowired(required = false)
    AlaAuthClient alaAuthClient

    static final API_KEY_COOKIE = "ALA-API-Key"

    def username() {
        def username = authService.getDisplayName()

        return (username) ? username : 'not available'
    }

    def isAdmin() {
        return !grailsApplication.config.security.oidc.enabled.toBoolean() || authService.userInRole(grailsApplication.config.ROLE_ADMIN as String)
    }

    protected boolean userInRole(role) {
        def roleFlag = false
        if(!grailsApplication.config.security.oidc.enabled.toBoolean())
            roleFlag = true
        else {
            if (authService) {
                roleFlag = authService.userInRole(role)
            }
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

    /**
     * Get the provided api key from all possible options i.e. params, cookie, and header
     * @param params
     * @param request
     * @return apiKey String
     */
    private static String getApiKey(params, HttpServletRequest request) {
        def apiKey = {
            // handle api keys if present in params
            if (request.JSON && !(request.JSON instanceof List) && request.JSON.api_key) {
                request.JSON.api_key
            } else if (request.JSON && !(request.JSON instanceof List) && request.JSON.apiKey) {
                request.JSON.apiKey
            } else if (request.JSON && !(request.JSON instanceof List) && request.JSON.Authorization) {
                request.JSON.Authorization
            } else if (params.api_key) {
                params.api_key
            }else if (params.apiKey) {
                params.apiKey
                // handle api keys if present in cookie
            } else  if (request.cookies.find { cookie -> cookie.name == API_KEY_COOKIE }){
                def cookieApiKey = request.cookies.find { cookie -> cookie.name == API_KEY_COOKIE }
                cookieApiKey.value
            } else {
                // handle api key in  header. check for default api key header and the check for Authorization
                def headerValue = request.getHeader(WebService.DEFAULT_API_KEY_HEADER) ?: request.getHeader("Authorization")
                headerValue
            }
        }.call()
        apiKey
    }



    def checkJWT(HttpServletRequest request, HttpServletResponse response, String requiredRole, String requiredScope) {
        def result = false

        if (jwtProperties.enabled) {
            def context = context(request, response)
            def sessionStore = sessionStore()
            ProfileManager profileManager = new ProfileManager(context, sessionStore)
            profileManager.setConfig(config)

            result = alaAuthClient.getCredentials(context, sessionStore)
                    .map { credentials -> checkCredentials(requiredScope, credentials, requiredRole, context, profileManager) }
        }
        return result
    }

    /**
     * Validate the given credentials against any required scope or role
     *
     * @param requiredScope The required scope for the access token, if any
     * @param credentials The credentials, should be an OidcCredentials instance
     * @param requiredRole The required role for the user, if any
     * @param context The web context (request, response)
     * @param profileManager The profile manager, the user profile if available, will be saved into this profile manager
     * @return true if the credentials match both the requiredScope and requiredRole
     */
    private boolean checkCredentials(String requiredScope, Credentials credentials, String requiredRole, WebContext context, ProfileManager profileManager) {
        boolean matchesScope
        if (requiredScope) {

            if (credentials instanceof OidcCredentials) {

                OidcCredentials oidcCredentials = credentials

                matchesScope = oidcCredentials.accessToken.scope.contains(requiredScope)

                if (!matchesScope) {
                    log.debug "access_token scopes '${oidcCredentials.accessToken.scope}' is missing required scopes ${requiredScope}"
                }
            } else {
                matchesScope = false
                log.debug("$credentials are not OidcCredentials, so can't get access_token")
            }
        } else {
            matchesScope = true
        }

        boolean matchesRole
        Optional<UserProfile> userProfile = alaAuthClient.getUserProfile(credentials, context, config.sessionStore)
                .map { userProfile -> // save profile into profile manager to match pac4j filter
                    profileManager.save(
                            alaAuthClient.getSaveProfileInSession(context, userProfile),
                            userProfile,
                            alaAuthClient.isMultiProfile(context, userProfile)
                    )
                    userProfile
                }
        if (requiredRole) {
            matchesRole = userProfile
                    .map {profile -> checkProfileRole(profile, requiredRole) }
                    .orElseGet {
                        log.debug "rejecting request because role $requiredRole is required but no user profile is available"
                        false
                    }
        } else {
            matchesRole = true
        }

        return matchesScope && matchesRole
    }

    /**
     * Checks that the given profile has the required role
     * @param userProfile
     * @param requiredRole
     * @return true if the profile has the role, false otherwise
     */
     boolean checkProfileRole(UserProfile userProfile, String requiredRole) {
        def userProfileContainsRole = userProfile.roles.contains(requiredRole)

        if (!userProfileContainsRole) {
            log.debug "user profile roles '${userProfile.roles}' is missing required role ${requiredRole}"
        }
        return userProfileContainsRole
    }

    private WebContext context(request, response) {
        final WebContext context = FindBest.webContextFactory(null, config, JEEContextFactory.INSTANCE).newContext(request, response)
        return context
    }

    private SessionStore sessionStore() {
        final SessionStore sessionStore = FindBest.sessionStoreFactory(null, config, JEEContextFactory.INSTANCE as SessionStoreFactory).newSessionStore()
        return sessionStore
    }

    def isAuthorisedWsRequest(GrailsParameterMap params, HttpServletRequest request, HttpServletResponse response, String requiredRole, String requiredScope){
        Boolean authorised = false
        if(grailsApplication.config.security.apikey.checkEnabled.toBoolean() || grailsApplication.config.security.apikey.enabled.toBoolean()){
            def apiKey = getApiKey(params, request)
            if (apiKey) {
                Call<CheckApiKeyResult> checkApiKeyCall = apiKeyClient.checkApiKey(apiKey)
                final Response<CheckApiKeyResult> checkApiKeyResponse = checkApiKeyCall.execute()
                CheckApiKeyResult apiKeyCheck = checkApiKeyResponse.body();
                authorised = apiKeyCheck.isValid()
            }
        }

        if(!authorised){
            authorised = checkJWT(request, response, requiredRole, requiredScope)
        }
        return authorised
    }

}
