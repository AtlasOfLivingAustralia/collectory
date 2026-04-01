package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByEmail(String email);

    List<Contact> findAllByEmail(String email);

    Optional<Contact> findByFirstNameAndLastName(String firstName, String lastName);
}
