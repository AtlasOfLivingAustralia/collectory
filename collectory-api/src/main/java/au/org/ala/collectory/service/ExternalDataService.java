package au.org.ala.collectory.service;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.dto.*;
import au.org.ala.collectory.exception.ExternalResourceException;
import au.org.ala.collectory.repository.*;
import au.org.ala.collectory.resources.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Services required to collect data from various external sources.
 * <p>
 * Manages loading data from external sources (GBIF, etc.) using the DataSourceAdapter pattern.
 */
@Service
@Slf4j
public class ExternalDataService {

    private final CrudService crudService;
    private final ExternalIdentifierService externalIdentifierService;
    private final AppProperties appProperties;
    private final DataResourceRepository dataResourceRepository;
    private final ObjectMapper objectMapper;

    private static final long POLL_INTERVAL = 15000L;
    private static final DateTimeFormatter ALA_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int CONCURRENT_LOADS = 3;

    private final ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_LOADS);
    private final Map<String, DataSourceLoad> loadMap = new ConcurrentHashMap<>();

    public ExternalDataService(CrudService crudService,
                               ExternalIdentifierService externalIdentifierService,
                               AppProperties appProperties,
                               DataResourceRepository dataResourceRepository,
                               ObjectMapper objectMapper) {
        this.crudService = crudService;
        this.externalIdentifierService = externalIdentifierService;
        this.appProperties = appProperties;
        this.dataResourceRepository = dataResourceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the status information for a specific load.
     *
     * @param guid The load guid
     * @return The DataSourceLoad, or null if not found
     */
    public DataSourceLoad getStatusInfoFor(String guid) {
        return loadMap.get(guid);
    }

    /**
     * Look for some external resources.
     *
     * @param configuration The data source configuration
     * @return A sorted list of external resource descriptions
     */
    public List<ExternalResourceBean> searchForDatasets(DataSourceConfiguration configuration) {
        DataSourceAdapter adaptor = configuration.createAdaptor();
        List<Map<String, Object>> datasets = adaptor.datasets();
        List<ExternalResourceBean> resources = datasets.stream()
                .map(adaptor::createExternalResource)
                .collect(Collectors.toList());
        Collections.sort(resources);
        return resources;
    }

    /**
     * Update the collectory with data from external resources.
     *
     * @param configuration The data source configuration
     * @param loadGuid      The unique identifier for this load operation
     */
    public void updateFromExternalSources(DataSourceConfiguration configuration, String loadGuid) {
        DataSourceLoad load = new DataSourceLoad();
        load.setGuid(loadGuid);
        load.setStartTime(new Date());
        load.setConfiguration(configuration);

        DataSourceAdapter adaptor = load.getConfiguration().createAdaptor();
        loadMap.put(loadGuid, load);

        // Sequential metadata processing to avoid transaction race conditions
        for (ExternalResourceBean resource : load.getResources()) {
            resource.setPhase(TaskPhase.NEW);
            processResourceMetadata(adaptor, load, resource);
        }

        // Submit non-terminal resources for async processing
        for (ExternalResourceBean resource : load.getResources()) {
            if (!resource.getPhase().isTerminal()) {
                resource.setPhase(TaskPhase.QUEUED);
                pool.submit(() -> processResource(load, resource));
            }
        }

        // Submit load completion watcher
        pool.submit(() -> processLoad(load));
    }

    /**
     * Process that waits on a data load for completion.
     *
     * @param load The load to process
     */
    void processLoad(DataSourceLoad load) {
        try {
            while (!load.isComplete()) {
                try {
                    Thread.sleep(POLL_INTERVAL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            load.setFinishTime(new Date());
            try {
                // Keep the load info available for a short time for status polling
                Thread.sleep(POLL_INTERVAL * 4);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } finally {
            loadMap.remove(load.getGuid());
        }
    }

    /**
     * Update any metadata on the resource.
     * <p>
     * Done sequentially to avoid transaction problems leading to race conditions.
     *
     * @param adaptor  The data source adapter
     * @param load     The current load
     * @param resource The external resource bean
     */
    @Transactional
    public void processResourceMetadata(DataSourceAdapter adaptor, DataSourceLoad load,
                                        ExternalResourceBean resource) {
        try {
            if (resource.getPhase().isTerminal()) return; // Cancelled externally

            resource.setPhase(TaskPhase.METADATA);
            DataResource dr = null;
            ExternalIdentifier ext = null;

            if (resource.getUid() != null) {
                dr = dataResourceRepository.findByUid(resource.getUid()).orElse(null);
                if (dr == null) {
                    throw new ExternalResourceException("Can't find resource",
                            "manage.note.note12", resource.getUid());
                }

                // Check if external identifier already exists
                if (dr.getExternalIdentifiers() != null) {
                    ext = dr.getExternalIdentifiers().stream()
                            .filter(ei -> adaptor.getSource().equals(ei.getSource())
                                    && resource.getGuid().equals(ei.getIdentifier()))
                            .findFirst()
                            .orElse(null);
                }

                if (ext == null) {
                    externalIdentifierService.addExternalIdentifier(
                            dr.getUid(), resource.getGuid(), adaptor.getSource(), resource.getSource());
                }
            }

            if (!resource.isUpdateRequired()) {
                resource.setPhase((dr != null && ext == null) ? TaskPhase.COMPLETED : TaskPhase.IGNORED);
                return;
            }

            Map<String, Object> update = adaptor.getDataset(resource.getGuid());
            if (load.getConfiguration().getDataProviderUid() != null) {
                update.put("dataProvider", Map.of("uid", load.getConfiguration().getDataProviderUid()));
            }
            if (load.getConfiguration().getCountry() != null) {
                update.put("repatriationCountry", load.getConfiguration().getCountry());
            }

            Map<String, Object> jsonUpdate = convertToMap(update);

            if (dr != null && jsonUpdate != null && Boolean.TRUE.equals(resource.getUpdateMetadata())) {
                crudService.updateDataResource(dr, jsonUpdate);
            }

            if (dr == null && Boolean.TRUE.equals(resource.getAddResource())) {
                dr = crudService.insertDataResource(jsonUpdate);
                if (dr == null) {
                    throw new ExternalResourceException("Can't create resource",
                            "manage.note.note01", resource.getName(), resource.getGuid());
                }
                externalIdentifierService.addExternalIdentifier(
                        dr.getUid(), resource.getGuid(), adaptor.getSource(), resource.getSource());
                resource.setUid(dr.getUid());
                resource.addNote("manage.note.note03", resource.getUid());
            }

            if (dr == null || !Boolean.TRUE.equals(resource.getUpdateConnection())) {
                resource.setPhase(TaskPhase.COMPLETED);
            }
        } catch (ExternalResourceException ex) {
            log.error("Unable to process resource {} {}", resource, ex.getClass().getName(), ex);
            resource.addError(ex.getCode(), ex.getArgs());
        } catch (Exception ex) {
            log.error("Unable to process resource {} {}", resource, ex.getClass().getName(), ex);
            resource.addError("manage.note.note05",
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName());
        }
    }

    /**
     * Load a single external resource asynchronously.
     *
     * @param load     The load process
     * @param resource The resource to load
     */
    void processResource(DataSourceLoad load, ExternalResourceBean resource) {
        try {
            if (resource.getPhase().isTerminal()) return; // Cancelled externally

            // Allow transaction to complete
            Thread.sleep(100);

            DataSourceAdapter adaptor = load.getConfiguration().createAdaptor();
            DataResource dr = dataResourceRepository.findByUid(resource.getUid()).orElse(null);

            if (dr == null) {
                throw new ExternalResourceException("Can't find resource",
                        "manage.note.note12", resource.getUid());
            }

            if (!Boolean.TRUE.equals(resource.getUpdateConnection())) {
                resource.setPhase(TaskPhase.COMPLETED);
                return;
            }

            // Check if data is available
            boolean hasData = adaptor.isDataAvailableForResource(resource.getGuid());
            if (!hasData) {
                resource.setPhase(TaskPhase.EMPTY);
                return;
            }
            if (resource.getPhase().isTerminal()) return; // Cancelled externally

            // Generate data if adapter supports it
            if (adaptor.isGeneratable()) {
                resource.setPhase(TaskPhase.GENERATING);
                resource.setOccurrenceId(adaptor.generateData(resource.getGuid(), resource.getCountry()));
                if (resource.getPhase().isTerminal()) return;

                TaskPhase status = TaskPhase.GENERATING;
                while (!status.isTerminal() && !resource.getPhase().isTerminal()) {
                    status = adaptor.generateStatus(resource.getOccurrenceId());
                    if (!status.isTerminal()) {
                        Thread.sleep(POLL_INTERVAL);
                    }
                }
                if (resource.getPhase().isTerminal()) return;
                if (status != TaskPhase.COMPLETED) {
                    throw new ExternalResourceException("Unable to generate occurrence data",
                            "manage.note.note04", status);
                }
            }

            // Download data if adapter supports it
            File uploadFileName = null;
            if (adaptor.isDownloadable()) {
                resource.setPhase(TaskPhase.DOWNLOADING);
                File uploadDir = new File(appProperties.getUploadFilePath());
                File uploadTmpDir = new File(new File(uploadDir, "tmp"), resource.getOccurrenceId());
                FileUtils.forceMkdir(uploadTmpDir);
                File tmpFileName = new File(uploadTmpDir, resource.getOccurrenceId());
                adaptor.downloadData(resource.getOccurrenceId(), tmpFileName);
                if (resource.getPhase().isTerminal()) return;

                resource.setPhase(TaskPhase.PROCESSING);
                String fileId = Long.toString(System.currentTimeMillis());
                File uploadWorkDir = new File(uploadDir, fileId);
                FileUtils.forceMkdir(uploadWorkDir);
                uploadFileName = adaptor.processData(tmpFileName, uploadWorkDir, resource);
                if (resource.getPhase().isTerminal()) return;
            }

            // Connect the resource
            resource.setPhase(TaskPhase.CONNECITNG);
            Object connection = parseConnectionParameters(dr.getConnectionParameters());
            Object builtConnection = adaptor.buildConnection(uploadFileName, connection, resource);
            Map<String, Object> updateMap = convertToMap(builtConnection);
            crudService.updateDataResource(dr, updateMap);

            resource.setPhase(TaskPhase.COMPLETED);
        } catch (ExternalResourceException ex) {
            log.error("Unable to process resource {} {}", resource, ex.getClass().getName(), ex);
            resource.addError(ex.getCode(), ex.getArgs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Processing interrupted for resource {}", resource, ex);
            resource.addError("manage.note.note05", "Processing interrupted");
        } catch (Exception ex) {
            log.error("Unable to process resource {} {}", resource, ex.getClass().getName(), ex);
            resource.addError("manage.note.note05",
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName());
        }
    }

    /**
     * Parse connection parameters JSON string into a Map.
     */
    @SuppressWarnings("unchecked")
    private Object parseConnectionParameters(String connectionParameters) {
        if (connectionParameters == null || connectionParameters.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(connectionParameters, Map.class);
        } catch (Exception e) {
            log.warn("Could not parse connection parameters: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Deep convert of an object to a Map suitable for the CrudService.
     * <p>
     * In Spring Boot with Jackson, this replaces the Grails JSONObject conversion.
     *
     * @param o The object to convert
     * @return The converted map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> convertToMap(Object o) {
        if (o == null) return null;

        if (o instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            ((Map<?, ?>) o).forEach((key, value) -> {
                result.put(String.valueOf(key), convertValue(value));
            });
            return result;
        }

        // Try Jackson conversion for other object types
        try {
            return objectMapper.convertValue(o, Map.class);
        } catch (Exception e) {
            log.warn("Could not convert object to map: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Recursively convert values for JSON compatibility.
     */
    @SuppressWarnings("unchecked")
    private Object convertValue(Object o) {
        if (o == null) return null;

        if (o instanceof Map) {
            return convertToMap(o);
        }
        if (o instanceof java.util.Collection) {
            List<Object> list = new ArrayList<>();
            for (Object item : (java.util.Collection<?>) o) {
                list.add(convertValue(item));
            }
            return list;
        }
        if (o instanceof Date) {
            return ALA_TIMESTAMP_FORMAT.format(
                    ((Date) o).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return o;
    }
}
