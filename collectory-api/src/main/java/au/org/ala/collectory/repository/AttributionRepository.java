package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.Attribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttributionRepository extends JpaRepository<Attribution, Long> {

    Optional<Attribution> findByUid(String uid);

    List<Attribution> findAllByUidIn(List<String> uids);
}
