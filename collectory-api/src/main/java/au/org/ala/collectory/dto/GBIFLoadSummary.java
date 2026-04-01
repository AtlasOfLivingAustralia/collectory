package au.org.ala.collectory.dto;

import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@ToString(includeFieldNames = true)
public class GBIFLoadSummary {
    private Date startTime;
    private Date finishTime;
    private String country;
    private String status;
    private List<GBIFActiveLoad> loads = new ArrayList<>();

    public boolean isLoadRunning() {
        return loads.stream().anyMatch(load -> !load.isComplete());
    }

    public double getPercentageComplete() {
        if (loads.isEmpty()) return 100.0;
        long complete = loads.stream().filter(GBIFActiveLoad::isComplete).count();
        return ((double) complete / loads.size()) * 100;
    }
}
