package au.org.ala.collectory.dto;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(includeFieldNames = true)
public class GBIFActiveLoad {
    private String downloadId;
    private String gbifResourceUid;
    private String name;
    private String phase = "<NOT STARTED>";
    private String dataResourceUid;
    private String repatriationCountry;
    private boolean completed = false;

    public void setCompleted() {
        this.completed = true;
    }

    public boolean isComplete() {
        return completed;
    }
}
