package au.org.ala.collectory.service;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class ProviderGroupService {

    private final CollectionRepository collectionRepository;
    private final InstitutionRepository institutionRepository;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;
    private final DataHubRepository dataHubRepository;
    private final ContactForRepository contactForRepository;
    private final ExternalIdentifierRepository externalIdentifierRepository;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Autowired @Lazy
    private CollectoryAuthService collectoryAuthService;

    @Autowired
    private MessageSource messageSource;

    public ProviderGroupService(
            CollectionRepository collectionRepository,
            InstitutionRepository institutionRepository,
            DataProviderRepository dataProviderRepository,
            DataResourceRepository dataResourceRepository,
            DataHubRepository dataHubRepository,
            ContactForRepository contactForRepository,
            ExternalIdentifierRepository externalIdentifierRepository,
            AppProperties appProperties,
            ObjectMapper objectMapper) {
        this.collectionRepository = collectionRepository;
        this.institutionRepository = institutionRepository;
        this.dataProviderRepository = dataProviderRepository;
        this.dataResourceRepository = dataResourceRepository;
        this.dataHubRepository = dataHubRepository;
        this.contactForRepository = contactForRepository;
        this.externalIdentifierRepository = externalIdentifierRepository;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public String urlFormOfEntityType(String entityType) {
        if (entityType == null || entityType.isEmpty()) return "";
        return entityType.substring(0, 1).toLowerCase() + entityType.substring(1);
    }

    public String urlFormFromUid(String uid) {
        return urlFormOfEntityType(entityTypeFromUid(uid));
    }

    public String entityTypeFromUid(String uid) {
        if (uid == null || uid.length() < 2) return "";
        String prefix = uid.substring(0, 2);
        return switch (prefix) {
            case "in" -> Institution.ENTITY_TYPE;
            case "co" -> au.org.ala.collectory.domain.Collection.ENTITY_TYPE;
            case "dp" -> DataProvider.ENTITY_TYPE;
            case "dr" -> DataResource.ENTITY_TYPE;
            case "dh" -> DataHub.ENTITY_TYPE;
            default -> "";
        };
    }

    public String textFormOfEntityType(String uid) {
        String entityType = entityTypeFromUid(uid);
        StringBuilder result = new StringBuilder();
        for (char c : entityType.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (!result.isEmpty()) result.append(" ");
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public ProviderGroup get(String uid) {
        if (uid == null || uid.length() < 3) return null;
        String prefix = uid.startsWith("drt") ? "drt" : uid.substring(0, 2);
        return switch (prefix) {
            case "in" -> institutionRepository.findByUid(uid).orElse(null);
            case "co" -> collectionRepository.findByUid(uid).orElse(null);
            case "dp" -> dataProviderRepository.findByUid(uid).orElse(null);
            case "dr" -> dataResourceRepository.findByUid(uid).orElse(null);
            case "dh" -> dataHubRepository.findByUid(uid).orElse(null);
            default -> null;
        };
    }

    public ProviderGroup getEager(String uid) {
        if (uid == null || uid.length() < 3) return null;
        String prefix = uid.substring(0, 2);
        return switch (prefix) {
            case "in" -> institutionRepository.findWithCollectionsByUid(uid).orElse(null);
            case "co" -> collectionRepository.findByUid(uid).orElse(null);
            case "dp" -> dataProviderRepository.findByUid(uid).orElse(null);
            case "dr" -> dataResourceRepository.findByUid(uid).orElse(null);
            case "dh" -> dataHubRepository.findByUid(uid).orElse(null);
            default -> null;
        };
    }

    public ProviderGroup getByIdAndType(String id, String entityType) {
        try {
            return switch (entityType.toLowerCase()) {
                case "institution" -> institutionRepository.findById(Long.parseLong(id)).orElse(null);
                case "collection" -> collectionRepository.findById(Long.parseLong(id)).orElse(null);
                case "dataprovider" -> dataProviderRepository.findById(Long.parseLong(id)).orElse(null);
                case "dataresource" -> dataResourceRepository.findById(Long.parseLong(id)).orElse(null);
                case "datahub" -> dataHubRepository.findById(Long.parseLong(id)).orElse(null);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    public List<ProviderGroup> getContactsFor(Contact contact) {
        List<ProviderGroup> result = new ArrayList<>();
        contactForRepository.findAllByContact(contact).forEach(cf -> {
            ProviderGroup pg = get(cf.getEntityUid());
            if (pg != null) {
                result.add(pg);
            }
        });
        return result;
    }

    public boolean isAuthorisedToEdit(String uid) {
        if (!appProperties.getSecurity().isOidcEnabled() || collectoryAuthService.isAdmin()) {
            return true;
        }
        String email = collectoryAuthService.userEmail();
        if (email != null) {
            Map<String, Object> authorised = collectoryAuthService.authorisedForUser(email);
            @SuppressWarnings("unchecked")
            List<String> keys = (List<String>) authorised.get("keys");
            return keys != null && keys.contains(uid);
        }
        return false;
    }

    @Transactional
    public void updateExternalIdentifiers(ProviderGroup pg, List<Map<String, String>> newIdentifiers) {
        if (newIdentifiers == null) return;

        List<ExternalIdentifier> updates = newIdentifiers.stream()
                .filter(ext -> ext.get("source") != null && ext.get("identifier") != null)
                .map(ext -> ExternalIdentifier.builder()
                        .entityUid(pg.getUid())
                        .source(ext.get("source"))
                        .identifier(ext.get("identifier"))
                        .uri(ext.get("uri"))
                        .build())
                .collect(Collectors.toList());

        // Get existing external identifiers by entityUid
        List<ExternalIdentifier> existing = externalIdentifierRepository.findAllByEntityUid(pg.getUid());
        List<ExternalIdentifier> toDelete = new ArrayList<>(existing);

        for (ExternalIdentifier update : updates) {
            ExternalIdentifier found = toDelete.stream()
                    .filter(e -> e.getSource().equals(update.getSource())
                            && e.getIdentifier().equals(update.getIdentifier()))
                    .findFirst()
                    .orElse(null);

            if (found != null) {
                found.setUri(update.getUri());
                externalIdentifierRepository.save(found);
                toDelete.remove(found);
            } else {
                externalIdentifierRepository.save(update);
            }
        }

        // Delete remaining unmatched
        for (ExternalIdentifier old : toDelete) {
            externalIdentifierRepository.delete(old);
        }
    }

    public String toJson(Object param) {
        if (param == null) return "";
        try {
            if (param instanceof String s) {
                return objectMapper.writeValueAsString(List.of(s));
            }
            return objectMapper.writeValueAsString(param);
        } catch (JsonProcessingException e) {
            log.error("Error converting to JSON", e);
            return "";
        }
    }

    public Address resolveAddress(ProviderGroup pg) {
        Address addr = pg.getAddress();
        if (addr != null) return addr;
        // Try to resolve from consumer relationships for DataProvider/DataResource
        if (pg instanceof DataProvider dp) {
            if (dp.getConsumerInstitutions() != null) {
                for (ProviderGroup consumer : dp.getConsumerInstitutions()) {
                    if (consumer.getAddress() != null) return consumer.getAddress();
                }
            }
            if (dp.getConsumerCollections() != null) {
                for (ProviderGroup consumer : dp.getConsumerCollections()) {
                    if (consumer.getAddress() != null) return consumer.getAddress();
                }
            }
        } else if (pg instanceof DataResource dr) {
            if (dr.getConsumerInstitutions() != null) {
                for (ProviderGroup consumer : dr.getConsumerInstitutions()) {
                    if (consumer.getAddress() != null) return consumer.getAddress();
                }
            }
            if (dr.getConsumerCollections() != null) {
                for (ProviderGroup consumer : dr.getConsumerCollections()) {
                    if (consumer.getAddress() != null) return consumer.getAddress();
                }
            }
        }
        return null;
    }

    @Transactional
    public ProviderGroup saveEntity(ProviderGroup pg) {
        pg.setUserLastModified(collectoryAuthService.username());
        if (pg instanceof Institution inst) {
            return institutionRepository.saveAndFlush(inst);
        } else if (pg instanceof au.org.ala.collectory.domain.Collection co) {
            return collectionRepository.saveAndFlush(co);
        } else if (pg instanceof DataProvider dp) {
            return dataProviderRepository.saveAndFlush(dp);
        } else if (pg instanceof DataResource dr) {
            return dataResourceRepository.saveAndFlush(dr);
        } else if (pg instanceof DataHub dh) {
            return dataHubRepository.saveAndFlush(dh);
        }
        throw new IllegalArgumentException("Unknown entity type: " + pg.getClass().getSimpleName());
    }

    @Transactional
    public void deleteEntity(ProviderGroup pg) {
        // Delete contact associations first
        List<ContactFor> contactFors = contactForRepository.findAllByEntityUid(pg.getUid());
        contactForRepository.deleteAll(contactFors);
        // Delete external identifiers
        List<ExternalIdentifier> extIds = externalIdentifierRepository.findAllByEntityUid(pg.getUid());
        externalIdentifierRepository.deleteAll(extIds);

        if (pg instanceof Institution inst) {
            institutionRepository.delete(inst);
        } else if (pg instanceof au.org.ala.collectory.domain.Collection co) {
            collectionRepository.delete(co);
        } else if (pg instanceof DataProvider dp) {
            dataProviderRepository.delete(dp);
        } else if (pg instanceof DataResource dr) {
            dataResourceRepository.delete(dr);
        } else if (pg instanceof DataHub dh) {
            dataHubRepository.delete(dh);
        }
    }

    public long countEntities(String entityType) {
        return switch (entityType.toLowerCase()) {
            case "institution" -> institutionRepository.count();
            case "collection" -> collectionRepository.count();
            case "dataprovider" -> dataProviderRepository.count();
            case "dataresource" -> dataResourceRepository.count();
            case "datahub" -> dataHubRepository.count();
            default -> 0;
        };
    }

    @SuppressWarnings("unchecked")
    public <T extends ProviderGroup> List<T> listEntities(String entityType) {
        return switch (entityType.toLowerCase()) {
            case "institution" -> (List<T>) institutionRepository.findAll();
            case "collection" -> (List<T>) collectionRepository.findAll();
            case "dataprovider" -> (List<T>) dataProviderRepository.findAll();
            case "dataresource" -> (List<T>) dataResourceRepository.findAll();
            case "datahub" -> (List<T>) dataHubRepository.findAll();
            default -> List.of();
        };
    }

    /**
     * L1: Returns the suitableFor lookup map.
     * Keys are stored codes; values are human-readable labels resolved from i18n messages.
     * Mirrors Grails getSuitableFor() which reads an ordered config array and resolves each
     * key via messageSource ("dataresource.suitablefor.<key>").
     */
    public Map<String, String> getSuitableFor() {
        // Ordered list of suitableFor keys (from Grails config/i18n)
        List<String> keys = List.of(
                "national", "state", "largerThan1000", "100to1000",
                "10to100", "lessThan10", "speciesDistribution",
                "quantifyAtTime", "quantifyOverTime", "other"
        );
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : keys) {
            String label = messageSource.getMessage(
                    "dataresource.suitablefor." + key, null, key, java.util.Locale.getDefault());
            result.put(key, label);
        }
        return result;
    }
}
