package au.org.ala.collectory.resources.gbif;

import au.org.ala.collectory.config.ApplicationContextHolder;
import au.org.ala.collectory.dto.DataSourceConfiguration;
import au.org.ala.collectory.dto.ExternalResourceBean;
import au.org.ala.collectory.exception.ExternalResourceException;
import au.org.ala.collectory.resources.DataSourceAdapter;
import au.org.ala.collectory.resources.TaskPhase;
import au.org.ala.collectory.service.GbifService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Data source adapter for the GBIF API.
 *
 * @author Doug Palmer &lt;Doug.Palmer@csiro.au&gt;
 * @copyright Copyright (c) 2017 CSIRO
 */
public class GbifDataSourceAdapter extends DataSourceAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GbifDataSourceAdapter.class);

    public static final String SOURCE = "GBIF";

    static final MessageFormat DATASET_SEARCH =
            new MessageFormat("dataset/search?publishingCountry={0}&type={1}&offset={2}&limit={3}");
    static final MessageFormat DATASET_SEARCH_PROV =
            new MessageFormat("dataset/search?publishingCountry={0}&type={1}&offset={2}&limit={3}");
    static final MessageFormat DATASET_SEARCH_PROV_WITH_ORG =
            new MessageFormat("dataset/search?publishingCountry={0}&type={1}&offset={2}&limit={3}&publishingOrg={4}");

    static final MessageFormat DATASET_GET =
            new MessageFormat("dataset/{0}");
    static final MessageFormat DATASET_RECORD_COUNT =
            new MessageFormat("occurrence/count?datasetKey={0}");
    static final MessageFormat DOWNLOAD_STATUS =
            new MessageFormat("occurrence/download/{0}");

    static final DateFormat TIMESTAMP_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    /** Maps Creative Commons licence URLs to {licenseType, licenseVersion}. */
    @SuppressWarnings("serial")
    public static final Map<String, Map<String, String>> LICENSE_MAP = Map.ofEntries(
            Map.entry("https://creativecommons.org/publicdomain/zero/1.0/legalcode",
                    Map.of("licenseType", "CC0", "licenseVersion", "1.0")),
            Map.entry("https://creativecommons.org/licenses/by-nc/4.0/legalcode",
                    Map.of("licenseType", "CC BY-NC", "licenseVersion", "4.0")),
            Map.entry("https://creativecommons.org/licenses/by/4.0/legalcode",
                    Map.of("licenseType", "CC BY", "licenseVersion", "4.0")),
            Map.entry("http://creativecommons.org/publicdomain/zero/1.0/legalcode",
                    Map.of("licenseType", "CC0", "licenseVersion", "1.0")),
            Map.entry("http://creativecommons.org/licenses/by-nc/4.0/legalcode",
                    Map.of("licenseType", "CC BY-NC", "licenseVersion", "4.0")),
            Map.entry("http://creativecommons.org/licenses/by/4.0/legalcode",
                    Map.of("licenseType", "CC BY", "licenseVersion", "4.0"))
    );

    /** Maps GBIF dataset types to ALA resource types. */
    public static final Map<String, String> TYPE_MAP = Map.of(
            "CHECKLIST", "species-list",
            "METADATA", "document",
            "OCCURRENCE", "records",
            "SAPLING_EVENT", "records"
    );

    /** Maps GBIF dataset types to display names (currently only occurrence records). */
    public static final Map<String, String> DATASET_TYPES = Map.of(
            "OCCURRENCE", "Occurrence Records"
    );

    /** Maps GBIF dataset types to content type lists. */
    @SuppressWarnings("serial")
    public static final Map<String, List<String>> CONTENT_MAP = Map.of(
            "CHECKLIST", List.of("species list", "taxonomy", "gbif import"),
            "METADATA", List.of("gbif import"),
            "OCCURRENCE", List.of("point occurrence data", "gbif import"),
            "SAPLING_EVENT", List.of("point occurrence data", "gbif import")
    );

    /** Maps GBIF download statuses to TaskPhase values. */
    public static final Map<String, TaskPhase> DOWNLOAD_STATUS_MAP = Map.of(
            "CANCELLED", TaskPhase.CANCELLED,
            "FAILED", TaskPhase.ERROR,
            "KILLED", TaskPhase.CANCELLED,
            "PREPARING", TaskPhase.GENERATING,
            "RUNNING", TaskPhase.GENERATING,
            "SUCCEEDED", TaskPhase.COMPLETED,
            "SUSPENDED", TaskPhase.GENERATING,
            "UNKNOWN", TaskPhase.ERROR
    );

    protected int pageSize = 500;

    public GbifDataSourceAdapter(DataSourceConfiguration configuration) {
        super(configuration);
    }

    // ---- Lazy service lookup ----

    protected GbifService getGbifService() {
        return ApplicationContextHolder.getBean(GbifService.class);
    }

    // ---- DataSourceAdapter implementation ----

    @Override
    public String getSource() {
        return SOURCE;
    }

    @Override
    public Map<String, String> getCountryMap() {
        return getGbifService().getCountryMap();
    }

    @Override
    public Map<String, String> getDatasetTypeMap() {
        return DATASET_TYPES;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> datasets() throws ExternalResourceException {
        int offset = 0;
        List<Map<String, Object>> datasets = new ArrayList<>();
        boolean atEnd = false;

        int pageSizeToUse = (configuration.getMaxNoOfDatasets() != null
                && configuration.getMaxNoOfDatasets() < pageSize)
                ? configuration.getMaxNoOfDatasets() : pageSize;

        while (!atEnd) {
            LOGGER.info("Requesting dataset lists configuration.country: {} offset: {}, pageSize: {}, dataProvider: {}",
                    configuration.getCountry(), offset, pageSizeToUse, configuration.getDataProviderUid());

            String targetUrl = DATASET_SEARCH.format(new Object[]{
                    configuration.getCountry(),
                    configuration.getRecordType(),
                    String.valueOf(offset),
                    String.valueOf(pageSizeToUse)
            });

            Map<String, Object> json = getJSONWS(targetUrl);

            List<Map<String, Object>> results = json != null
                    ? (List<Map<String, Object>>) json.get("results") : null;

            if (results != null) {
                for (Map<String, Object> result : results) {
                    datasets.add(translate(result));
                }
            }

            offset += pageSize;

            int resultsSize = results != null ? results.size() : 0;
            LOGGER.info("Results: {}", resultsSize);
            if (resultsSize > 0) {
                LOGGER.info("Results: {}", results.get(0).get("title"));
            }

            Boolean endOfRecords = json != null ? (Boolean) json.get("endOfRecords") : null;
            atEnd = json == null
                    || results == null
                    || resultsSize == 0
                    || Boolean.TRUE.equals(endOfRecords)
                    || (configuration.getMaxNoOfDatasets() != null && offset > configuration.getMaxNoOfDatasets());
        }

        LOGGER.info("Total datasets retrieved: {}", datasets.size());
        return datasets;
    }

    @Override
    public ExternalResourceBean createExternalResource(Map<String, Object> external) {
        ExternalResourceBean ext = new ExternalResourceBean();
        ext.setName((String) external.get("name"));
        ext.setGuid((String) external.get("guid"));
        ext.setSource((String) external.get("source"));
        ext.setSourceUpdated((Date) external.get("dataCurrency"));

        // In the Grails version, resolve() checked DataResource by guid.
        // In Spring Boot, the adapter is not Spring-managed so we skip the
        // DataResource lookup here. The calling service (ExternalDataService)
        // will resolve against existing resources when processing the list.
        ext.setStatus(ExternalResourceBean.ResourceStatus.NEW);
        ext.setAddResource(true);
        ext.setUpdateMetadata(false);
        ext.setUpdateConnection(true);

        return ext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDataset(String id) throws ExternalResourceException {
        Map<String, Object> json = getJSONWS(DATASET_GET.format(new Object[]{id}), false);
        return json != null ? translate(json) : null;
    }

    /**
     * Translates a GBIF dataset JSON map into an ALA-format resource map.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> translate(Map<String, Object> dataset) {
        Date currency = null;

        // Find the ORIGINATOR contact
        List<Map<String, Object>> contacts =
                (List<Map<String, Object>>) dataset.get("contacts");
        Map<String, Object> originator = null;
        if (contacts != null) {
            originator = contacts.stream()
                    .filter(c -> "ORIGINATOR".equals(c.get("type")))
                    .findFirst()
                    .orElse(null);
        }

        Map<String, String> address = null;
        String phone = null;
        String email = null;
        if (originator != null) {
            List<String> addressLines = (List<String>) originator.get("address");
            address = new LinkedHashMap<>();
            address.put("street", addressLines != null ? String.join(" ", addressLines) : null);
            address.put("city", (String) originator.get("city"));
            address.put("state", (String) originator.get("province"));
            address.put("postcode", (String) originator.get("postalCode"));
            address.put("country", (String) originator.get("country"));

            List<String> phones = (List<String>) originator.get("phone");
            phone = (phones != null && !phones.isEmpty()) ? phones.get(0) : null;

            List<String> emails = (List<String>) originator.get("email");
            email = (emails != null && !emails.isEmpty()) ? emails.get(0) : null;
        }

        String licenseUrl = (String) dataset.get("license");
        Map<String, String> license = LICENSE_MAP.get(licenseUrl);

        String datasetType = (String) dataset.get("type");
        String recordType = TYPE_MAP.get(datasetType);
        List<String> contentTypes = CONTENT_MAP.getOrDefault(datasetType, List.of());

        String doi = (String) dataset.get("doi");
        String source = doi != null ? doi.replace("doi:", "https://doi.org/") : null;

        String pubDate = (String) dataset.get("pubDate");
        if (pubDate != null) {
            try {
                synchronized (TIMESTAMP_FORMAT) {
                    currency = TIMESTAMP_FORMAT.parse(pubDate);
                }
            } catch (ParseException ex) {
                // Ignore parse errors
            }
        }

        String contentTypesJson;
        try {
            contentTypesJson = new ObjectMapper().writeValueAsString(contentTypes);
        } catch (Exception e) {
            contentTypesJson = "[]";
        }

        // Build citation
        Map<String, Object> citationMap = (Map<String, Object>) dataset.get("citation");
        String citation = null;
        if (citationMap != null) {
            citation = citationMap.get("identifier") != null
                    ? (String) citationMap.get("identifier")
                    : (String) citationMap.get("text");
        }

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("name", dataset.get("title"));
        resource.put("acronym", dataset.get("abbreviation"));
        resource.put("guid", dataset.get("key"));
        resource.put("address", address);
        resource.put("phone", phone);
        resource.put("email", email);
        resource.put("pubDescription", dataset.get("description"));
        resource.put("state", address != null ? address.get("state") : null);
        resource.put("websiteUrl", dataset.get("homepage"));
        resource.put("rights", dataset.get("rights"));
        resource.put("licenseType", license != null ? license.get("licenseType") : null);
        resource.put("licenseVersion", license != null ? license.get("licenseVersion") : null);
        resource.put("citation", citation);
        resource.put("resourceType", recordType);
        resource.put("contentTypes", contentTypesJson);
        resource.put("dataCurrency", currency);
        resource.put("lastChecked", new Date());
        resource.put("source", source);
        resource.put("gbifDoi", doi);
        resource.put("gbifRegistryKey", dataset.get("key"));
        resource.put("gbifDataset", true);
        resource.put("isShareableWithGBIF", false);

        addDefaultDatasetValues(resource);
        return resource;
    }

    // ---- HTTP helpers ----

    /**
     * Uses an HTTP GET to return the parsed JSON output of the supplied URL path.
     *
     * @param path         The request URL path, relative to the configuration endpoint
     * @param authRequired Whether HTTP Basic authentication is required
     * @return A parsed JSON map
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getJSONWS(String path, boolean authRequired) throws ExternalResourceException {
        try {
            URL url = new URL(configuration.getEndpoint(), path);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            if (authRequired) {
                String authString = configuration.getUsername() + ":" + configuration.getPassword();
                String encodedAuthString = "Basic " +
                        Base64.getEncoder().encodeToString(authString.getBytes());
                connection.setRequestProperty("Authorization", encodedAuthString);
            }

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return new ObjectMapper().readValue(response.toString(), Map.class);
                }
            } else {
                throw new ExternalResourceException(
                        "Unable to get " + url + " response " + connection.getResponseCode(),
                        "manage.note.note10", url.toString(), connection.getResponseCode());
            }
        } catch (ExternalResourceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalResourceException("Error calling GBIF API: " + path, e);
        }
    }

    /**
     * Convenience overload that defaults to auth required.
     */
    protected Map<String, Object> getJSONWS(String path) throws ExternalResourceException {
        return getJSONWS(path, true);
    }

    // ---- Data availability ----

    @Override
    public boolean isDataAvailableForResource(String guid) throws ExternalResourceException {
        try {
            URL url = new URL(configuration.getEndpoint(),
                    DATASET_RECORD_COUNT.format(new Object[]{guid}));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            String authString = configuration.getUsername() + ":" + configuration.getPassword();
            String encodedAuthString = "Basic " +
                    Base64.getEncoder().encodeToString(authString.getBytes());
            connection.setRequestProperty("Authorization", encodedAuthString);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String response = reader.readLine();
                    return response != null && !response.isEmpty() && Integer.parseInt(response.trim()) > 0;
                }
            } else {
                throw new ExternalResourceException(
                        "Unable check for data",
                        "manage.note.note11", guid, connection.getResponseCode());
            }
        } catch (ExternalResourceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalResourceException("Error checking data availability for " + guid, e);
        }
    }

    // ---- Data generation ----

    @Override
    public String generateData(String guid, String country) throws ExternalResourceException {
        GbifService gbifService = getGbifService();
        return gbifService.startGBIFDownload(guid, country,
                configuration.getEndpoint().toString(),
                configuration.getUsername(),
                configuration.getPassword());
    }

    @Override
    @SuppressWarnings("unchecked")
    public TaskPhase generateStatus(String id) throws ExternalResourceException {
        Map<String, Object> status = getJSONWS(DOWNLOAD_STATUS.format(new Object[]{id}));
        LOGGER.debug("Download status for {}: {}", id, status);
        String statusStr = status != null ? (String) status.get("status") : null;
        return DOWNLOAD_STATUS_MAP.getOrDefault(statusStr, TaskPhase.GENERATING);
    }

    // ---- Download ----

    @Override
    @SuppressWarnings("unchecked")
    public void downloadData(String id, File target) throws ExternalResourceException {
        Map<String, Object> status = getJSONWS(DOWNLOAD_STATUS.format(new Object[]{id}));
        String statusStr = status != null ? (String) status.get("status") : null;
        TaskPhase phase = DOWNLOAD_STATUS_MAP.get(statusStr);
        if (phase != TaskPhase.COMPLETED) {
            throw new ExternalResourceException(
                    "Expecting completed generation",
                    "manage.note.note07", statusStr);
        }

        String downloadLink = (String) status.get("downloadLink");
        try {
            URL downloadUrl = new URL(downloadLink);
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                            configuration.getUsername(),
                            configuration.getPassword().toCharArray());
                }
            });
            try (InputStream in = downloadUrl.openStream();
                 OutputStream out = new FileOutputStream(target)) {
                in.transferTo(out);
            }
        } catch (IOException e) {
            throw new ExternalResourceException("Unable to download data", e,
                    "manage.note.note08", id);
        }
    }

    // ---- Processing ----

    @Override
    public File processData(File downloaded, File workDir, ExternalResourceBean resource) throws ExternalResourceException {
        try {
            File upload = new File(workDir, resource.getOccurrenceId() + "-dwca.zip");
            FileUtils.moveFile(downloaded, upload);
            return upload;
        } catch (IOException ex) {
            throw new ExternalResourceException(
                    "Unable to process download", ex,
                    "manage.note.note09", downloaded.toString());
        }
    }

    // ---- Connection building ----

    @Override
    @SuppressWarnings("unchecked")
    public Object buildConnection(File upload, Object connection, ExternalResourceBean resource) throws ExternalResourceException {
        Map<String, Object> connMap;
        if (connection instanceof Map) {
            connMap = (Map<String, Object>) connection;
        } else {
            connMap = new LinkedHashMap<>();
        }

        connMap.put("url", "file:///" + upload.getAbsolutePath());
        connMap.put("protocol", "DwCA");
        connMap.put("termsForUniqueKey", List.of("http://rs.gbif.org/terms/1.0/gbifID"));

        Map<String, Object> update = new LinkedHashMap<>();
        try {
            update.put("connectionParameters", new ObjectMapper().writeValueAsString(connMap));
        } catch (Exception e) {
            throw new ExternalResourceException("Unable to serialise connection parameters", e);
        }
        return update;
    }
}
