package au.org.ala.collectory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "institution", indexes = {
        @Index(name = "idx_institution_uid", columnList = "uid")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true, exclude = {"collections", "externalIdentifiers"})
public class Institution extends ProviderGroup {

    public static final String ENTITY_TYPE = "Institution";
    public static final String ENTITY_PREFIX = "in";

    @Column(name = "institution_type", length = 45)
    private String institutionType;

    @Column(name = "child_institutions", length = 255)
    private String childInstitutions;

    @NotNull
    @Column(name = "gbif_country_to_attribute", length = 3, nullable = false)
    private String gbifCountryToAttribute = "";

    @OneToMany(mappedBy = "institution")
    private Set<Collection> collections = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "institution_external_identifier",
            joinColumns = @JoinColumn(name = "institution_external_identifiers_id"),
            inverseJoinColumns = @JoinColumn(name = "external_identifier_id")
    )
    private Set<ExternalIdentifier> externalIdentifiers = new HashSet<>();

    @Override
    public String entityType() {
        return ENTITY_TYPE;
    }

    @Override
    public Long dbId() {
        return getId();
    }

    @Override
    public String urlForm() {
        return "institution";
    }
}
