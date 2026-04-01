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
public class InstitutionSummary extends ProviderGroupSummary {
    private String institutionId;
    private String institutionUid;
    private String institutionName;
    private List<List<String>> collections;
    private List<Map<String, String>> relatedDataProviders = new ArrayList<>();
    private List<Map<String, String>> relatedDataResources = new ArrayList<>();
    private List<Map<String, String>> hubMembership = new ArrayList<>();
}
