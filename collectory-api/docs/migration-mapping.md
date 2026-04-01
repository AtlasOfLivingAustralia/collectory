# Collectory Migration Mapping

## Domain Class → JPA Entity Mapping

| Groovy Domain | Java Entity | Key Changes |
|---------------|-------------|-------------|
| `ProviderGroup` (trait) | `ProviderGroup` (@MappedSuperclass) | 30+ fields, Address/Image embedded |
| `Collection` | `Collection` | @ManyToOne Institution, @OneToOne ProviderMap |
| `Institution` | `Institution` | @OneToMany Collection, childInstitutions as text |
| `DataProvider` | `DataProvider` | @OneToMany DataResource, hiddenJSON |
| `DataResource` | `DataResource` | @ManyToOne DataProvider/Institution, many GBIF fields |
| `DataHub` | `DataHub` | memberInstitutions/Collections/DataResources as text |
| `TempDataResource` | `TempDataResource` | Standalone, no ProviderGroup parent |
| `Contact` | `Contact` | Standalone entity |
| `ContactFor` | `ContactFor` | entityUid links to any entity's uid |
| `ExternalIdentifier` | `ExternalIdentifier` | @ManyToMany from each entity type |
| `Attribution` | `Attribution` | Standalone |
| `Licence` | `Licence` | Standalone |
| `ProviderCode` | `ProviderCode` | Standalone |
| `ProviderMap` | `ProviderMap` | @ManyToMany collectionCodes/institutionCodes |
| `AuditLogEvent` | `AuditLogEvent` | From audit-logging plugin |
| `ActivityLog` | `ActivityLog` | Custom audit log |
| `Sequence` | `Sequence` | UID sequence table |
| `ExternalResourceBean` (mapWith='none') | `ExternalResourceBean` (DTO) | Not persisted |
| `DataSourceConfiguration` (mapWith='none') | `DataSourceConfiguration` (DTO) | Not persisted |

## Type Mapping

| Groovy/GORM | Java/JPA | Notes |
|-------------|----------|-------|
| `Double latitude/longitude` | `Double latitude/longitude` | Nullable, no sentinel |
| `java.sql.Timestamp` | `LocalDateTime` | PostgreSQL compatible |
| `String` with `type:'text'` | `@Column(columnDefinition="text")` | |
| `BigDecimal` coordinates | `BigDecimal` (Collection bounds) | Kept for precision |
| `Boolean` fields | `Boolean` (wrapper) | Lombok: `getIsPrivate()` not `isPrivate()` |
| `embedded = ['address']` | `@Embedded @AttributeOverrides` | Column name prefixes |
| `hasMany` (no belongsTo) | `@ManyToMany @JoinTable` | Separate join table per entity |
| `hasMany` (with belongsTo) | `@OneToMany(mappedBy=...)` | FK on child |
| `belongsTo` | `@ManyToOne @JoinColumn` | |
| `hasOne` | `@OneToOne(mappedBy=...)` | |
| `transients = [...]` | `@Transient` | Computed properties |

## Service Migration Map

| Groovy Service | Java Service | Lines (Groovy→Java) | Key Changes |
|---------------|-------------|---------------------|-------------|
| `CrudService` | `CrudService` | ~800→~730 | Jackson ObjectMapper replaces JSONBuilder |
| `ProviderGroupService` | `ProviderGroupService` | ~400→~350 | Repository injection replaces GORM calls |
| `CollectoryAuthService` | `CollectoryAuthService` | ~200→~180 | Principal-based auth |
| `IdGeneratorService` | `IdGeneratorService` | ~80→~70 | REQUIRES_NEW propagation preserved |
| `MetadataService` | `MetadataService` | ~300→~250 | JSON profile loading |
| `EmlImportService` | `EmlImportService` | 383→~330 | DOM (DocumentBuilder) replaces XmlSlurper |
| `EmlRenderService` | `EmlRenderService` | 758→~600 | StAX (XMLStreamWriter) replaces StreamingMarkupBuilder |
| `GbifService` | `GbifService` | 586→~500 | RestTemplate replaces Apache HttpClient |
| `GbifRegistryService` | `GbifRegistryService` | 1167→~1100 | RestTemplate with Basic auth interceptor |
| `IptService` | `IptService` | 283→~280 | DOM XML parsing, DateTimeFormatter |
| `ExternalDataService` | `ExternalDataService` | 299→~280 | ConcurrentHashMap, ExecutorService |
| `DataImportService` | `DataImportService` | 186→~170 | ZipFile + DocumentBuilder |
| `RifCsService` | `RifCsService` | 77→~70 | @Cacheable |
| `SitemapService` | `SitemapService` | 134→~120 | @Scheduled, @ConditionalOnProperty |

## Controller Endpoint Mapping

| Grails URL | HTTP Method | Spring Boot Controller | Method |
|-----------|-------------|----------------------|--------|
| `/ws/{entity}` | GET | DataController | list() |
| `/ws/{entity}/{uid}` | GET | DataController | show() |
| `/ws/{entity}` | POST | DataController | create() |
| `/ws/{entity}/{uid}` | PUT | DataController | update() |
| `/ws/{entity}/{uid}` | DELETE | DataController | delete() |
| `/ws/{entity}/count` | GET | DataController | count() |
| `/ws/find/{entity}` | GET | DataController | find() |
| `/ws/{entity}/{uid}/contacts` | GET | DataController | contacts() |
| `/ws/{entity}/{uid}/contacts` | POST | DataController | addContact() |
| `/ws/{entity}/{uid}/contacts/{contactId}` | PUT | DataController | updateContact() |
| `/ws/{entity}/{uid}/contacts/{contactId}` | DELETE | DataController | deleteContact() |
| `/ws/dataResource/{uid}/connectionParameters` | POST | DataController | updateConnectionParameters() |
| `/ws/lookup/inst/{instCode}` | GET | LookupController | lookupInst() |
| `/ws/lookup/collection/{instCode}/{collCode}` | GET | LookupController | lookupCollection() |
| `/ws/lookup/name/{name}` | GET | LookupController | lookupName() |
| `/ws/lookup/entity/{uid}` | GET | LookupController | lookupEntity() |
| `/ws/resolveNames/{uids}` | GET | LookupController | resolveNames() |
| `/ws/codeMapDump` | GET | LookupController | codeMapDump() |
| `/ws/citations` | POST | LookupController | citations() |
| `/ws/downloadLimits` | GET | LookupController | downloadLimits() |
| `/public/mapFeatures` | GET | PublicController | mapFeatures() |
| `/public/condensed` | GET | PublicController | condensed() |
| `/feed` | GET | DataFeedsController | feed() |
| `/rif-cs` | GET | DataFeedsController | rifCs() |
| `/ws/eml/{id}` | GET | DataFeedsController | eml() |
| `/ws/manage/list` | GET | ManageController | list() |
| `/ws/manage/show/{uid}` | GET | ManageController | show() |
| `/ws/reports/*` | GET | ReportsController | various |
| `/ws/admin/export` | GET | AdminController | export() |
| `/ws/admin/connectionProfiles` | GET | AdminController | connectionProfiles() |
| `/ws/gbif/*` | various | GbifController | various |
| `/ws/ipt/scan/{uid}` | POST | IptController | scan() |
| `/sitemap*.xml` | GET | SitemapController | sitemap() |
| `/ws/config` | GET | ConfigController | config() |

## Groovy → Java Pattern Translations

| Groovy Pattern | Java Equivalent |
|---------------|----------------|
| `def x = [:]` | `Map<String, Object> x = new LinkedHashMap<>()` |
| `render x as JSON` | Return object from @RestController (Jackson auto-serializes) |
| `params.uid` | `@PathVariable String uid` |
| `params.int('max', 10)` | `@RequestParam(defaultValue = "10") int max` |
| `grailsApplication.config.x` | `appProperties.getX()` |
| `Entity.findByUid(uid)` | `repository.findByUid(uid)` |
| `entity.save(flush: true)` | `repository.saveAndFlush(entity)` |
| `entity.delete()` | `repository.delete(entity)` |
| `Entity.withTransaction { }` | `@Transactional` on service method |
| `new JsonSlurper().parseText(s)` | `objectMapper.readValue(s, Map.class)` |
| `new JsonBuilder(map).toString()` | `objectMapper.writeValueAsString(map)` |
| `new XmlSlurper().parse(stream)` | `DocumentBuilderFactory...parse(stream)` |
| `new StreamingMarkupBuilder()` | `XMLOutputFactory...createXMLStreamWriter()` |
| `messageSource.getMessage(key, args, locale)` | Same (Spring MessageSource) |
| `@DelegateAsync` | `@Async` on method |
| `@grails.gorm.transactions.Transactional` | `@org.springframework.transaction.annotation.Transactional` |
