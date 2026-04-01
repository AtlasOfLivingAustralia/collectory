package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.TempDataResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TempDataResourceRepository extends JpaRepository<TempDataResource, Long> {

    Optional<TempDataResource> findByUid(String uid);

    List<TempDataResource> findAllByStatus(String status);

    List<TempDataResource> findAllByEmail(String email);

    Optional<TempDataResource> findByAlaId(String alaId);

    long count();

    // L4: paginated listing with optional status filter — mirrors Grails criteria query
    Page<TempDataResource> findAll(Pageable pageable);

    Page<TempDataResource> findAllByStatus(String status, Pageable pageable);

    // L4: search by name (partial match)
    @Query("SELECT t FROM TempDataResource t WHERE (:name IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%')))")
    Page<TempDataResource> findByNameContainingIgnoreCase(@Param("name") String name, Pageable pageable);
}
