package au.org.ala.collectory.service;

import au.org.ala.collectory.domain.DataHub;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.domain.ProviderGroup;
import au.org.ala.collectory.dto.DataResourceSummary;
import au.org.ala.collectory.util.JSONHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataResourceService {

    private final DataHubService dataHubService;

    public DataResourceSummary buildSummary(DataResource dataResource) {
        DataResourceSummary drs = new DataResourceSummary();
        drs.setId(dataResource.getId());
        drs.setUid(dataResource.getUid());
        drs.setName(dataResource.getName());
        drs.setAcronym(dataResource.getAcronym());
        drs.setShortDescription(dataResource.getPubShortDescription());
        drs.setLsid(dataResource.getGuid());

        if (dataResource.getDataProvider() != null) {
            drs.setDataProvider(dataResource.getDataProvider().getName());
            drs.setDataProviderId(String.valueOf(dataResource.getDataProvider().getId()));
            drs.setDataProviderUid(dataResource.getDataProvider().getUid());
        }
        drs.setDownloadLimit(dataResource.getDownloadLimit());

        // Hub membership
        List<DataHub> hubs = dataHubService.listDataHubs();
        if (hubs != null) {
            drs.setHubMembership(hubs.stream()
                    .filter(h -> isDataResourceMember(h, dataResource.getUid()))
                    .map(h -> Map.of("uid", h.getUid(), "name", h.getName()))
                    .collect(Collectors.toList()));
        }

        // Consumer relationships
        Set<ProviderGroup> consumers = new HashSet<>();
        if (dataResource.getConsumerInstitutions() != null) consumers.addAll(dataResource.getConsumerInstitutions());
        if (dataResource.getConsumerCollections() != null) consumers.addAll(dataResource.getConsumerCollections());

        for (ProviderGroup consumer : consumers) {
            if (consumer.getUid().startsWith("co")) {
                drs.getRelatedCollections().add(Map.of("uid", consumer.getUid(), "name", consumer.getName()));
            } else {
                drs.getRelatedInstitutions().add(Map.of("uid", consumer.getUid(), "name", consumer.getName()));
            }
        }

        // Backward compatibility
        if (!drs.getRelatedInstitutions().isEmpty()) {
            drs.setInstitution(drs.getRelatedInstitutions().get(0).get("name"));
            drs.setInstitutionUid(drs.getRelatedInstitutions().get(0).get("uid"));
        }

        return drs;
    }

    private boolean isDataResourceMember(DataHub hub, String uid) {
        if (hub.getMemberDataResources() == null) return false;
        List<String> members = JSONHelper.parseJsonList(hub.getMemberDataResources());
        return members.contains(uid);
    }
}
