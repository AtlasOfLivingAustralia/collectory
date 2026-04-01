package au.org.ala.collectory.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProviderGroupSummary {
    private long id;
    private String uid;
    private String uri;
    private String name;
    private String acronym;
    private String shortDescription;
    private String lsid;
    private Object taxonomyCoverageHints;
}
