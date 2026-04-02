package au.org.ala.collectory.controller;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.repository.*;
import au.org.ala.collectory.service.*;
import au.org.ala.collectory.util.JSONHelper;
import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DataController {

    private final CrudService crudService;
    private final ProviderGroupService providerGroupService;
    private final CollectoryAuthService collectoryAuthService;
    private final ActivityLogService activityLogService;
    private final ContactService contactService;
    private final IdGeneratorService idGeneratorService;
    private final EmlRenderService emlRenderService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private final InstitutionRepository institutionRepository;
    private final CollectionRepository collectionRepository;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;
    private final DataHubRepository dataHubRepository;
    private final TempDataResourceRepository tempDataResourceRepository;
    private final ContactRepository contactRepository;
    private final ContactForRepository contactForRepository;
    private final ExternalIdentifierRepository externalIdentifierRepository;
    private final ProviderMapRepository providerMapRepository;
    private final ProviderCodeRepository providerCodeRepository;
    private final AttributionRepository attributionRepository;

    private static final Set<String> VALID_ENTITIES = Set.of(
            "collection", "institution", "dataProvider", "dataResource", "tempDataResource", "dataHub");

    // --- Entity CRUD ---

    @GetMapping("/ws/{entity}/{uid}")
    public ResponseEntity<?> getEntity(
            @PathVariable String entity,
            @PathVariable String uid,
            @RequestParam(required = false) Boolean summary,
            HttpServletRequest request) {
        if (!VALID_ENTITIES.contains(entity)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown entity type: " + entity));
        }

        ProviderGroup pg = providerGroupService.get(uid);
        if (pg == null) {
            return ResponseEntity.notFound().build();
        }

        // Check If-Modified-Since for caching
        long ifModifiedSince = request.getDateHeader("If-Modified-Since");
        if (ifModifiedSince > 0 && pg.getLastUpdated() != null) {
            long lastMod = pg.getLastUpdated().toInstant(ZoneOffset.UTC).toEpochMilli();
            if (lastMod <= ifModifiedSince) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
            }
        }

        Map<String, Object> result = readEntity(pg);
        return ResponseEntity.ok()
                .lastModified(pg.getLastUpdated() != null ?
                        pg.getLastUpdated().toInstant(ZoneOffset.UTC).toEpochMilli() : 0)
                .body(result);
    }

    @GetMapping("/ws/{entity}")
    public ResponseEntity<?> listEntities(
            @PathVariable String entity,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        if (!VALID_ENTITIES.contains(entity)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown entity type: " + entity));
        }

        if ("tempDataResource".equals(entity)) {
            List<TempDataResource> all = tempDataResourceRepository.findAll();
            List<Map<String, Object>> result = all.stream()
                    .map(crudService::readTempDataResource)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        }

        List<? extends ProviderGroup> entities = providerGroupService.listEntities(entity);

        // H4: filter private data resources
        if ("dataResource".equals(entity)) {
            entities = entities.stream()
                    .filter(pg -> !Boolean.TRUE.equals(((DataResource) pg).getIsPrivate()))
                    .collect(Collectors.toList());
        }

        // H5: apply query param filters (mirror Grails filter() logic)
        if (!params.isEmpty()) {
            final List<? extends ProviderGroup> toFilter = entities;
            entities = toFilter.stream().filter(pg -> {
                Map<String, Object> json = readEntityBrief(pg);
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    String key = entry.getKey();
                    String paramValue = entry.getValue();
                    if (!json.containsKey(key)) continue;
                    Object fieldValue = json.get(key);
                    if (fieldValue == null) {
                        if (paramValue != null && !paramValue.isEmpty()) return false;
                    } else if (fieldValue instanceof Boolean) {
                        boolean boolParam = "true".equalsIgnoreCase(paramValue);
                        if (!fieldValue.equals(boolParam)) return false;
                    } else {
                        if (!fieldValue.toString().equals(paramValue)) return false;
                    }
                }
                return true;
            }).collect(Collectors.toList());
        }

        // M21: check If-Modified-Since against most-recently-updated record
        long latestMod = latestModified(entities);
        long ifModifiedSince = request.getDateHeader("If-Modified-Since");
        if (ifModifiedSince > 0 && latestMod > 0 && latestMod <= ifModifiedSince) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        List<Map<String, Object>> result = entities.stream()
                .map(this::readEntityBrief)
                .collect(Collectors.toList());

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (latestMod > 0) {
            builder = builder.lastModified(latestMod);
        }
        return builder.body(result);
    }

    @Transactional
    @PostMapping("/ws/{entity}")
    public ResponseEntity<?> createEntity(
            @PathVariable String entity,
            @RequestBody Map<String, Object> body) {
        return saveOrCreateEntity(entity, null, body);
    }

    @Transactional
    @RequestMapping(method = {RequestMethod.PUT, RequestMethod.POST}, path = "/ws/{entity}/{uid}")
    public ResponseEntity<?> saveEntity(
            @PathVariable String entity,
            @PathVariable String uid,
            @RequestBody Map<String, Object> body) {
        return saveOrCreateEntity(entity, uid, body);
    }

    private ResponseEntity<?> saveOrCreateEntity(String entity, String uid, Map<String, Object> body) {
        if (!VALID_ENTITIES.contains(entity)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown entity type: " + entity));
        }

        boolean isCreate = (uid == null);
        ProviderGroup existing = isCreate ? null : providerGroupService.get(uid);

        if (!isCreate && existing == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Object saved = switch (entity) {
                case "dataProvider" -> isCreate ?
                        crudService.insertDataProvider(body) :
                        crudService.updateDataProvider((DataProvider) existing, body);
                case "dataHub" -> isCreate ?
                        crudService.insertDataHub(body) :
                        crudService.updateDataHub((DataHub) existing, body);
                case "dataResource" -> isCreate ?
                        crudService.insertDataResource(body) :
                        crudService.updateDataResource((DataResource) existing, body);
                case "tempDataResource" -> {
                    if (isCreate) {
                        yield crudService.insertTempDataResource(body);
                    } else {
                        TempDataResource tdr = tempDataResourceRepository.findByUid(uid).orElse(null);
                        yield tdr != null ? crudService.updateTempDataResource(tdr, body) : null;
                    }
                }
                case "institution" -> isCreate ?
                        crudService.insertInstitution(body) :
                        crudService.updateInstitution((Institution) existing, body);
                case "collection" -> isCreate ?
                        crudService.insertCollection(body) :
                        crudService.updateCollection((au.org.ala.collectory.domain.Collection) existing, body);
                default -> null;
            };

            if (saved == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to save entity"));
            }

            HttpStatus status = isCreate ? HttpStatus.CREATED : HttpStatus.OK;
            return ResponseEntity.status(status).body(saved instanceof ProviderGroup pg ?
                    readEntity(pg) : crudService.readTempDataResource((TempDataResource) saved));
        } catch (Exception e) {
            log.error("Error saving entity: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Transactional
    @DeleteMapping("/ws/{entity}/{uid}")
    public ResponseEntity<?> deleteEntity(
            @PathVariable String entity,
            @PathVariable String uid) {
        if (!VALID_ENTITIES.contains(entity)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown entity type"));
        }

        if ("tempDataResource".equals(entity)) {
            TempDataResource tdr = tempDataResourceRepository.findByUid(uid).orElse(null);
            if (tdr == null) return ResponseEntity.notFound().build();
            tempDataResourceRepository.delete(tdr);
            return ResponseEntity.ok(Map.of("message", "Deleted " + uid));
        }

        ProviderGroup pg = providerGroupService.get(uid);
        if (pg == null) return ResponseEntity.notFound().build();

        providerGroupService.deleteEntity(pg);
        return ResponseEntity.ok(Map.of("message", "Deleted " + uid));
    }

    @RequestMapping(value = "/ws/{entity}/{uid}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headEntity(
            @PathVariable String entity,
            @PathVariable String uid) {
        ProviderGroup pg = providerGroupService.get(uid);
        if (pg == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .lastModified(pg.getLastUpdated() != null ?
                        pg.getLastUpdated().toInstant(ZoneOffset.UTC).toEpochMilli() : 0)
                .build();
    }

    @GetMapping("/ws/{entity}/count")
    public ResponseEntity<?> count(
            @PathVariable String entity,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String callback,
            @RequestParam(required = false) String _public) {
        if (!VALID_ENTITIES.contains(entity)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown entity type"));
        }
        List<? extends ProviderGroup> list = providerGroupService.listEntities(entity);

        // suppress declined/private data resources when ?public=true
        if ("dataResource".equals(entity) && "true".equals(_public)) {
            list = list.stream()
                    .filter(pg -> {
                        DataResource dr = (DataResource) pg;
                        return !"declined".equals(dr.getStatus()) && !Boolean.TRUE.equals(dr.getIsPrivate());
                    })
                    .collect(Collectors.toList());
        }

        Map<String, Object> results = new LinkedHashMap<>();
        results.put("total", list.size());

        if (groupBy != null && !groupBy.isBlank()) {
            results.put("groupBy", groupBy);
            Map<String, Integer> groups = new LinkedHashMap<>();
            for (ProviderGroup pg : list) {
                Map<String, Object> json = readEntity(pg);
                Object val = json.get(groupBy);
                String key = val != null ? val.toString() : "null";
                groups.merge(key, 1, Integer::sum);
            }
            results.put("groups", groups);
        }

        return jsonpOk(results, callback);
    }

    @PostMapping("/ws/find/{entity}")
    public ResponseEntity<?> findEntities(
            @PathVariable String entity,
            @RequestBody List<String> uids) {
        List<Map<String, Object>> result = uids.stream()
                .map(providerGroupService::get)
                .filter(Objects::nonNull)
                .map(this::readEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ws/resolveNames/{uids}")
    public ResponseEntity<?> resolveNames(@PathVariable String uids) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String uid : uids.split(",")) {
            ProviderGroup pg = providerGroupService.get(uid.trim());
            if (pg != null) {
                result.put(uid.trim(), pg.getName());
            }
        }
        return ResponseEntity.ok(result);
    }

    // --- EML ---

    @GetMapping(value = {"/ws/eml/{id}", "/eml/{id}"}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> eml(@PathVariable String id) {
        ProviderGroup entity = providerGroupService.get(id);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            String xml = emlRenderService.emlForEntity(entity);
            return ResponseEntity.ok(xml);
        } catch (Exception e) {
            log.error("Error generating EML for {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("<error>Error generating EML</error>");
        }
    }

    // --- DataHub members ---

    @GetMapping("/ws/dataHub/{uid}/institutions")
    public ResponseEntity<?> institutionsForDataHub(@PathVariable String uid) {
        DataHub hub = dataHubRepository.findByUid(uid).orElse(null);
        if (hub == null) return ResponseEntity.notFound().build();

        List<String> memberUids = JSONHelper.parseJsonList(hub.getMemberInstitutions());
        List<Map<String, Object>> result = memberUids.stream()
                .map(providerGroupService::get)
                .filter(Objects::nonNull)
                .map(pg -> Map.<String, Object>of("name", pg.getName(), "uid", pg.getUid()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ws/dataHub/{uid}/collections")
    public ResponseEntity<?> collectionsForDataHub(@PathVariable String uid) {
        DataHub hub = dataHubRepository.findByUid(uid).orElse(null);
        if (hub == null) return ResponseEntity.notFound().build();

        List<String> memberUids = JSONHelper.parseJsonList(hub.getMemberCollections());
        List<Map<String, Object>> result = memberUids.stream()
                .map(providerGroupService::get)
                .filter(Objects::nonNull)
                .map(pg -> Map.<String, Object>of("name", pg.getName(), "uid", pg.getUid()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // --- Contacts ---

    @GetMapping("/ws/contacts")
    public ResponseEntity<?> listContacts() {
        List<Contact> contacts = contactRepository.findAll(Sort.by("lastName"));
        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/ws/contacts/{id}")
    public ResponseEntity<?> getContact(@PathVariable Long id) {
        return contactRepository.findById(id)
                .map(c -> ResponseEntity.ok((Object) c))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/ws/contacts/email/{email}")
    public ResponseEntity<?> getContactByEmail(@PathVariable String email) {
        return contactRepository.findByEmail(email)
                .map(c -> ResponseEntity.ok((Object) c))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PostMapping("/ws/contacts")
    public ResponseEntity<?> createContact(@RequestBody Map<String, Object> body) {
        Contact contact = new Contact();
        updateContactFromMap(contact, body);
        contact.setUserLastModified(collectoryAuthService.username());
        Contact saved = contactRepository.saveAndFlush(contact);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Transactional
    @PutMapping("/ws/contacts/{id}")
    public ResponseEntity<?> updateContact(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Contact contact = contactRepository.findById(id).orElse(null);
        if (contact == null) return ResponseEntity.notFound().build();
        updateContactFromMap(contact, body);
        contact.setUserLastModified(collectoryAuthService.username());
        Contact saved = contactRepository.saveAndFlush(contact);
        return ResponseEntity.ok(saved);
    }

    @Transactional
    @DeleteMapping("/ws/contacts/{id}")
    public ResponseEntity<?> deleteContact(@PathVariable Long id) {
        Contact contact = contactRepository.findById(id).orElse(null);
        if (contact == null) return ResponseEntity.notFound().build();
        contactService.delete(contact);
        return ResponseEntity.ok(Map.of("message", "Deleted contact " + id));
    }

    // --- Entity contacts ---

    @GetMapping("/ws/{entity}/{uid}/contacts")
    public ResponseEntity<?> contactsForEntity(
            @PathVariable String entity,
            @PathVariable String uid) {
        List<ContactFor> cfs = contactForRepository.findAllByEntityUid(uid);
        List<Map<String, Object>> result = cfs.stream()
                .map(cf -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    Contact c = cf.getContact();
                    m.put("contact", c);
                    m.put("role", cf.getRole());
                    m.put("administrator", cf.isAdministrator());
                    m.put("primaryContact", cf.isPrimaryContact());
                    m.put("notify", cf.isNotify());
                    m.put("id", cf.getId());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ws/{entity}/{uid}/contacts/{contactId}")
    public ResponseEntity<?> contactForEntity(
            @PathVariable String entity,
            @PathVariable String uid,
            @PathVariable Long contactId) {
        return contactForRepository.findByEntityUidAndContactId(uid, contactId)
                .map(cf -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("contact", cf.getContact());
                    m.put("role", cf.getRole());
                    m.put("administrator", cf.isAdministrator());
                    m.put("primaryContact", cf.isPrimaryContact());
                    m.put("notify", cf.isNotify());
                    return ResponseEntity.ok((Object) m);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @RequestMapping(method = {RequestMethod.POST, RequestMethod.PUT}, path = "/ws/{entity}/{uid}/contacts/{contactId}")
    public ResponseEntity<?> updateContactFor(
            @PathVariable String entity,
            @PathVariable String uid,
            @PathVariable Long contactId,
            @RequestBody Map<String, Object> body) {
        Contact contact = contactRepository.findById(contactId).orElse(null);
        if (contact == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Contact not found"));
        }

        Optional<ContactFor> existingOpt = contactForRepository.findByEntityUidAndContactId(uid, contactId);
        ContactFor cf;
        boolean isCreate = existingOpt.isEmpty();

        if (isCreate) {
            cf = new ContactFor();
            cf.setContact(contact);
            cf.setEntityUid(uid);
        } else {
            cf = existingOpt.get();
        }

        if (body.containsKey("role")) cf.setRole((String) body.get("role"));
        if (body.containsKey("administrator")) cf.setAdministrator(toBool(body.get("administrator")));
        if (body.containsKey("primaryContact")) cf.setPrimaryContact(toBool(body.get("primaryContact")));
        if (body.containsKey("notify")) cf.setNotify(toBool(body.get("notify")));
        cf.setUserLastModified(collectoryAuthService.username());

        contactForRepository.saveAndFlush(cf);
        return ResponseEntity.status(isCreate ? HttpStatus.CREATED : HttpStatus.OK)
                .body(Map.of("message", isCreate ? "Contact added" : "Contact updated"));
    }

    @Transactional
    @DeleteMapping("/ws/{entity}/{uid}/contacts/{contactId}")
    public ResponseEntity<?> deleteContactFor(
            @PathVariable String entity,
            @PathVariable String uid,
            @PathVariable Long contactId) {
        contactForRepository.findByEntityUidAndContactId(uid, contactId)
                .ifPresent(contactForRepository::delete);
        return ResponseEntity.ok(Map.of("message", "Contact removed from entity"));
    }

    @GetMapping("/ws/{entity}/{uid}/contacts/notifiable")
    public ResponseEntity<?> notifyList(
            @PathVariable String entity,
            @PathVariable String uid) {
        List<ContactFor> cfs = contactForRepository.findAllByEntityUidAndNotifyTrue(uid);
        List<Map<String, Object>> result = cfs.stream()
                .map(cf -> Map.<String, Object>of(
                        "contact", cf.getContact(),
                        "role", cf.getRole() != null ? cf.getRole() : ""))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // --- External identifiers ---

    @GetMapping("/ws/{entity}/{uid}/externalIdentifiers")
    public ResponseEntity<?> externalIdentifiers(
            @PathVariable String entity,
            @PathVariable String uid) {
        List<ExternalIdentifier> identifiers = externalIdentifierRepository.findAllByEntityUid(uid);
        return ResponseEntity.ok(identifiers);
    }

    // --- Attributions ---

    @GetMapping("/ws/{entity}/{uid}/attributions")
    public ResponseEntity<?> attributions(
            @PathVariable String entity,
            @PathVariable String uid) {
        ProviderGroup pg = providerGroupService.get(uid);
        if (pg == null) return ResponseEntity.notFound().build();
        String attributionsStr = pg.getAttributions();
        if (attributionsStr == null || attributionsStr.isBlank()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<String> uids = Arrays.asList(attributionsStr.trim().split("\\s+"));
        List<Map<String, Object>> result = attributionRepository
                .findAllByUidIn(uids).stream()
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", a.getName());
                    m.put("url", a.getUrl());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // H7: Notification endpoint — accepts event notifications from biocache/other services
    @Transactional
    @PostMapping("/ws/notify")
    public ResponseEntity<?> notify(@RequestBody Map<String, Object> payload) {
        String uid = payload.get("uid") != null ? payload.get("uid").toString() : null;
        String event = payload.get("event") != null ? payload.get("event").toString() : null;
        String id = payload.get("id") != null ? payload.get("id").toString() : null;
        String action = payload.get("action") != null ? payload.get("action").toString() : null;

        if (uid == null || event == null || id == null || action == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "must specify a uid, an event and an event id"));
        }

        activityLogService.log("notify-service", false, Action.NOTIFY, action + "d " + id);
        return ResponseEntity.ok(Map.of("message", "notification accepted"));
    }

    @GetMapping("/ws/contacts/{id}/authorised")
    public ResponseEntity<?> authorisedForContact(@PathVariable Long id) {
        Contact contact = contactRepository.findById(id).orElse(null);
        if (contact == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(collectoryAuthService.authorisedForUser(contact));
    }

    // --- Connection parameters ---

    @GetMapping("/ws/dataResource/{uid}/connectionParameters")
    public ResponseEntity<?> connectionParameters(@PathVariable String uid) {
        DataResource dr = dataResourceRepository.findByUid(uid).orElse(null);
        if (dr == null) return ResponseEntity.notFound().build();
        if (dr.getConnectionParameters() == null) {
            return ResponseEntity.ok(Map.of());
        }
        return ResponseEntity.ok(JSONHelper.parseJson(dr.getConnectionParameters()));
    }

    // --- Code map dump ---

    @GetMapping(value = "/ws/codeMapDump", produces = "text/csv")
    public void codeMapDump(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=codeMapDump.csv");
        var writer = response.getWriter();
        writer.println("collection_uid,collection_name,institution_code,collection_code");

        List<ProviderMap> maps = providerMapRepository.findAll();
        for (ProviderMap pm : maps) {
            if (pm.getCollection() != null) {
                String collUid = pm.getCollection().getUid();
                String collName = pm.getCollection().getName();
                Set<ProviderCode> instCodes = pm.getInstitutionCodes();
                Set<ProviderCode> collCodes = pm.getCollectionCodes();
                String instCodeStr = instCodes != null ?
                        instCodes.stream().map(ProviderCode::getCode).collect(Collectors.joining("|")) : "";
                String collCodeStr = collCodes != null ?
                        collCodes.stream().map(ProviderCode::getCode).collect(Collectors.joining("|")) : "";
                writer.println(String.join(",", collUid, csvEscape(collName), instCodeStr, collCodeStr));
            }
        }
        writer.flush();
    }

    // --- File serving ---

    @GetMapping({"/data/{directory}/{file}", "/static/data/{directory}/{file}"})
    public ResponseEntity<Resource> serveFile(
            @PathVariable String directory,
            @PathVariable String file) {
        File f = new File(appProperties.getRepositoryLocationImages() + "/" + directory + "/" + file);
        if (!f.exists()) {
            return ResponseEntity.notFound().build();
        }
        try {
            String contentType = Files.probeContentType(f.toPath());
            return ResponseEntity.ok()
                    .contentType(contentType != null ?
                            MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                    .body(new FileSystemResource(f));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping({"/upload/{directory}/{file}", "/ws/upload/{directory}/{file}"})
    public ResponseEntity<Resource> fileDownload(
            @PathVariable String directory,
            @PathVariable String file) {
        File f = new File(appProperties.getUploadFilePath() + "/" + directory + "/" + file);
        if (!f.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(f));
    }

    // --- WS catalogue ---

    @GetMapping("/ws")
    public ResponseEntity<?> catalogue() {
        return ResponseEntity.ok(Map.of(
                "message", "Collectory web services",
                "entities", VALID_ENTITIES));
    }

    // --- Consumer/provider relationships ---

    @GetMapping("/ws/showConsumers/{uid}")
    public ResponseEntity<?> showConsumers(@PathVariable String uid) {
        ProviderGroup pg = providerGroupService.get(uid);
        if (pg == null) return ResponseEntity.notFound().build();

        List<Map<String, String>> consumers = new ArrayList<>();
        if (pg instanceof DataProvider dp) {
            if (dp.getConsumerInstitutions() != null) {
                dp.getConsumerInstitutions().forEach(i -> consumers.add(Map.of("uid", i.getUid(), "name", i.getName())));
            }
            if (dp.getConsumerCollections() != null) {
                dp.getConsumerCollections().forEach(c -> consumers.add(Map.of("uid", c.getUid(), "name", c.getName())));
            }
        } else if (pg instanceof DataResource dr) {
            if (dr.getConsumerInstitutions() != null) {
                dr.getConsumerInstitutions().forEach(i -> consumers.add(Map.of("uid", i.getUid(), "name", i.getName())));
            }
            if (dr.getConsumerCollections() != null) {
                dr.getConsumerCollections().forEach(c -> consumers.add(Map.of("uid", c.getUid(), "name", c.getName())));
            }
        }
        return ResponseEntity.ok(Map.of("uid", uid, "name", pg.getName(), "consumers", consumers));
    }

    @GetMapping("/ws/showProviders/{uid}")
    public ResponseEntity<?> showProviders(@PathVariable String uid) {
        ProviderGroup pg = providerGroupService.get(uid);
        if (pg == null) return ResponseEntity.notFound().build();

        List<Map<String, String>> providers = new ArrayList<>();
        if (pg instanceof Institution inst) {
            dataProviderRepository.findAll().stream()
                    .filter(dp -> dp.getConsumerInstitutions() != null && dp.getConsumerInstitutions().contains(inst))
                    .forEach(dp -> providers.add(Map.of("uid", dp.getUid(), "name", dp.getName())));
            dataResourceRepository.findAll().stream()
                    .filter(dr -> dr.getConsumerInstitutions() != null && dr.getConsumerInstitutions().contains(inst))
                    .forEach(dr -> providers.add(Map.of("uid", dr.getUid(), "name", dr.getName())));
        } else if (pg instanceof au.org.ala.collectory.domain.Collection co) {
            dataProviderRepository.findAll().stream()
                    .filter(dp -> dp.getConsumerCollections() != null && dp.getConsumerCollections().contains(co))
                    .forEach(dp -> providers.add(Map.of("uid", dp.getUid(), "name", dp.getName())));
            dataResourceRepository.findAll().stream()
                    .filter(dr -> dr.getConsumerCollections() != null && dr.getConsumerCollections().contains(co))
                    .forEach(dr -> providers.add(Map.of("uid", dr.getUid(), "name", dr.getName())));
        }
        return ResponseEntity.ok(Map.of("uid", uid, "name", pg.getName(), "providers", providers));
    }

    // --- Fragment endpoint (M24) ---

    @GetMapping("/ws/fragment/{entity}/{uid}")
    public ResponseEntity<?> getFragment(
            @PathVariable String entity,
            @PathVariable String uid) {
        ProviderGroup pg = providerGroupService.get(uid);
        if (pg == null) return ResponseEntity.notFound().build();
        // Grails rendered a GSP HTML fragment here; in the REST API we return the JSON entity instead
        return ResponseEntity.ok(readEntity(pg));
    }

    // --- Helpers ---

    private Map<String, Object> readEntity(ProviderGroup pg) {
        if (pg instanceof DataProvider dp) return crudService.readDataProvider(dp);
        if (pg instanceof DataHub dh) return crudService.readDataHub(dh);
        if (pg instanceof DataResource dr) return crudService.readDataResource(dr, true);
        if (pg instanceof Institution inst) return crudService.readInstitution(inst);
        if (pg instanceof au.org.ala.collectory.domain.Collection co) return crudService.readCollection(co);
        return Map.of("uid", pg.getUid(), "name", pg.getName());
    }

    /**
     * Lightweight brief format for list endpoints — matches Grails {name, uri, uid} brief plus
     * the extra fields the admin list UI needs. Avoids N+1 queries triggered by readEntity()
     * (linkedRecordProviders, hubMembership, etc.).
     */
    private Map<String, Object> readEntityBrief(ProviderGroup pg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", pg.getName());
        m.put("uid", pg.getUid());
        m.put("acronym", pg.getAcronym());
        m.put("uri", pg.buildUri(appProperties.getServerUrl()));
        m.put("lastUpdated", pg.getLastUpdated());

        if (pg instanceof au.org.ala.collectory.domain.Collection co) {
            m.put("collectionType", JSONHelper.parseJson(co.getCollectionType()));
            if (co.getInstitution() != null) {
                m.put("institution", Map.of(
                        "name", co.getInstitution().getName(),
                        "uid", co.getInstitution().getUid()));
            }
        } else if (pg instanceof Institution inst) {
            m.put("isALAPartner", inst.isALAPartner());
            m.put("institutionType", inst.getInstitutionType());
        } else if (pg instanceof DataResource dr) {
            m.put("resourceType", dr.getResourceType());
            m.put("isPrivate", dr.getIsPrivate());
            m.put("licenseType", dr.getLicenseType());
            m.put("verified", crudService.isDataResourceVerified(dr));
        } else if (pg instanceof DataProvider dp) {
            m.put("gbifRegistryKey", dp.getGbifRegistryKey());
        }
        return m;
    }

    private void updateContactFromMap(Contact contact, Map<String, Object> body) {
        if (body.containsKey("title")) contact.setTitle((String) body.get("title"));
        if (body.containsKey("firstName")) contact.setFirstName((String) body.get("firstName"));
        if (body.containsKey("lastName")) contact.setLastName((String) body.get("lastName"));
        if (body.containsKey("email")) contact.setEmail((String) body.get("email"));
        if (body.containsKey("phone")) contact.setPhone((String) body.get("phone"));
        if (body.containsKey("fax")) contact.setFax((String) body.get("fax"));
        if (body.containsKey("mobile")) contact.setMobile((String) body.get("mobile"));
        if (body.containsKey("notes")) contact.setNotes((String) body.get("notes"));
        if (body.containsKey("publish")) contact.setPublish(toBool(body.get("publish")));
    }

    private boolean toBool(Object val) {
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    private String csvEscape(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    /** M20: Wrap response as JSONP if callback param is present. */
    private ResponseEntity<?> jsonpOk(Object body, String callback) {
        if (callback != null && !callback.isBlank()) {
            String json;
            try {
                json = objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                json = "{}";
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/javascript;charset=UTF-8"))
                    .body(callback + "(" + json + ")");
        }
        return ResponseEntity.ok(body);
    }

    /** M21: Return the most recent lastUpdated timestamp across a list of entities. */
    private long latestModified(List<? extends ProviderGroup> entities) {
        return entities.stream()
                .map(ProviderGroup::getLastUpdated)
                .filter(Objects::nonNull)
                .mapToLong(dt -> dt.toInstant(ZoneOffset.UTC).toEpochMilli())
                .max()
                .orElse(0L);
    }
}
