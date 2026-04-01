package au.org.ala.collectory.service;

import au.org.ala.collectory.config.AppProperties;
import au.org.ala.collectory.domain.DataProvider;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.repository.DataProviderRepository;
import au.org.ala.collectory.repository.DataResourceRepository;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Idempotent bulk-import helpers that mirror the Grails DataLoaderService.
 *
 * Each import method reads a delimited file from disk and upserts records
 * (insert if not found by UID, skip/no-op if already present).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataLoaderService {

    static final String[] DATA_PROVIDER_COLUMNS =
            {"uid", "name", "pubDescription", "address", "websiteUrl", "logoUrl", "email", "phone"};

    static final String[] DATA_RESOURCE_COLUMNS =
            {"uid", "dataProvider", "name", "pubDescription", "rights", "citation", "websiteUrl", "logoUrl"};

    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;
    private final IdGeneratorService idGeneratorService;
    private final AppProperties appProperties;

    /**
     * Idempotent import of DataProviders from a tab-separated file.
     *
     * @param filename path to the TSV file
     * @return summary map with keys: headerLines, dataLines, inserts, exists, updates, failures
     */
    @Transactional
    public Map<String, Integer> importDataProviders(String filename) {
        int headerLines = 0, dataLines = 0, inserts = 0, exists = 0, failures = 0;

        try (CSVReader reader = new CSVReaderBuilder(new FileReader(filename))
                .withCSVParser(new CSVParserBuilder().withSeparator('\t').build())
                .build()) {

            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, String> params = toParamMap(DATA_PROVIDER_COLUMNS, line);

                if ("name".equals(params.get("name"))) {
                    headerLines++;
                    continue;
                }
                dataLines++;

                String uid = params.get("uid");
                if (uid == null || uid.isEmpty()) {
                    failures++;
                    log.warn("DataProvider row missing uid, skipping");
                    continue;
                }

                if (dataProviderRepository.findByUid(uid).isPresent()) {
                    exists++;
                    log.debug("DataProvider {} already exists, skipping", uid);
                } else {
                    try {
                        DataProvider dp = new DataProvider();
                        dp.setUid(uid);
                        dp.setName(params.getOrDefault("name", uid));
                        dp.setPubDescription(params.get("pubDescription"));
                        dp.setWebsiteUrl(params.get("websiteUrl"));
                        dp.setEmail(params.get("email"));
                        dp.setPhone(params.get("phone"));
                        dp.setGbifCountryToAttribute(
                                appProperties.getGbifDefaultEntityCountry() != null
                                        ? appProperties.getGbifDefaultEntityCountry()
                                        : "");
                        dataProviderRepository.save(dp);
                        inserts++;
                        log.info("Created DataProvider {}", dp.getName());
                    } catch (Exception e) {
                        failures++;
                        log.error("Failed to create DataProvider {}: {}", params.get("name"), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error reading DataProvider file {}: {}", filename, e.getMessage(), e);
            failures++;
        }

        return buildSummary(headerLines, dataLines, inserts, exists, 0, failures);
    }

    /**
     * Idempotent import of DataResources from a comma-separated file.
     *
     * @param filename path to the CSV file
     * @return summary map with keys: headerLines, dataLines, inserts, exists, updates, failures
     */
    @Transactional
    public Map<String, Integer> importDataResources(String filename) {
        int headerLines = 0, dataLines = 0, inserts = 0, exists = 0, failures = 0;

        try (CSVReader reader = new CSVReaderBuilder(new FileReader(filename))
                .withCSVParser(new CSVParserBuilder().withSeparator(',').build())
                .build()) {

            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, String> params = toParamMap(DATA_RESOURCE_COLUMNS, line);

                if ("name".equals(params.get("name"))) {
                    headerLines++;
                    continue;
                }
                dataLines++;

                String uid = params.get("uid");
                if (uid == null || uid.isEmpty()) {
                    failures++;
                    log.warn("DataResource row missing uid, skipping");
                    continue;
                }

                if (dataResourceRepository.findByUid(uid).isPresent()) {
                    exists++;
                    log.debug("DataResource {} already exists, skipping", uid);
                } else {
                    try {
                        DataResource dr = new DataResource();
                        dr.setUid(uid);
                        dr.setName(params.getOrDefault("name", uid));
                        dr.setPubDescription(params.get("pubDescription"));
                        dr.setRights(params.get("rights"));
                        dr.setCitation(params.get("citation"));
                        dr.setWebsiteUrl(params.get("websiteUrl"));

                        String dpUid = params.get("dataProvider");
                        if (dpUid != null && !dpUid.isEmpty()) {
                            dataProviderRepository.findByUid(dpUid).ifPresent(dr::setDataProvider);
                        }

                        dataResourceRepository.save(dr);
                        inserts++;
                        log.info("Created DataResource {}", dr.getName());
                    } catch (Exception e) {
                        failures++;
                        log.error("Failed to create DataResource {}: {}", params.get("name"), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error reading DataResource file {}: {}", filename, e.getMessage(), e);
            failures++;
        }

        return buildSummary(headerLines, dataLines, inserts, exists, 0, failures);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, String> toParamMap(String[] columns, String[] values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < columns.length && i < values.length; i++) {
            String v = values[i];
            if (v != null && !v.isEmpty()) {
                map.put(columns[i], v);
            }
        }
        return map;
    }

    private Map<String, Integer> buildSummary(
            int headerLines, int dataLines, int inserts, int exists, int updates, int failures) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("headerLines", headerLines);
        result.put("dataLines", dataLines);
        result.put("inserts", inserts);
        result.put("exists", exists);
        result.put("updates", updates);
        result.put("failures", failures);
        return result;
    }
}
