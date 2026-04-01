package au.org.ala.collectory.service;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.DataProvider;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.repository.DataProviderRepository;
import au.org.ala.collectory.repository.DataResourceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class RifCsService {

    private final AppProperties appProperties;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;
    private final ObjectMapper objectMapper;

    /**
     * Retrieves content types and bounding box coordinates for a list of data resources.
     * Content types are extracted from each DataResource's contentTypes JSON field.
     * Bounding boxes are fetched from the biocache services API.
     */
    @Cacheable("longTermCache")
    public Map<String, Object> getResData(List<DataResource> dataResources) {
        log.debug("starting getResData()");
        Map<String, String> resContentTypes = new LinkedHashMap<>();
        Map<String, List<Double>> resBoundingBoxCoords = new LinkedHashMap<>();

        for (DataResource res : dataResources) {
            // content types
            String contentTypesJson = res.getContentTypes();
            if (contentTypesJson != null) {
                try {
                    List<String> types = objectMapper.readValue(contentTypesJson, new TypeReference<>() {});
                    resContentTypes.put(res.getUid(), String.join(",", types));
                } catch (Exception e) {
                    log.warn("Error parsing content types for {}: {}", res.getUid(), e.getMessage());
                }
            }

            // bounding box from biocache
            String url = appProperties.getBiocacheServicesUrl()
                    + "/mapping/bounds?q=data_resource_uid:" + res.getUid()
                    + "%20AND%20geospatial_kosher:true";
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(50_000);

                if (conn.getResponseCode() == 200) {
                    String bboxJson = new String(conn.getInputStream().readAllBytes());
                    List<Double> coords = objectMapper.readValue(bboxJson, new TypeReference<>() {});
                    if (coords.size() >= 4) {
                        double minLong = coords.get(0);
                        double minLat = coords.get(1);
                        double maxLong = coords.get(2);
                        double maxLat = coords.get(3);
                        if (!(minLong == 0 && minLat == 0 && maxLong == 0 && maxLat == 0)) {
                            log.debug("resBoundingBoxCoords => {}", res.getUid());
                            resBoundingBoxCoords.put(res.getUid(), coords);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error looking up data resource bounding box in biocache - {}", e.getLocalizedMessage());
            }
        }

        Map<String, Object> resData = new LinkedHashMap<>();
        resData.put("resContentTypes", resContentTypes);
        resData.put("resBoundingBoxCoords", resBoundingBoxCoords);
        return resData;
    }

    /**
     * Returns all data providers sorted by UID.
     */
    @Cacheable("longTermCache")
    public List<DataProvider> getDataProviders() {
        return dataProviderRepository.findAll(Sort.by("uid"));
    }

    /**
     * Returns all public (non-private) data resources sorted by the given field.
     */
    @Cacheable("longTermCache")
    public List<DataResource> getDataResources(String sort) {
        String sortField = (sort != null && !sort.isEmpty()) ? sort : "uid";
        return dataResourceRepository.findAllByIsPrivateFalse(Sort.by(sortField));
    }
}
