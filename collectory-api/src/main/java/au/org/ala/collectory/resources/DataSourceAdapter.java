package au.org.ala.collectory.resources;

import au.org.ala.collectory.dto.DataSourceConfiguration;
import au.org.ala.collectory.dto.ExternalResourceBean;
import au.org.ala.collectory.exception.ExternalResourceException;

import java.io.File;
import java.util.List;
import java.util.Map;

public abstract class DataSourceAdapter {
    protected DataSourceConfiguration configuration;

    public DataSourceAdapter(DataSourceConfiguration configuration) {
        this.configuration = configuration;
    }

    public abstract List<Map<String, Object>> datasets() throws ExternalResourceException;

    public abstract ExternalResourceBean createExternalResource(Map<String, Object> external);

    public abstract Map<String, Object> getDataset(String id) throws ExternalResourceException;

    public void addDefaultDatasetValues(Map<String, Object> resource) {
        if (configuration.getDefaultDatasetValues() != null) {
            configuration.getDefaultDatasetValues().forEach((field, value) -> {
                if (!resource.containsKey(field)) {
                    resource.put(field, value);
                }
            });
        }
    }

    public abstract String getSource();

    public abstract Map<String, String> getCountryMap();

    public abstract Map<String, String> getDatasetTypeMap();

    public boolean isGeneratable() {
        return true;
    }

    public boolean isDownloadable() {
        return true;
    }

    public abstract boolean isDataAvailableForResource(String guid) throws ExternalResourceException;

    public abstract String generateData(String guid, String country) throws ExternalResourceException;

    public abstract TaskPhase generateStatus(String id) throws ExternalResourceException;

    public abstract void downloadData(String id, File target) throws ExternalResourceException;

    public abstract File processData(File downloaded, File workDir, ExternalResourceBean resource) throws ExternalResourceException;

    public abstract Object buildConnection(File upload, Object connection, ExternalResourceBean resource) throws ExternalResourceException;
}
