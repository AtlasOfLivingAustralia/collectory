package au.org.ala.collectory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AuditLogEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String actor;

    @Column(name = "class_name", length = 255)
    private String className;

    @Column(name = "date_created")
    private LocalDateTime dateCreated;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "event_name", length = 255)
    private String eventName;

    @Column(name = "new_value", length = 255)
    private String newValue;

    @Column(name = "old_value", length = 255)
    private String oldValue;

    @Column(name = "persisted_object_id", length = 255)
    private String persistedObjectId;

    @Column(name = "persisted_object_version")
    private Long persistedObjectVersion;

    @Column(name = "property_name", length = 255)
    private String propertyName;

    @Column(length = 255)
    private String uri;
}
