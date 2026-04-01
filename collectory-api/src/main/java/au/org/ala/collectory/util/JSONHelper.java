package au.org.ala.collectory.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class JSONHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Object parseJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return null;
        try {
            return MAPPER.readValue(jsonStr, Object.class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON: {}", jsonStr, e);
            return "error";
        }
    }

    public static List<Map<String, String>> taxonomyHints(String taxonomyHintsJson) {
        if (taxonomyHintsJson == null || taxonomyHintsJson.isEmpty()) return null;
        try {
            Map<String, Object> hints = MAPPER.readValue(taxonomyHintsJson, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, String>> coverage = (List<Map<String, String>>) hints.get("coverage");
            return coverage;
        } catch (JsonProcessingException e) {
            log.error("Error parsing taxonomy hints", e);
            return null;
        }
    }

    public static List<String> parseJsonList(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return Collections.emptyList();
        try {
            return MAPPER.readValue(jsonStr, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON list", e);
            return Collections.emptyList();
        }
    }

    public static String toJsonString(Object obj) {
        if (obj == null) return null;
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Error converting to JSON", e);
            return null;
        }
    }
}
