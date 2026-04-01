package au.org.ala.collectory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "contact_for")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(exclude = {"contact"})
public class ContactFor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @NotBlank
    @Column(name = "entity_uid", length = 255, nullable = false)
    private String entityUid;

    @Column(length = 128)
    private String role;

    @Column
    private boolean administrator;

    @Column(name = "primary_contact")
    private boolean primaryContact;

    @Column
    private boolean notify;

    @Column(name = "date_created")
    private LocalDateTime dateCreated;

    @Column(name = "date_last_modified")
    private LocalDateTime dateLastModified;

    @Column(name = "user_last_modified", length = 256)
    private String userLastModified;
}
