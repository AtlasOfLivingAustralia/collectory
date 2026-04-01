package au.org.ala.collectory.dto;

import au.org.ala.collectory.resources.TaskPhase;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
public class ExternalResourceBean implements Comparable<ExternalResourceBean> {

    private String uid;
    private String name;
    private String guid;
    private ResourceStatus status;
    private String source;
    private Date sourceUpdated;
    private Date existingChecked;
    private Boolean addResource;
    private Boolean updateMetadata;
    private Boolean updateConnection;
    private TaskPhase phase;
    private List<Map<String, Object>> notes = new ArrayList<>();
    private String occurrenceId;
    private Integer recordCount;
    private String country;

    public void addNote(String code, Object... args) {
        notes.add(Map.of("code", code, "args", args));
    }

    public void addError(String code, Object... args) {
        addNote(code, args);
        this.phase = TaskPhase.ERROR;
    }

    public boolean isUpdateRequired() {
        return Boolean.TRUE.equals(addResource) || Boolean.TRUE.equals(updateMetadata) || Boolean.TRUE.equals(updateConnection);
    }

    @Override
    public int compareTo(ExternalResourceBean o) {
        int so1 = status != null ? status.getOrder() : 0;
        int so2 = o.status != null ? o.status.getOrder() : 0;
        if (so1 != so2) return so1 - so2;
        return name != null ? name.compareTo(o.name != null ? o.name : "") : 0;
    }

    public enum ResourceStatus {
        UNKNOWN(0),
        NEW(1),
        CHANGED(2),
        UNCHANGED(3),
        LOCAL(4);

        private final int order;

        ResourceStatus(int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }
    }
}
