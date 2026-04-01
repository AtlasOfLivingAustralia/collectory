package au.org.ala.collectory.service;

import au.org.ala.collectory.domain.Contact;
import au.org.ala.collectory.domain.ContactFor;
import au.org.ala.collectory.domain.ProviderGroup;
import au.org.ala.collectory.repository.ContactForRepository;
import au.org.ala.collectory.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactService {

    private final ContactRepository contactRepository;
    private final ContactForRepository contactForRepository;
    private final ProviderGroupService providerGroupService;

    public Page<Contact> list(int page, int size) {
        return contactRepository.findAll(
                PageRequest.of(page, Math.min(size, 100), Sort.by("lastName")));
    }

    public Optional<Contact> get(Long id) {
        return contactRepository.findById(id);
    }

    public Optional<Contact> findByEmail(String email) {
        return contactRepository.findByEmail(email);
    }

    @Transactional
    public Contact save(Contact contact) {
        return contactRepository.saveAndFlush(contact);
    }

    @Transactional
    public void delete(Contact contact) {
        // Delete all ContactFor links first
        List<ContactFor> links = contactForRepository.findAllByContact(contact);
        contactForRepository.deleteAll(links);
        contactRepository.delete(contact);
        contactRepository.flush();
    }

    public List<Map<String, Object>> getContactRelationships(Contact contact) {
        return contactForRepository.findAllByContact(contact).stream()
                .map(cf -> {
                    ProviderGroup entity = providerGroupService.get(cf.getEntityUid());
                    String entityName = entity != null ? entity.getName() : cf.getEntityUid();
                    return Map.<String, Object>of(
                            "entityUid", cf.getEntityUid(),
                            "entityName", entityName,
                            "role", cf.getRole() != null ? cf.getRole() : "",
                            "administrator", cf.isAdministrator(),
                            "primaryContact", cf.isPrimaryContact()
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public ContactFor addContactFor(Contact contact, String entityUid, String role,
                                     boolean administrator, boolean primaryContact) {
        ContactFor cf = new ContactFor();
        cf.setContact(contact);
        cf.setEntityUid(entityUid);
        cf.setRole(role);
        cf.setAdministrator(administrator);
        cf.setPrimaryContact(primaryContact);
        return contactForRepository.saveAndFlush(cf);
    }

    @Transactional
    public void removeContactFor(Contact contact, String entityUid) {
        contactForRepository.findByContactAndEntityUid(contact, entityUid)
                .ifPresent(contactForRepository::delete);
    }

    public List<ContactFor> getContactsForEntity(String entityUid) {
        return contactForRepository.findAllByEntityUid(entityUid);
    }
}
