package au.org.ala.collectory.service;

import au.org.ala.collectory.domain.*;
import au.org.ala.collectory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalIdentifierService {

    private final ExternalIdentifierRepository externalIdentifierRepository;
    private final CollectionRepository collectionRepository;
    private final InstitutionRepository institutionRepository;
    private final DataProviderRepository dataProviderRepository;
    private final DataResourceRepository dataResourceRepository;

    @Transactional
    public ExternalIdentifier addExternalIdentifier(String uid, String identifier, String source, String link) {
        ExternalIdentifier ext = ExternalIdentifier.builder()
                .entityUid(uid)
                .identifier(identifier)
                .source(source)
                .uri(link)
                .build();
        final ExternalIdentifier saved = externalIdentifierRepository.saveAndFlush(ext);

        String prefix = uid.substring(0, 2);
        switch (prefix) {
            case "co" -> collectionRepository.findByUid(uid).ifPresent(c -> {
                if (c.getExternalIdentifiers() == null) c.setExternalIdentifiers(new java.util.HashSet<>());
                c.getExternalIdentifiers().add(saved);
                collectionRepository.saveAndFlush(c);
            });
            case "in" -> institutionRepository.findByUid(uid).ifPresent(i -> {
                if (i.getExternalIdentifiers() == null) i.setExternalIdentifiers(new java.util.HashSet<>());
                i.getExternalIdentifiers().add(saved);
                institutionRepository.saveAndFlush(i);
            });
            case "dp" -> dataProviderRepository.findByUid(uid).ifPresent(dp -> {
                if (dp.getExternalIdentifiers() == null) dp.setExternalIdentifiers(new java.util.HashSet<>());
                dp.getExternalIdentifiers().add(saved);
                dataProviderRepository.saveAndFlush(dp);
            });
            case "dr" -> dataResourceRepository.findByUid(uid).ifPresent(dr -> {
                if (dr.getExternalIdentifiers() == null) dr.setExternalIdentifiers(new java.util.HashSet<>());
                dr.getExternalIdentifiers().add(saved);
                dataResourceRepository.saveAndFlush(dr);
            });
            default -> log.warn("Unknown entity prefix for uid: {}", uid);
        }

        return saved;
    }
}
