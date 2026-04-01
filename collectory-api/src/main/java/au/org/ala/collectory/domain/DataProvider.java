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
@Table(name = "data_provider", indexes = {
        @Index(name = "idx_data_provider_uid", columnList = "uid")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true, exclude = {"resources", "consumerInstitutions", "consumerCollections", "externalIdentifiers"})
public class DataProvider extends ProviderGroup {

    public static final String ENTITY_TYPE = "DataProvider";
    public static final String ENTITY_PREFIX = "dp";

    @Column(name = "hiddenjson", columnDefinition = "text")
    private String hiddenJSON;

    @NotNull
    @Column(name = "gbif_country_to_attribute", length = 3, nullable = false)
    private String gbifCountryToAttribute = "";

    @OneToMany(mappedBy = "dataProvider")
    private Set<DataResource> resources = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "data_provider_institution",
            joinColumns = @JoinColumn(name = "data_provider_id"),
            inverseJoinColumns = @JoinColumn(name = "institution_id")
    )
    private Set<Institution> consumerInstitutions = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "data_provider_collection",
            joinColumns = @JoinColumn(name = "data_provider_id"),
            inverseJoinColumns = @JoinColumn(name = "collection_id")
    )
    private Set<Collection> consumerCollections = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "data_provider_external_identifier",
            joinColumns = @JoinColumn(name = "data_provider_external_identifiers_id"),
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
        return "dataProvider";
    }
}
