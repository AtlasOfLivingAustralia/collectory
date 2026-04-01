package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstitutionRepository extends JpaRepository<Institution, Long>, JpaSpecificationExecutor<Institution> {

    Optional<Institution> findByUid(String uid);

    Optional<Institution> findByGuid(String guid);

    @EntityGraph(attributePaths = {"collections"})
    Optional<Institution> findWithCollectionsByUid(String uid);

    @Query("SELECT i FROM Institution i WHERE i.childInstitutions LIKE %:uid%")
    List<Institution> findByChildInstitutionsContaining(String uid);

    List<Institution> findAllByNameContainingIgnoreCase(String name);

    List<Institution> findAllByIsALAPartnerTrue();

    List<Institution> findAllByNetworkMembershipContainingIgnoreCase(String membership);

    Optional<Institution> findByName(String name);

    long count();

    @Query("SELECT COUNT(i) FROM Institution i WHERE NOT EXISTS " +
           "(SELECT cf FROM ContactFor cf WHERE cf.entityUid = i.uid)")
    long countWithoutContacts();

    @Query("SELECT COUNT(i) FROM Institution i WHERE NOT EXISTS " +
           "(SELECT cf FROM ContactFor cf WHERE cf.entityUid = i.uid AND cf.contact.email <> '')")
    long countWithoutEmailContacts();
}
