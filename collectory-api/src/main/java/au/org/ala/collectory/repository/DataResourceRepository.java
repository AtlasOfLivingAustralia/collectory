package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.Collection;
import au.org.ala.collectory.domain.DataResource;
import au.org.ala.collectory.domain.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataResourceRepository extends JpaRepository<DataResource, Long>, JpaSpecificationExecutor<DataResource> {

    Optional<DataResource> findByUid(String uid);

    Optional<DataResource> findByGuid(String guid);

    List<DataResource> findAllByResourceType(String resourceType);

    List<DataResource> findAllByResourceType(String resourceType, Sort sort);

    List<DataResource> findAllByDataProviderId(Long dataProviderId);

    @Query("SELECT dr FROM DataResource dr WHERE dr.dataProvider.uid = :dpUid")
    List<DataResource> findAllByDataProviderUid(String dpUid);

    List<DataResource> findAllByNameContainingIgnoreCase(String name);

    List<DataResource> findAllByGbifDatasetTrue();

    @Query("SELECT dr FROM DataResource dr WHERE dr.status = :status")
    List<DataResource> findAllByStatus(String status);

    List<DataResource> findAllByIsPrivateFalse();

    List<DataResource> findAllByIsPrivateFalse(Sort sort);

    List<DataResource> findAllByConsumerInstitutionsContaining(Institution institution);

    List<DataResource> findAllByInstitution(Institution institution);

    List<DataResource> findAllByConsumerCollectionsContaining(Collection collection);

    long count();
}
