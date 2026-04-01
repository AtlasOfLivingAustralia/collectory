package au.org.ala.collectory.resources;

import au.org.ala.collectory.dto.DataSourceConfiguration;
import au.org.ala.collectory.dto.ExternalResourceBean;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class DataSourceLoad {
    private String guid;
    private Date startTime;
    private Date finishTime;
    private DataSourceConfiguration configuration;

    public List<ExternalResourceBean> getResources() {
        return configuration.getResources();
    }

    public boolean isComplete() {
        List<ExternalResourceBean> resources = getResources();
        return resources == null || resources.isEmpty() || resources.stream().allMatch(r -> r.getPhase() == null || r.getPhase().isTerminal());
    }

    public double getPercentageComplete() {
        List<ExternalResourceBean> resources = getResources();
        if (resources == null || resources.isEmpty()) return 100.0;
        long completed = resources.stream().filter(r -> r.getPhase() == null || r.getPhase().isTerminal()).count();
        return ((double) completed / resources.size()) * 100;
    }
}
