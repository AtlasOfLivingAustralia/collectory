package au.org.ala.collectory.service;

import au.org.ala.collectory.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private Map<String, Map<String, Object>> connectionProfileMetadata = null;
    private Map<String, Map<String, Object>> connectionParameterMetadata = null;

    public Map<String, Object> getConnectionProfile(String profileName) {
        checkConnectionMetadata();
        Map<String, Object> pr = connectionProfileMetadata.get(profileName);
        if (pr == null) return null;

        Map<String, Object> clone = new LinkedHashMap<>();
        clone.put("name", pr.get("name"));
        clone.put("display", pr.get("display"));

        @SuppressWarnings("unchecked")
        List<String> params = (List<String>) pr.get("params");
        if (params != null) {
            List<Map<String, Object>> fullParams = new ArrayList<>();
            for (String paramName : params) {
                Map<String, Object> param = connectionParameterMetadata.get(paramName);
                if (param != null) {
                    fullParams.add(param);
                }
            }
            clone.put("params", fullParams);
        }
        return clone;
    }

    public String convertAnyLocalPaths(String obj) {
        if (obj == null) return null;
        String oldPath = "file:///" + appProperties.getUploadFilePath();
        String newPath = appProperties.getServerUrl() + appProperties.getUploadExternalUrlPath();
        return obj.replace(oldPath, newPath);
    }

    public Map<String, Map<String, Object>> getConnectionProfiles() {
        checkConnectionMetadata();
        return connectionProfileMetadata;
    }

    public List<Map<String, Object>> getConnectionProfilesAsList() {
        Map<String, Map<String, Object>> profiles = getConnectionProfiles();
        return profiles != null ? new ArrayList<>(profiles.values()) : List.of();
    }

    public List<Map<String, Object>> getConnectionProfilesWithFileUpload() {
        return getConnectionProfilesAsList().stream()
                .filter(p -> Boolean.TRUE.equals(p.get("supportFileUpload")))
                .toList();
    }

    public Map<String, Map<String, Object>> getConnectionParameters() {
        checkConnectionMetadata();
        return connectionParameterMetadata;
    }

    public Map<String, Object> getConnectionParameter(String name) {
        checkConnectionMetadata();
        return connectionParameterMetadata != null ? connectionParameterMetadata.get(name) : null;
    }

    public void clearConnectionProfiles() {
        connectionProfileMetadata = null;
    }

    public void clearConnectionParameters() {
        connectionParameterMetadata = null;
    }

    private void checkConnectionMetadata() {
        if (connectionProfileMetadata == null) {
            loadConnectionMetadata();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadConnectionMetadata() {
        String path = "/data/collectory/config/connection-profiles.json";
        log.info("Loading connection profiles and parameters from: {}", path);
        File pFile = new File(path);
        if (!pFile.exists()) {
            log.info("Connection profiles file does not exist: {}", path);
            return;
        }

        try {
            JsonNode md = objectMapper.readTree(pFile);
            connectionProfileMetadata = new LinkedHashMap<>();
            connectionParameterMetadata = new LinkedHashMap<>();

            if (md.has("profiles")) {
                for (JsonNode pr : md.get("profiles")) {
                    Map<String, Object> profile = objectMapper.convertValue(pr, Map.class);
                    connectionProfileMetadata.put((String) profile.get("name"), profile);
                }
            }
            if (md.has("parameters")) {
                for (JsonNode pa : md.get("parameters")) {
                    Map<String, Object> param = objectMapper.convertValue(pa, Map.class);
                    connectionParameterMetadata.put((String) param.get("name"), param);
                }
            }
        } catch (Exception e) {
            log.error("Error loading connection profiles", e);
        }
    }
}
