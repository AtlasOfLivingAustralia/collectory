package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.ProviderCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderCodeRepository extends JpaRepository<ProviderCode, Long> {

    Optional<ProviderCode> findByCode(String code);
}
