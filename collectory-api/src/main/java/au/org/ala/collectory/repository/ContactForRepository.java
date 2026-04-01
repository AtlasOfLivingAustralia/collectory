package au.org.ala.collectory.repository;

import au.org.ala.collectory.domain.Contact;
import au.org.ala.collectory.domain.ContactFor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactForRepository extends JpaRepository<ContactFor, Long> {

    List<ContactFor> findAllByEntityUid(String entityUid);

    Optional<ContactFor> findByEntityUidAndContact(String entityUid, Contact contact);

    Optional<ContactFor> findByContactAndEntityUid(Contact contact, String entityUid);

    Optional<ContactFor> findByEntityUidAndContactId(String entityUid, Long contactId);

    List<ContactFor> findAllByContactId(Long contactId);

    List<ContactFor> findAllByContact(Contact contact);

    List<ContactFor> findAllByEntityUidAndNotifyTrue(String entityUid);

    void deleteByEntityUidAndContactId(String entityUid, Long contactId);
}
