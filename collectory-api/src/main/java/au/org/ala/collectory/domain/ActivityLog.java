package au.org.ala.collectory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @NotNull
    @Column(length = 255, nullable = false)
    private String action;

    @Column
    private boolean admin;

    @Column(name = "administrator_for_entity")
    private boolean administratorForEntity;

    @Column(name = "contact_for_entity")
    private boolean contactForEntity;

    @Column(name = "entity_uid", length = 255)
    private String entityUid;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime timestamp;

    @NotNull
    @Column(name = "\"user\"", length = 255, nullable = false)
    private String user;
}
