package au.org.ala.collectory.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DataResourceSummary extends ProviderGroupSummary {
    private String dataProvider;
    private String dataProviderId;
    private String dataProviderUid;
    private String institution;
    private String institutionUid;
    private int downloadLimit;
    private List<Map<String, String>> relatedCollections = new ArrayList<>();
    private List<Map<String, String>> relatedInstitutions = new ArrayList<>();
    private List<Map<String, String>> hubMembership = new ArrayList<>();
}
