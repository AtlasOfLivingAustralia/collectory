package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.ExternalIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalIdentifierRepository extends JpaRepository<ExternalIdentifier, Long> {

    List<ExternalIdentifier> findAllByEntityUid(String entityUid);

    Optional<ExternalIdentifier> findBySourceAndIdentifier(String source, String identifier);

    Optional<ExternalIdentifier> findByEntityUidAndIdentifierAndSource(String entityUid, String identifier, String source);

    void deleteByEntityUidAndIdentifierAndSource(String entityUid, String identifier, String source);
}
