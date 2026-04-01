package au.org.ala.collectory.service;

import au.org.ala.collectory.domain.Action;
import au.org.ala.collectory.domain.ActivityLog;
import au.org.ala.collectory.domain.Contact;
import au.org.ala.collectory.domain.ContactFor;
import au.org.ala.collectory.domain.ProviderGroup;
import au.org.ala.collectory.repository.ActivityLogRepository;
import au.org.ala.collectory.repository.ContactForRepository;
import au.org.ala.collectory.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final ContactRepository contactRepository;
    private final ContactForRepository contactForRepository;
    private final ProviderGroupService providerGroupService;

    @Transactional
    public void log(String user, boolean isAdmin, Action action) {
        ActivityLog al = ActivityLog.builder()
                .timestamp(LocalDateTime.now())
                .user(user)
                .admin(isAdmin)
                .action(action.toString())
                .build();
        activityLogRepository.saveAndFlush(al);
    }

    @Transactional
    public void log(String user, boolean isAdmin, Action action, String item) {
        ActivityLog al = ActivityLog.builder()
                .timestamp(LocalDateTime.now())
                .user(user)
                .admin(isAdmin)
                .action(action.toString() + " " + item)
                .build();
        activityLogRepository.saveAndFlush(al);
    }

    @Transactional
    public void log(String user, boolean isAdmin, String uid, Action action) {
        ProviderGroup pg = providerGroupService.get(uid);
        if (pg == null) {
            log(user, isAdmin, action, " entity with uid = " + uid);
            return;
        }

        boolean isContact = false;
        boolean isEntityAdmin = false;

        Optional<Contact> contactOpt = contactRepository.findByEmail(user);
        if (contactOpt.isPresent()) {
            Optional<ContactFor> cfOpt = contactForRepository.findByContactAndEntityUid(contactOpt.get(), uid);
            if (cfOpt.isPresent()) {
                isContact = true;
                isEntityAdmin = cfOpt.get().isAdministrator();
            }
        }

        ActivityLog al = ActivityLog.builder()
                .timestamp(LocalDateTime.now())
                .user(user)
                .admin(isAdmin)
                .entityUid(uid)
                .contactForEntity(isContact)
                .administratorForEntity(isEntityAdmin)
                .action(action.toString())
                .build();
        activityLogRepository.saveAndFlush(al);
    }

    @Transactional
    public void log(String user, boolean isAdmin, long id, Action action) {
        ActivityLog al = ActivityLog.builder()
                .timestamp(LocalDateTime.now())
                .user(user)
                .admin(isAdmin)
                .entityUid(String.valueOf(id))
                .action(action.toString())
                .build();
        activityLogRepository.saveAndFlush(al);
    }
}
