package au.org.ala.collectory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "data_hub", indexes = {
        @Index(name = "idx_data_hub_uid", columnList = "uid")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true, exclude = {"externalIdentifiers"})
public class DataHub extends ProviderGroup {

    public static final String ENTITY_TYPE = "DataHub";
    public static final String ENTITY_PREFIX = "dh";

    @Column(name = "member_institutions", columnDefinition = "text")
    private String memberInstitutions = "[]";

    @Column(name = "member_collections", columnDefinition = "text")
    private String memberCollections = "[]";

    @Column(name = "member_data_resources", columnDefinition = "text")
    private String memberDataResources = "[]";

    @Column(columnDefinition = "text")
    private String members = "[]";

    @ManyToMany
    @JoinTable(
            name = "data_hub_external_identifier",
            joinColumns = @JoinColumn(name = "data_hub_external_identifiers_id"),
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
        return "dataHub";
    }
}
