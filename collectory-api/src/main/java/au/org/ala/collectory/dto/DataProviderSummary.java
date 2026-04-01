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
public class DataProviderSummary extends ProviderGroupSummary {
    private List<Map<String, String>> resources;
    private List<Map<String, String>> relatedCollections = new ArrayList<>();
    private List<Map<String, String>> relatedInstitutions = new ArrayList<>();
}
