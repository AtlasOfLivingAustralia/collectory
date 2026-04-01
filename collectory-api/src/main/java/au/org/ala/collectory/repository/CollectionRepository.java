package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, Long>, JpaSpecificationExecutor<Collection> {

    Optional<Collection> findByUid(String uid);

    Optional<Collection> findByGuid(String guid);

    Optional<Collection> findByAcronym(String acronym);

    List<Collection> findAllByInstitutionId(Long institutionId);

    @EntityGraph(attributePaths = {"institution"})
    List<Collection> findAll();

    List<Collection> findAllByNetworkMembershipContainingIgnoreCase(String membership);

    long count();

    List<Collection> findAllByNameContainingIgnoreCase(String name);

    @Query("SELECT c FROM Collection c WHERE c.institution.uid = :institutionUid")
    List<Collection> findAllByInstitutionUid(String institutionUid);

    // Data report count queries
    @Query("SELECT COUNT(c) FROM Collection c WHERE c.collectionType IS NOT NULL")
    long countWithCollectionType();

    @Query("SELECT COUNT(c) FROM Collection c WHERE c.focus IS NOT NULL")
    long countWithFocus();

    @Query("SELECT COUNT(c) FROM Collection c WHERE c.keywords IS NOT NULL")
    long countWithKeywords();

    @Query("SELECT COUNT(c) FROM Collection c WHERE c.geographicDescription IS NOT NULL")
    long countWithGeoDescription();

    @Query("SELECT COUNT(c) FROM Collection c WHERE c.numRecords <> -1")
    long countWithNumRecords();

    @Query("SELECT COUNT(c) FROM Collection c WHERE c.numRecordsDigitised <> -1")
    long countWithNumRecordsDigitised();

    @Query("SELECT COUNT(c) FROM Collection c WHERE c.pubDescription IS NOT NULL AND c.techDescription IS NOT NULL")
    long countWithDescriptions();

    @Query("SELECT COUNT(c) FROM Collection c WHERE NOT EXISTS " +
           "(SELECT cf FROM ContactFor cf WHERE cf.entityUid = c.uid)")
    long countWithoutContacts();

    @Query("SELECT COUNT(c) FROM Collection c WHERE NOT EXISTS " +
           "(SELECT cf FROM ContactFor cf WHERE cf.entityUid = c.uid AND cf.contact.email <> '')")
    long countWithoutEmailContacts();
}
