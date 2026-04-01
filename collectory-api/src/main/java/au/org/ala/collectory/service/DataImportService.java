package au.org.ala.collectory.service;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.Contact;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.repository.DataResourceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataImportService {

    private static final String EML_FILE = "eml.xml";

    private final AppProperties appProperties;
    private final DataResourceRepository dataResourceRepository;
    private final MetadataService metadataService;
    private final CollectoryAuthService collectoryAuthService;
    private final IdGeneratorService idGeneratorService;
    private final EmlImportService emlImportService;
    private final IptService iptService;
    private final ObjectMapper objectMapper;

    /**
     * Re-imports EML metadata from existing DwCA archives for all data resources
     * that use the DwCA protocol.
     *
     * @return the number of data resources processed
     */
    @Transactional
    public int reimportMetadataFromArchives() {
        int count = 0;
        List<DataResource> allResources = dataResourceRepository.findAll();

        for (DataResource dataResource : allResources) {
            if (dataResource.getConnectionParameters() != null) {
                try {
                    Map<String, Object> json = objectMapper.readValue(
                            dataResource.getConnectionParameters(), new TypeReference<>() {});
                    String protocol = (String) json.get("protocol");

                    if ("DwCA".equals(protocol)) {
                        String urlStr = (String) json.get("url");
                        if (urlStr != null) {
                            File archive = new File(urlStr
                                    .replaceAll("file:////", "/")
                                    .replaceAll("file:///", "/"));
                            if (archive.exists()) {
                                importDataFileForDataResource(dataResource, archive,
                                        Map.of("protocol", "DwCA"), false);
                            } else {
                                log.warn("Archive missing: {}", urlStr);
                            }
                            count++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing connection parameters for {}: {}",
                            dataResource.getUid(), e.getMessage());
                }
            }
        }
        return count;
    }

    /**
     * Imports all DwCA zip files from the specified directory. For each zip,
     * attempts to find an existing DataResource by GUID (extracted from eml.xml),
     * or creates a new one if not found.
     *
     * @param directoryPath the path to the directory containing DwCA zip files
     */
    @Transactional
    public void importDirOfDwCA(String directoryPath) {
        File dir = new File(directoryPath);
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.getName().endsWith(".zip")) {
                DataResource dr = null;
                String guid = getGuidFromDwCAFile(file);
                if (guid != null) {
                    dr = dataResourceRepository.findByGuid(guid).orElse(null);
                }
                if (dr == null) {
                    dr = new DataResource();
                    dr.setUid(idGeneratorService.getNextDataResourceId());
                    dr.setName("to be replaced");
                    dr.setUserLastModified(collectoryAuthService.username());
                    dataResourceRepository.saveAndFlush(dr);
                }
                importDataFileForDataResource(dr, file, Map.of("protocol", "DwCA"), true);
            }
        }
    }

    /**
     * Imports a data file for a data resource, optionally migrating (copying) the file
     * to the upload directory first.
     *
     * @param dataResource   the target data resource
     * @param fileToImport   the file to import
     * @param params         connection parameters including protocol
     * @param migrate        if true, copies the file to the upload directory before importing
     */
    @Transactional
    public void importDataFileForDataResource(DataResource dataResource, File fileToImport,
                                               Map<String, Object> params, boolean migrate) {
        if (migrate) {
            long fileId = System.currentTimeMillis();
            String uploadDirPath = appProperties.getUploadFilePath() + "/" + fileId;
            log.debug("Creating upload directory {}", uploadDirPath);
            File uploadDir = new File(uploadDirPath);
            uploadDir.mkdirs();

            File newFile = new File(uploadDirPath + File.separatorChar + fileToImport.getName());
            try {
                org.apache.commons.io.FileUtils.copyFile(fileToImport, newFile);
            } catch (IOException e) {
                log.error("Failed to copy file: {}", e.getMessage());
                return;
            }
            importDataFileForDataResource(dataResource, newFile, params);
        } else {
            importDataFileForDataResource(dataResource, fileToImport, params);
        }
    }

    /**
     * Core import method: updates connection parameters on the data resource and,
     * for DwCA files, extracts EML metadata and contacts.
     *
     * @param dataResource the target data resource
     * @param newFile      the data file (already in its final location)
     * @param params       connection parameters including protocol
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public void importDataFileForDataResource(DataResource dataResource, File newFile,
                                               Map<String, Object> params) {
        try {
            // Build connection parameters from existing + profile defaults
            Map<String, Object> connParams;
            if (dataResource.getConnectionParameters() != null) {
                connParams = objectMapper.readValue(
                        dataResource.getConnectionParameters(), new TypeReference<>() {});
            } else {
                connParams = new LinkedHashMap<>();
            }

            // Merge connection profile parameters
            String protocol = (String) params.get("protocol");
            Map<String, Object> connProfile = metadataService.getConnectionProfile(protocol);

            if (connProfile != null && connProfile.containsKey("params")) {
                List<Map<String, Object>> profileParams =
                        (List<Map<String, Object>>) connProfile.get("params");
                Map<String, Map<String, Object>> allConnParams = metadataService.getConnectionParameters();

                for (Map<String, Object> param : profileParams) {
                    String paramName = (String) param.get("paramName");
                    String name = (String) param.get("name");
                    if (allConnParams.containsKey(name)) {
                        Map<String, Object> fullDesc =
                                (Map<String, Object>) allConnParams.get(name);
                        if ("boolean".equals(fullDesc.get("type"))) {
                            connParams.put(paramName,
                                    Boolean.parseBoolean(String.valueOf(params.get(paramName))));
                        } else {
                            connParams.put(paramName, params.get(paramName));
                        }
                    }
                }
            }

            // Handle termsForUniqueKey
            Object termsObj = params.get("termsForUniqueKey");
            if (termsObj instanceof String termsStr && !termsStr.trim().isEmpty()) {
                List<String> terms = Arrays.stream(termsStr.split(","))
                        .map(String::trim)
                        .toList();
                connParams.put("termsForUniqueKey", terms);
            }

            connParams.put("url", "file:///" + newFile.getPath());
            connParams.put("protocol", protocol);

            if (!connParams.containsKey("termsForUniqueKey")) {
                connParams.put("termsForUniqueKey", List.of("occurrenceID"));
            }

            // For DwCA, extract metadata from EML
            if ("DwCA".equals(protocol)) {
                List<Contact> contacts = null;
                List<Contact> primaryContacts = null;
                try (ZipFile zipFile = new ZipFile(newFile)) {
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry.getName().startsWith(EML_FILE)) {
                            try (InputStream is = zipFile.getInputStream(entry)) {
                                Map<String, Object> result = emlImportService.extractContactsFromEml(is, dataResource);
                                @SuppressWarnings("unchecked")
                                List<Contact> c = (List<Contact>) result.get("contacts");
                                @SuppressWarnings("unchecked")
                                List<Contact> pc = (List<Contact>) result.get("primaryContacts");
                                contacts = c;
                                primaryContacts = pc;
                            }
                        }
                    }
                }
                // Sync contacts — add new, update existing, remove obsolete
                if (contacts != null) {
                    iptService.syncContacts(dataResource, contacts, primaryContacts,
                            collectoryAuthService.username(), true);
                }
            }

            dataResource.setConnectionParameters(objectMapper.writeValueAsString(connParams));
            dataResourceRepository.saveAndFlush(dataResource);

        } catch (Exception e) {
            log.error("Error importing data file for {}: {}", dataResource.getUid(),
                    e.getMessage(), e);
        }
    }

    /**
     * Extracts the packageId attribute from the eml.xml within a DwCA zip file,
     * which serves as the GUID for the dataset.
     *
     * @param file the DwCA zip file
     * @return the packageId (GUID), or null if not found or on error
     */
    public String getGuidFromDwCAFile(File file) {
        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().startsWith(EML_FILE)) {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document doc = builder.parse(is);
                        return doc.getDocumentElement().getAttribute("packageId");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error reading GUID from DwCA file {}: {}", file.getName(), e.getMessage());
        }
        return null;
    }
}
