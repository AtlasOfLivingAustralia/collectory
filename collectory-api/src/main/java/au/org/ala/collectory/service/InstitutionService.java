package au.org.ala.collectory.service;

import au.org.ala.collectory.domain.DataHub;
import au.org.ala.collectory.domain.DataProvider;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.domain.Institution;
import au.org.ala.collectory.dto.InstitutionSummary;
import au.org.ala.collectory.repository.DataProviderRepository;
import au.org.ala.collectory.repository.DataResourceRepository;
import au.org.ala.collectory.util.JSONHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final DataHubService dataHubService;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;

    public InstitutionSummary buildSummary(Institution institution) {
        InstitutionSummary is = new InstitutionSummary();
        is.setId(institution.getId());
        is.setUid(institution.getUid());
        is.setName(institution.getName());
        is.setAcronym(institution.getAcronym());
        is.setShortDescription(institution.getPubShortDescription());
        is.setLsid(institution.getGuid());

        is.setInstitutionId(String.valueOf(institution.getId()));
        is.setInstitutionUid(institution.getUid());
        is.setInstitutionName(institution.getName());

        if (institution.getCollections() != null) {
            is.setCollections(institution.getCollections().stream()
                    .map(c -> List.of(c.getUid(), c.getName()))
                    .collect(Collectors.toList()));
        }

        // relatedDataProviders: DataProviders where this institution is in their consumerInstitutions
        List<DataProvider> providerDPs = dataProviderRepository.findAllByConsumerInstitutionsContaining(institution);
        List<Map<String, String>> relatedDPs = new ArrayList<>();
        for (DataProvider dp : providerDPs) {
            relatedDPs.add(Map.of("uid", dp.getUid(), "name", dp.getName()));
        }
        is.setRelatedDataProviders(relatedDPs);

        // relatedDataResources: DataResources where this institution is in their consumerInstitutions
        //   OR where dataResource.institution == this institution
        List<DataResource> consumerDRs = dataResourceRepository.findAllByConsumerInstitutionsContaining(institution);
        List<DataResource> institutionDRs = dataResourceRepository.findAllByInstitution(institution);
        // merge, deduplicate by uid
        List<Map<String, String>> relatedDRs = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (DataResource dr : consumerDRs) {
            if (seen.add(dr.getUid())) {
                relatedDRs.add(Map.of("uid", dr.getUid(), "name", dr.getName()));
            }
        }
        for (DataResource dr : institutionDRs) {
            if (seen.add(dr.getUid())) {
                relatedDRs.add(Map.of("uid", dr.getUid(), "name", dr.getName()));
            }
        }
        is.setRelatedDataResources(relatedDRs);

        // Hub membership
        List<DataHub> hubs = dataHubService.listDataHubs();
        if (hubs != null) {
            is.setHubMembership(hubs.stream()
                    .filter(h -> isInstitutionMember(h, institution.getUid()))
                    .map(h -> Map.of("uid", h.getUid(), "name", h.getName()))
                    .collect(Collectors.toList()));
        }

        return is;
    }

    private boolean isInstitutionMember(DataHub hub, String uid) {
        if (hub.getMemberInstitutions() == null) return false;
        List<String> members = JSONHelper.parseJsonList(hub.getMemberInstitutions());
        return members.contains(uid);
    }
}
