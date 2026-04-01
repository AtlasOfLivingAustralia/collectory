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

@Entity
@Table(name = "external_identifier")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ExternalIdentifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @NotNull
    @Column(name = "entity_uid", length = 255, nullable = false)
    private String entityUid;

    @NotNull
    @Column(length = 255, nullable = false)
    private String identifier;

    @NotNull
    @Column(length = 255, nullable = false)
    private String source;

    @Column(length = 255)
    private String uri;
}
