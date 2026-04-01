package au.org.ala.collectory.service;

import au.org.ala.collectory.domain.DataHub;
import au.org.ala.collectory.repository.DataHubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DataHubService {

    private final DataHubRepository dataHubRepository;

    @Cacheable("dataHubCache")
    public List<DataHub> listDataHubs() {
        return dataHubRepository.findAll();
    }
}
