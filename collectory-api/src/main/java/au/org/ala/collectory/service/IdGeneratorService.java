package au.org.ala.collectory.service;

import au.org.ala.collectory.domain.Sequence;
import au.org.ala.collectory.repository.SequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdGeneratorService {

    private final SequenceRepository sequenceRepository;

    public enum IdType {
        COLLECTION("co", 1),
        INSTITUTION("in", 2),
        DATA_PROVIDER("dp", 3),
        DATA_RESOURCE("dr", 4),
        DATA_HUB("dh", 5),
        ATTRIBUTION("at", 6),
        TEMP_DATA_RESOURCE("drt", 7);

        public final String prefix;
        public final int index;

        IdType(String prefix, int index) {
            this.prefix = prefix;
            this.index = index;
        }

        public static IdType lookup(String prefix) {
            for (IdType type : values()) {
                if (type.prefix.equals(prefix)) {
                    return type;
                }
            }
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String getNextId(IdType type) {
        Sequence seq = sequenceRepository.findById((long) type.index)
                .orElseThrow(() -> new IllegalStateException("Sequence not found for type: " + type));

        long nextId = seq.getNextId();
        String prefix = seq.getPrefix();

        seq.setNextId(nextId + 1);
        sequenceRepository.saveAndFlush(seq);

        return prefix + nextId;
    }

    public String getNextCollectionId() {
        return getNextId(IdType.COLLECTION);
    }

    public String getNextInstitutionId() {
        return getNextId(IdType.INSTITUTION);
    }

    public String getNextDataProviderId() {
        return getNextId(IdType.DATA_PROVIDER);
    }

    public String getNextDataResourceId() {
        return getNextId(IdType.DATA_RESOURCE);
    }

    public String getNextDataHubId() {
        return getNextId(IdType.DATA_HUB);
    }

    public String getNextAttributionId() {
        return getNextId(IdType.ATTRIBUTION);
    }

    public String getNextTempDataResourceId() {
        return getNextId(IdType.TEMP_DATA_RESOURCE);
    }
}
