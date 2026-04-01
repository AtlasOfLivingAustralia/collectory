package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.Sequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SequenceRepository extends JpaRepository<Sequence, Long> {

    Optional<Sequence> findByName(String name);

    @Modifying
    @Query("UPDATE Sequence s SET s.nextId = s.nextId + 1 WHERE s.name = :name")
    int incrementNextId(String name);
}
