package au.org.ala.collectory.service;

import au.org.ala.collectory.domain.Collection;
import au.org.ala.collectory.domain.DataHub;
import au.org.ala.collectory.domain.DataProvider;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.domain.ProviderMap;
import au.org.ala.collectory.dto.CollectionSummary;
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
public class CollectionService {

    private final DataHubService dataHubService;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;

    public CollectionSummary buildSummary(Collection collection) {
        CollectionSummary cs = new CollectionSummary();
        initSummary(cs, collection);

        if (collection.getInstitution() != null) {
            cs.setInstitutionName(collection.getInstitution().getName());
            cs.setInstitutionId(String.valueOf(collection.getInstitution().getId()));
            cs.setInstitutionUid(collection.getInstitution().getUid());
            if (collection.getInstitution().getLogoRef() != null
                    && collection.getInstitution().getLogoRef().getFile() != null) {
                cs.setInstitutionLogoUrl(collection.getInstitution().getLogoRef().getFile());
            }
        }

        cs.setCollectionId(String.valueOf(cs.getId()));
        cs.setCollectionUid(cs.getUid());
        cs.setCollectionName(cs.getName());

        // derivedInstCodes / derivedCollCodes from the ProviderMap
        ProviderMap pm = collection.getProviderMap();
        if (pm != null) {
            cs.setDerivedInstCodes(pm.getInstitutionCodes().stream()
                    .map(au.org.ala.collectory.domain.ProviderCode::getCode)
                    .collect(Collectors.toList()));
            cs.setDerivedCollCodes(pm.getCollectionCodes().stream()
                    .map(au.org.ala.collectory.domain.ProviderCode::getCode)
                    .collect(Collectors.toList()));
        } else {
            cs.setDerivedInstCodes(new ArrayList<>());
            cs.setDerivedCollCodes(new ArrayList<>());
        }

        // relatedDataProviders: DataProviders where this collection is in their consumerCollections
        List<DataProvider> providerDPs = dataProviderRepository.findAllByConsumerCollectionsContaining(collection);
        List<Map<String, String>> relatedDPs = new ArrayList<>();
        for (DataProvider dp : providerDPs) {
            relatedDPs.add(Map.of("uid", dp.getUid(), "name", dp.getName()));
        }
        cs.setRelatedDataProviders(relatedDPs);

        // relatedDataResources: DataResources where this collection is in their consumerCollections
        List<DataResource> providerDRs = dataResourceRepository.findAllByConsumerCollectionsContaining(collection);
        List<Map<String, String>> relatedDRs = new ArrayList<>();
        for (DataResource dr : providerDRs) {
            relatedDRs.add(Map.of("uid", dr.getUid(), "name", dr.getName()));
        }
        cs.setRelatedDataResources(relatedDRs);

        // Hub membership
        List<DataHub> hubs = dataHubService.listDataHubs();
        if (hubs != null) {
            cs.setHubMembership(hubs.stream()
                    .filter(h -> isCollectionMember(h, collection.getUid()))
                    .map(h -> Map.of("uid", h.getUid(), "name", h.getName()))
                    .collect(Collectors.toList()));
        }

        return cs;
    }

    private boolean isCollectionMember(DataHub hub, String uid) {
        if (hub.getMemberCollections() == null) return false;
        List<String> members = JSONHelper.parseJsonList(hub.getMemberCollections());
        return members.contains(uid);
    }

    private void initSummary(CollectionSummary cs, Collection collection) {
        cs.setId(collection.getId());
        cs.setUid(collection.getUid());
        cs.setName(collection.getName());
        cs.setAcronym(collection.getAcronym());
        cs.setShortDescription(collection.getPubShortDescription());
        cs.setLsid(collection.getGuid());
        cs.setTaxonomyCoverageHints(JSONHelper.taxonomyHints(collection.getTaxonomyHints()));
    }
}
