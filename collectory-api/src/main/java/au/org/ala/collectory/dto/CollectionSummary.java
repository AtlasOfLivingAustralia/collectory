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
public class CollectionSummary extends ProviderGroupSummary {
    private String institutionName;
    private String institutionId;
    private String institutionUid;

    private String collectionId;
    private String collectionUid;
    private String collectionName;

    private List<String> derivedInstCodes;
    private List<String> derivedCollCodes;
    private String institutionLogoUrl;
    private List<Map<String, String>> relatedDataProviders = new ArrayList<>();
    private List<Map<String, String>> relatedDataResources = new ArrayList<>();
    private List<Map<String, String>> hubMembership = new ArrayList<>();
}
