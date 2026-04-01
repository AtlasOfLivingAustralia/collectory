package au.org.ala.collectory.service;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.Collection;
import au.org.ala.collectory.domain.Contact;
import au.org.ala.collectory.domain.ContactFor;
import au.org.ala.collectory.domain.DataProvider;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.domain.Institution;
import au.org.ala.collectory.domain.ProviderGroup;
import au.org.ala.collectory.repository.ContactForRepository;
import au.org.ala.collectory.repository.ContactRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectoryAuthService {

    private final AppProperties appProperties;
    private final ProviderGroupService providerGroupService;
    private final ContactRepository contactRepository;
    private final ContactForRepository contactForRepository;
    private final Environment environment;

    public String username() {
        HttpServletRequest request = getRequest();
        if (request == null) return "not available";
        Principal principal = request.getUserPrincipal();
        return principal != null ? principal.getName() : "not available";
    }

    public String userEmail() {
        HttpServletRequest request = getRequest();
        if (request == null) return null;
        Principal principal = request.getUserPrincipal();
        return principal != null ? principal.getName() : null;
    }

    public boolean isAdmin() {
        HttpServletRequest request = getRequest();
        return request != null && request.isUserInRole(appProperties.getRoleAdmin());
    }

    public boolean userInRole(String role) {
        if (!appProperties.getSecurity().isOidcEnabled()) {
            return true;
        }
        HttpServletRequest request = getRequest();
        return (request != null && request.isUserInRole(role)) || isAdmin();
    }

    public boolean isAuthorised(String[] roles, String[] scopes) {
        boolean isUserAuthed = false;
        if (roles != null && roles.length > 0) {
            isUserAuthed = isUserAuthorised(resolveRoles(roles));
        }
        boolean isTokenAuthed = isTokenAuthorised(resolveRoles(scopes));
        return isUserAuthed || isTokenAuthed;
    }

    public Map<String, Object> authorisedForUser(String email) {
        List<Contact> contacts = contactRepository.findAllByEmail(email);
        if (contacts.isEmpty()) {
            return mapOf("sorted", List.of(), "keys", List.of(), "latestMod", null);
        }
        if (contacts.size() == 1) {
            return authorisedForUser(contacts.get(0));
        }

        Map<String, Map<String, String>> entities = new LinkedHashMap<>();
        LocalDateTime latestMod = null;
        for (Contact contact : contacts) {
            Map<String, Object> result = authorisedForUser(contact);
            @SuppressWarnings("unchecked")
            List<Map<String, String>> sorted = (List<Map<String, String>>) result.get("sorted");
            sorted.forEach(e -> entities.put(e.get("uid"), e));
            LocalDateTime mod = (LocalDateTime) result.get("latestMod");
            if (mod != null && (latestMod == null || mod.isAfter(latestMod))) {
                latestMod = mod;
            }
        }

        List<Map<String, String>> sorted = entities.values().stream()
                .sorted(Comparator.comparing(e -> e.getOrDefault("name", "")))
                .collect(Collectors.toList());
        List<String> keys = entities.keySet().stream().sorted().collect(Collectors.toList());

        return mapOf("sorted", sorted, "keys", keys, "latestMod", latestMod);
    }

    public Map<String, Object> authorisedForUser(Contact contact) {
        Map<String, Map<String, String>> entities = new LinkedHashMap<>();
        List<ContactFor> contactFors = contactForRepository.findAllByContact(contact);
        LocalDateTime latestMod = null;

        for (ContactFor cf : contactFors) {
            if (cf.getDateLastModified() != null
                    && (latestMod == null || cf.getDateLastModified().isAfter(latestMod))) {
                latestMod = cf.getDateLastModified();
            }
            if (cf.isAdministrator()) {
                ProviderGroup pg = providerGroupService.get(cf.getEntityUid());
                if (pg != null) {
                    entities.put(pg.getUid(), Map.of("uid", pg.getUid(), "name", pg.getName()));

                    // Add child entities:
                    // If the entity is an Institution, also add its collections
                    if (pg instanceof Institution) {
                        Institution inst = (Institution) pg;
                        if (inst.getCollections() != null) {
                            for (Collection child : inst.getCollections()) {
                                ProviderGroup ch = providerGroupService.get(child.getUid());
                                if (ch != null) {
                                    entities.put(ch.getUid(), Map.of("uid", ch.getUid(), "name", ch.getName()));
                                }
                            }
                        }
                    }
                    // If the entity is a DataProvider, also add its data resources
                    else if (pg instanceof DataProvider) {
                        DataProvider dp = (DataProvider) pg;
                        if (dp.getResources() != null) {
                            for (DataResource child : dp.getResources()) {
                                ProviderGroup ch = providerGroupService.get(child.getUid());
                                if (ch != null) {
                                    entities.put(ch.getUid(), Map.of("uid", ch.getUid(), "name", ch.getName()));
                                }
                            }
                        }
                    }
                }
            }
        }

        List<Map<String, String>> sorted = entities.values().stream()
                .sorted(Comparator.comparing(e -> e.getOrDefault("name", "")))
                .collect(Collectors.toList());
        List<String> keys = entities.keySet().stream().sorted().collect(Collectors.toList());

        return mapOf("sorted", sorted, "keys", keys, "latestMod", latestMod);
    }

    /** Build a Map allowing null values (Map.of does not permit nulls). */
    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }

    public String[] resolveRoles(String[] rolesOrScopes) {
        if (rolesOrScopes == null) return new String[0];
        Set<String> resolved = new LinkedHashSet<>();
        for (String key : rolesOrScopes) {
            if (key == null || key.isEmpty()) continue;
            String value = environment.getProperty(key, key);
            for (String part : value.split("[;,]")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    resolved.add(trimmed);
                }
            }
        }
        return resolved.toArray(new String[0]);
    }

    private boolean isTokenAuthorised(String[] scopes) {
        if (scopes == null || scopes.length == 0) return false;
        HttpServletRequest request = getRequest();
        if (request == null) return false;
        for (String scope : scopes) {
            if ("*".equals(scope)) return true;
            if (request.isUserInRole(scope)) return true;
        }
        return false;
    }

    private boolean isUserAuthorised(String[] roles) {
        HttpServletRequest request = getRequest();
        if (request == null) return false;
        for (String role : roles) {
            if (request.isUserInRole(role)) return true;
        }
        return false;
    }

    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
