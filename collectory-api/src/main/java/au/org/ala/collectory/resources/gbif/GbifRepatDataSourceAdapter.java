package au.org.ala.collectory.resources.gbif;

import au.org.ala.collectory.dto.DataSourceConfiguration;
import au.org.ala.collectory.dto.ExternalResourceBean;
import au.org.ala.collectory.exception.ExternalResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.*;

/**
 * Data source adapter for the GBIF API for downloading repatriation data subsets.
 */
public class GbifRepatDataSourceAdapter extends GbifDataSourceAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GbifRepatDataSourceAdapter.class);

    public static final String SOURCE = "GBIF_REPATRIATION";

    static final String OCCURRENCE_REPAT_SEARCH =
            "occurrence/search?repatriated=true&country={0}&type={1}&offset=0&limit=0&facet=datasetKey&facetLimit=10000";

    public GbifRepatDataSourceAdapter(DataSourceConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String getSource() {
        return SOURCE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> datasets() throws ExternalResourceException {
        List<Map<String, Object>> datasets = new ArrayList<>();

        LOGGER.info("Requesting dataset lists configuration.country: {}", configuration.getCountry());
        String url = MessageFormat.format(OCCURRENCE_REPAT_SEARCH,
                configuration.getCountry(), configuration.getRecordType());

        Map<String, Object> json = getJSONWS(url, false);

        if (json != null && json.get("facets") != null) {
            List<Map<String, Object>> facets = (List<Map<String, Object>>) json.get("facets");
            if (!facets.isEmpty()) {
                List<Map<String, Object>> counts =
                        (List<Map<String, Object>>) facets.get(0).get("counts");
                if (counts != null) {
                    for (Map<String, Object> entry : counts) {
                        String name = (String) entry.get("name");
                        int count = entry.get("count") instanceof Number
                                ? ((Number) entry.get("count")).intValue() : 0;

                        boolean withinBounds =
                                (configuration.getMinRecordCount() == null || count >= configuration.getMinRecordCount())
                                && (configuration.getMaxRecordCount() == null || count <= configuration.getMaxRecordCount());

                        boolean withinLimit = configuration.getMaxNoOfDatasets() == null
                                || datasets.size() < configuration.getMaxNoOfDatasets();

                        if (withinBounds && withinLimit) {
                            LOGGER.info("Getting metadata for {} = {}", name, count);
                            Map<String, Object> dataset = getDataset(name, count);
                            if (dataset != null && dataset.get("name") != null) {
                                datasets.add(dataset);
                            }
                        } else {
                            LOGGER.info("Skipping dataset {} = {}", name, count);
                        }
                    }
                }
            }
        }

        LOGGER.info("Total datasets retrieved: {}", datasets.size());
        return datasets;
    }

    @Override
    public ExternalResourceBean createExternalResource(Map<String, Object> external) {
        ExternalResourceBean ext = super.createExternalResource(external);
        Object recordCount = external.get("recordCount");
        if (recordCount instanceof Number) {
            ext.setRecordCount(((Number) recordCount).intValue());
        }
        return ext;
    }

    /**
     * Gets a single dataset from GBIF and enriches it with record count and repatriation country.
     *
     * @param id          The GBIF dataset key
     * @param recordCount The number of repatriated records
     * @return The translated dataset map
     */
    public Map<String, Object> getDataset(String id, Integer recordCount) throws ExternalResourceException {
        Map<String, Object> dataset = getDataset(id);
        if (dataset != null) {
            dataset.put("recordCount", recordCount);
            dataset.put("repatriationCountry", configuration.getCountry());
        }
        return dataset;
    }
}
