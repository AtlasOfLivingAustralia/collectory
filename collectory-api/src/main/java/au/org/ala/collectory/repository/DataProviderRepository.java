package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.Collection;
import au.org.ala.collectory.domain.DataProvider;
import au.org.ala.collectory.domain.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataProviderRepository extends JpaRepository<DataProvider, Long>, JpaSpecificationExecutor<DataProvider> {

    Optional<DataProvider> findByUid(String uid);

    Optional<DataProvider> findByGuid(String guid);

    Optional<DataProvider> findByGbifRegistryKey(String gbifRegistryKey);

    List<DataProvider> findAllByNameContainingIgnoreCase(String name);

    List<DataProvider> findAllByConsumerInstitutionsContaining(Institution institution);

    List<DataProvider> findAllByConsumerCollectionsContaining(Collection collection);

    long count();
}
