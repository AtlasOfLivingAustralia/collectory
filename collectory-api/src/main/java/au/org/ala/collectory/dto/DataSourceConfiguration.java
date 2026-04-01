package au.org.ala.collectory.dto;

import au.org.ala.collectory.resources.DataSourceAdapter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.net.URL;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"adaptorString"})
public class DataSourceConfiguration {
    private String guid;
    private String name;
    private String description;
    private Class<? extends DataSourceAdapter> adaptorClass;
    private URL endpoint;
    private String username;
    private String password;
    private String country;
    private String recordType;
    private String dataProviderUid;
    private Map<String, String> defaultDatasetValues;
    private List<String> keyTerms;
    private List<ExternalResourceBean> resources;
    private Integer maxNoOfDatasets;
    private Integer maxRecordCount;
    private Integer minRecordCount;

    public DataSourceAdapter createAdaptor() {
        try {
            return adaptorClass.getDeclaredConstructor(DataSourceConfiguration.class).newInstance(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create adaptor", e);
        }
    }

    public String getAdaptorString() {
        return adaptorClass != null ? adaptorClass.getName() : null;
    }

    @SuppressWarnings("unchecked")
    public void setAdaptorString(String ac) {
        try {
            this.adaptorClass = (Class<? extends DataSourceAdapter>) Class.forName(ac);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Adaptor class not found: " + ac, e);
        }
    }
}
