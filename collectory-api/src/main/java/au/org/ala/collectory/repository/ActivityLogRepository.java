package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findAllByEntityUid(String entityUid);

    Page<ActivityLog> findAll(Pageable pageable);

    // L3: used by ReportsController.notifications to filter by the 'notify-service' user
    List<ActivityLog> findAllByUser(String user);

    Page<ActivityLog> findAllByUser(String user, Pageable pageable);

    // Activity report count queries
    long countByActionAndAdministratorForEntityTrueAndAdminFalse(String action);

    long countByActionAndAdministratorForEntityTrueAndAdminTrue(String action);
}
