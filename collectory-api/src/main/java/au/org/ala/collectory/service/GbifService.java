package au.org.ala.collectory.service;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.dto.GBIFActiveLoad;
import au.org.ala.collectory.repository.DataResourceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Service for downloading and importing datasets from GBIF.
 * <p>
 * Ported from the Grails GbifService.groovy.
 */
@Service
@Slf4j
public class GbifService {

    private final AppProperties appProperties;
    private final CrudService crudService;
    private final DataResourceRepository dataResourceRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final String CITATION_FILE = "citations.txt";
    private static final String RIGHTS_FILE = "rights.txt";
    private static final String EML_DIRECTORY = "dataset";
    private static final String OCCURRENCE_FILE = "occurrence.txt";
    private static final String OCCURRENCE_DOWNLOAD = "occurrence/download/request";
    private static final String DOWNLOAD_STATUS = "occurrence/download/";
    private static final String DATASET_RECORD_COUNT = "occurrence/count?datasetKey={0}";
    private static final String LIST_COUNTRIES = "enumeration/country";

    private static final int CONCURRENT_LOADS = 3;
    private static final List<String> STOP_STATUSES = List.of(
            "CANCELLED", "FAILED", "KILLED", "SUCCEEDED", "UNKNOWN");

    private final ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_LOADS);
    private volatile boolean loading = false;
    private final ConcurrentHashMap<String, GBIFActiveLoad> loadMap = new ConcurrentHashMap<>();

    public GbifService(AppProperties appProperties, CrudService crudService,
                       DataResourceRepository dataResourceRepository, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.crudService = crudService;
        this.dataResourceRepository = dataResourceRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    // ---- Status tracking ----

    public GBIFActiveLoad getStatusInfoFor(String country) {
        return loadMap.get(country);
    }

    public GBIFActiveLoad getDatasetKeyStatusInfoFor(String datasetKey) {
        return loadMap.get(datasetKey);
    }

    // ---- Resource creation from archive URL ----

    @Transactional
    public DataResource createGBIFResourceFromArchiveURL(String gbifFileUrl) throws Exception {
        long fileId = System.currentTimeMillis();
        File tmpDir = new File(appProperties.getUploadFilePath() + File.separator + "tmp");
        if (!tmpDir.exists()) {
            FileUtils.forceMkdir(tmpDir);
        }
        File localFile = new File(tmpDir, String.valueOf(fileId));

        try (InputStream in = new URL(gbifFileUrl).openStream();
             OutputStream out = new BufferedOutputStream(new FileOutputStream(localFile))) {
            IOUtils.copy(in, out);
        }

        return createOrUpdateGBIFResource(localFile);
    }

    // ---- Create or update a DataResource from a GBIF ZIP file ----

    @Transactional
    public DataResource createOrUpdateGBIFResource(File uploadedFile) throws Exception {
        Map<String, Object> json = extractDataResourceJSON(uploadedFile);
        json.put("gbifDataset", true);
        json.put("resourceType", "records");
        json.put("contentTypes",
                objectMapper.writeValueAsString(List.of("point occurrence data", "gbif import")));
        log.debug("The JSON to create the dr: {}", json);

        String guid = (String) json.get("guid");
        DataResource dr = dataResourceRepository.findByGuid(guid).orElse(null);
        if (dr == null) {
            dr = crudService.insertDataResource(json);
        } else {
            crudService.updateDataResource(dr, json);
        }
        dr.setLastChecked(java.time.LocalDateTime.now());
        dataResourceRepository.saveAndFlush(dr);

        log.info("{} {} {}", dr.getUid(), dr.getId(), dr.getName());

        // L2: Use atomic Files.move (rename) instead of copyFile so the zip is never
        // observable in a partially-written state under concurrent access.
        String zipFileName = uploadedFile.getParentFile().getAbsolutePath()
                + File.separator + json.getOrDefault("guid", "dwca") + ".zip";
        Files.move(uploadedFile.toPath(), new File(zipFileName).toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log.info("Created the zip file {}", zipFileName);

        applyDwCA(new File(zipFileName), dr);
        return dr;
    }

    // ---- Apply DwCA to a DataResource ----

    @Transactional
    public void applyDwCA(File file, DataResource dr) {
        try {
            log.debug("Copying DwCA to staging and associating the file to the data resource");
            long fileId = System.currentTimeMillis();
            String targetFileName = appProperties.getUploadFilePath()
                    + fileId + File.separator + file.getName();
            File targetFile = new File(targetFileName);
            FileUtils.forceMkdir(targetFile.getParentFile());
            file.renameTo(targetFile);
            log.debug("Finished moving the file for {}", dr.getUid());

            Map<String, Object> connParams;
            if (dr.getConnectionParameters() != null) {
                connParams = objectMapper.readValue(
                        dr.getConnectionParameters(), new TypeReference<>() {});
            } else {
                connParams = new LinkedHashMap<>();
            }
            connParams.put("url", "file:///" + targetFileName);
            connParams.put("protocol", "DwCA");
            connParams.put("termsForUniqueKey", List.of("gbifID"));

            dr.setConnectionParameters(objectMapper.writeValueAsString(connParams));
            log.debug("Finished creating the connection params for {}", dr.getUid());
            dataResourceRepository.saveAndFlush(dr);
            log.debug("Finished saving the connection params for {}", dr.getUid());
        } catch (Exception e) {
            log.error("{} : {}", e.getClass().toString(), e.getMessage(), e);
        }
    }

    // ---- Extract metadata from a GBIF ZIP archive ----

    public Map<String, Object> extractDataResourceJSON(File uploadedFile) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        try (ZipFile zipFile = new ZipFile(uploadedFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                if (entry.getName().equals(CITATION_FILE)) {
                    String citation = new String(
                            zipFile.getInputStream(entry).readAllBytes())
                            .replaceAll("\n", " ");
                    map.putIfAbsent("citation", citation);

                } else if (entry.getName().equals(RIGHTS_FILE)) {
                    map.put("rights", new String(
                            zipFile.getInputStream(entry).readAllBytes())
                            .replaceAll("\n", " "));

                } else if (entry.getName().startsWith(EML_DIRECTORY)) {
                    parseEmlEntry(zipFile, entry, map);

                } else if (entry.getName().equals(OCCURRENCE_FILE)) {
                    try (InputStream is = zipFile.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(
                                 new File(uploadedFile.getParentFile(), OCCURRENCE_FILE))) {
                        IOUtils.copy(is, fos);
                    }
                }
            }
        }
        return map;
    }

    private void parseEmlEntry(ZipFile zipFile, ZipEntry entry,
                               Map<String, Object> map) throws Exception {
        try (InputStream is = zipFile.getInputStream(entry)) {
            DocumentBuilder builder =
                    DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(is);
            Element root = doc.getDocumentElement();
            map.put("guid", root.getAttribute("packageId"));

            NodeList abstracts = root.getElementsByTagName("abstract");
            if (abstracts.getLength() > 0) {
                NodeList paras =
                        ((Element) abstracts.item(0)).getElementsByTagName("para");
                if (paras.getLength() > 0) {
                    map.put("pubDescription", paras.item(0).getTextContent());
                }
            }

            NodeList titles = root.getElementsByTagName("title");
            if (titles.getLength() > 0) {
                map.put("name", titles.item(0).getTextContent());
            }

            NodeList contacts = root.getElementsByTagName("contact");
            if (contacts.getLength() > 0) {
                Element contact = (Element) contacts.item(0);
                NodeList phones = contact.getElementsByTagName("phone");
                if (phones.getLength() > 0) {
                    map.put("phone", phones.item(0).getTextContent());
                }
                NodeList emails =
                        contact.getElementsByTagName("electronicMailAddress");
                if (emails.getLength() > 0) {
                    map.put("email", emails.item(0).getTextContent());
                }
            }

            NodeList citations = root.getElementsByTagName("citation");
            if (citations.getLength() > 0) {
                map.putIfAbsent("citation", citations.item(0).getTextContent());
            }
            NodeList rightsList = root.getElementsByTagName("rights");
            if (rightsList.getLength() > 0) {
                map.putIfAbsent("rights", rightsList.item(0).getTextContent());
            }
        }
    }

    // ---- GBIF download status ----

    public String getDownloadStatus(String downloadId, String userName, String password) {
        String statusUrl = appProperties.getGbifApiUrl() + DOWNLOAD_STATUS + downloadId;
        Map<String, Object> json = getJSONWSWithAuth(statusUrl, userName, password);
        log.debug("Download status for {}: {}",
                downloadId, json != null ? json.get("status") : "null");
        return (json != null && json.get("status") != null)
                ? json.get("status").toString() : "UNKNOWN";
    }

    public String getDownloadStatus(String downloadId) {
        return getDownloadStatus(downloadId,
                appProperties.getGbifApiUser(), appProperties.getGbifApiPassword());
    }

    // ---- HTTP helpers ----

    @SuppressWarnings("unchecked")
    public Map<String, Object> getJSONWSWithAuth(String url, String username, String password) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (username != null && password != null) {
                String encoding = Base64.getEncoder()
                        .encodeToString((username + ":" + password).getBytes());
                headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
            }
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error calling {}: {}", url, e.getMessage());
            return null;
        }
    }

    public Map<String, Object> getJSONWS(String url) {
        return getJSONWSWithAuth(url, null, null);
    }

    // ---- Data availability check ----

    public boolean isDataAvailableForResource(String resourceId) {
        String url = appProperties.getGbifApiUrl()
                + MessageFormat.format(DATASET_RECORD_COUNT, resourceId);
        try {
            String value = restTemplate.getForObject(url, String.class);
            return value != null && Integer.parseInt(value.trim()) > 0;
        } catch (Exception e) {
            log.error("Problem calling the dataset count service for {}",
                    resourceId, e);
            return false;
        }
    }

    // ---- Start a GBIF download ----

    public String startGBIFDownload(String resourceId, String repatCountry) {
        return startGBIFDownload(resourceId, repatCountry,
                appProperties.getGbifApiUrl(),
                appProperties.getGbifApiUser(),
                appProperties.getGbifApiPassword());
    }

    public String startGBIFDownload(String resourceId, String repatCountry,
                                    String apiUrl, String username, String password) {
        try {
            log.debug("[startGBIFDownload] Initialising download.....");
            Map<String, Object> params;

            if (repatCountry != null && !repatCountry.isEmpty()) {
                params = Map.of(
                        "creator", username,
                        "notification_address", List.of(),
                        "format", "DWCA",
                        "predicate", Map.of(
                                "type", "and",
                                "predicates", List.of(
                                        Map.of("type", "equals",
                                                "key", "DATASET_KEY",
                                                "value", resourceId),
                                        Map.of("type", "equals",
                                                "key", "COUNTRY",
                                                "value", repatCountry)
                                )
                        )
                );
            } else {
                params = Map.of(
                        "creator", username,
                        "notification_address", List.of(),
                        "format", "DWCA",
                        "predicate", Map.of(
                                "type", "equals",
                                "key", "DATASET_KEY",
                                "value", resourceId)
                );
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String encoding = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes());
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoding);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl + OCCURRENCE_DOWNLOAD, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            return null;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    // ---- Async download orchestration ----

    public GBIFActiveLoad downloadGbifDataset(String datasetKey,
                                              String repatriationCountry) {
        GBIFActiveLoad l = new GBIFActiveLoad();
        l.setGbifResourceUid(datasetKey);
        l.setRepatriationCountry(repatriationCountry);

        log.debug("Started Gbif Dataset");
        if (!loading) {
            log.debug("Loading resources from GBIF");
            loading = true;
            loadMap.put(datasetKey, l);

            pool.submit(() -> {
                try {
                    DataResource existingDataResource =
                            dataResourceRepository.findByGuid(l.getGbifResourceUid())
                                    .orElse(null);

                    if (!isDataAvailableForResource(l.getGbifResourceUid())) {
                        l.setPhase("Data is currently not available for this "
                                + "resource through GBIF");
                        loading = false;
                        l.setCompleted();
                        return;
                    }

                    log.info("Submitting {} to be processed", l);
                    String downloadId = startGBIFDownload(
                            l.getGbifResourceUid(), l.getRepatriationCountry());

                    if (downloadId != null) {
                        l.setDownloadId(downloadId);
                        l.setPhase("Generating Download...");

                        String status = "";
                        while (!STOP_STATUSES.contains(status)) {
                            Thread.sleep(3000);
                            status = getDownloadStatus(l.getDownloadId());
                        }
                        log.debug("Download status: {}", status);

                        if ("SUCCEEDED".equals(status)) {
                            l.setPhase("Downloading...");
                            File localTmpDir = new File(
                                    appProperties.getUploadFilePath()
                                            + File.separator + "tmp"
                                            + File.separator + l.getDownloadId());
                            FileUtils.forceMkdir(localTmpDir);
                            String tmpFileName = localTmpDir.getAbsolutePath()
                                    + File.separator + l.getDownloadId();

                            try (InputStream instream = new URL(
                                    appProperties.getGbifApiUrl()
                                            + DOWNLOAD_STATUS + "/"
                                            + l.getDownloadId() + ".zip")
                                    .openStream();
                                 FileOutputStream outstream =
                                         new FileOutputStream(tmpFileName)) {
                                IOUtils.copy(instream, outstream);
                            } catch (Exception e) {
                                l.setPhase("Failed To download from GBIF.");
                                loading = false;
                                return;
                            }

                            l.setPhase("Creating GBIF Data resource...");
                            DataResource dr =
                                    createOrUpdateGBIFResource(new File(tmpFileName));

                            if (dr != null && existingDataResource != null) {
                                l.setDataResourceUid(dr.getUid());
                                l.setPhase("Data Resource Updated");
                            } else if (dr != null) {
                                l.setDataResourceUid(dr.getUid());
                                l.setPhase("Data Resource Created");
                            } else {
                                l.setPhase("Data Resource Creation Failed.");
                            }
                        } else {
                            l.setPhase("Download Failed: " + status);
                        }
                        l.setCompleted();
                        loading = false;
                    } else {
                        l.setPhase("Failed. Please check your authentication "
                                + "credentials are valid.");
                        loading = false;
                    }
                } catch (Exception e) {
                    log.error("Error during GBIF download: {}",
                            e.getMessage(), e);
                    l.setPhase("Error: " + e.getMessage());
                    loading = false;
                    l.setCompleted();
                }
            });
            return l;
        } else {
            loading = false;
            return l;
        }
    }

    // ---- Country map ----

    public Map<String, String> getCountryMap() {
        Map<String, String> isoMap = new TreeMap<>();
        try {
            String url = appProperties.getGbifApiUrl() + LIST_COUNTRIES;
            String json = restTemplate.getForObject(url, String.class);
            List<Map<String, Object>> list = objectMapper.readValue(
                    json, new TypeReference<>() {});
            for (Map<String, Object> entry : list) {
                String iso2 = (String) entry.get("iso2");
                String title = (String) entry.get("title");
                if (iso2 != null && title != null) {
                    isoMap.put(iso2, title);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching country map: {}", e.getMessage());
        }
        return isoMap;
    }

    // ---- Dataset last-updated date ----

    public Date getGbifDatasetLastUpdated(String guid) {
        try {
            String url = appProperties.getGbifApiUrl() + "dataset/" + guid;
            Map<String, Object> json = getJSONWS(url);
            if (json == null) {
                return null;
            }

            String dateStr = json.get("pubDate") != null
                    ? json.get("pubDate").toString()
                    : (json.get("modified") != null
                            ? json.get("modified").toString() : null);
            if (dateStr != null) {
                SimpleDateFormat sdf =
                        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                return sdf.parse(dateStr);
            }
        } catch (Exception e) {
            log.error("Unable to retrieve pubDate for GBIF guid: {}", guid);
        }
        return null;
    }
}
