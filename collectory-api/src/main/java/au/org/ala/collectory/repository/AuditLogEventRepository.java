package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.AuditLogEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogEventRepository extends JpaRepository<AuditLogEvent, Long> {

    List<AuditLogEvent> findAllByPersistedObjectId(String persistedObjectId);

    Page<AuditLogEvent> findAllByClassName(String className, Pageable pageable);

    Page<AuditLogEvent> findAll(Pageable pageable);

    List<AuditLogEvent> findAllByUri(String uri);

    Page<AuditLogEvent> findAllByUri(String uri, Pageable pageable);

    // Changes report: search by actor and/or property/uri
    Page<AuditLogEvent> findAllByActorContainingIgnoreCase(String actor, Pageable pageable);

    @Query("SELECT a FROM AuditLogEvent a WHERE " +
           "LOWER(a.actor) LIKE LOWER(CONCAT('%', :who, '%')) AND " +
           "(LOWER(a.uri) LIKE LOWER(CONCAT('%', :what, '%')) OR LOWER(a.propertyName) LIKE LOWER(CONCAT('%', :what, '%')))")
    Page<AuditLogEvent> searchByWhoAndWhat(String who, String what, Pageable pageable);

    @Query("SELECT a FROM AuditLogEvent a WHERE " +
           "LOWER(a.uri) LIKE LOWER(CONCAT('%', :what, '%')) OR LOWER(a.propertyName) LIKE LOWER(CONCAT('%', :what, '%'))")
    Page<AuditLogEvent> searchByWhat(String what, Pageable pageable);
}
