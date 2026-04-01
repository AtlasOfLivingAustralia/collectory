package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.ProviderMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderMapRepository extends JpaRepository<ProviderMap, Long> {

    Optional<ProviderMap> findByCollectionId(Long collectionId);

    @Query("SELECT pm FROM ProviderMap pm WHERE pm.collection.uid = :collectionUid")
    Optional<ProviderMap> findByCollectionUid(String collectionUid);

    @Query("SELECT DISTINCT pm FROM ProviderMap pm LEFT JOIN FETCH pm.collection LEFT JOIN FETCH pm.institution")
    List<ProviderMap> findAllWithCollectionAndInstitution();
}
