package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.Licence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LicenceRepository extends JpaRepository<Licence, Long> {

    Optional<Licence> findByAcronym(String acronym);

    Optional<Licence> findByName(String name);

    Optional<Licence> findByUrl(String url);
}
