package au.org.ala.collectory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "collection", indexes = {
        @Index(name = "idx_collection_uid", columnList = "uid")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true, exclude = {"institution", "providerMap", "externalIdentifiers"})
public class Collection extends ProviderGroup {

    public static final String ENTITY_TYPE = "Collection";
    public static final String ENTITY_PREFIX = "co";

    @Column(name = "collection_type", length = 256)
    private String collectionType;

    @Column(length = 14)
    private String active;

    @Column(name = "num_records")
    private int numRecords;

    @Column(name = "num_records_digitised")
    private int numRecordsDigitised;

    @Column(length = 255)
    private String states;

    @Column(name = "geographic_description", length = 255)
    private String geographicDescription;

    @Column(name = "east_coordinate", precision = 13, scale = 10)
    private BigDecimal eastCoordinate;

    @Column(name = "west_coordinate", precision = 13, scale = 10)
    private BigDecimal westCoordinate;

    @Column(name = "north_coordinate", precision = 13, scale = 10)
    private BigDecimal northCoordinate;

    @Column(name = "south_coordinate", precision = 13, scale = 10)
    private BigDecimal southCoordinate;

    @Column(name = "start_date", length = 45)
    private String startDate;

    @Column(name = "end_date", length = 45)
    private String endDate;

    @Column(name = "kingdom_coverage", columnDefinition = "text")
    private String kingdomCoverage;

    @Column(name = "scientific_names", columnDefinition = "text")
    private String scientificNames;

    @Column(name = "sub_collections", columnDefinition = "text")
    private String subCollections;

    @ManyToOne
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @OneToOne(mappedBy = "collection")
    private ProviderMap providerMap;

    @ManyToMany
    @JoinTable(
            name = "collection_external_identifier",
            joinColumns = @JoinColumn(name = "collection_external_identifiers_id"),
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
        return "collection";
    }
}
