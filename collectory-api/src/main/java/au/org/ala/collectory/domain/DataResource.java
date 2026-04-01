package au.org.ala.collectory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "data_resource", indexes = {
        @Index(name = "idx_data_resource_uid", columnList = "uid")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true, exclude = {"dataProvider", "institution", "consumerInstitutions", "consumerCollections", "externalIdentifiers"})
public class DataResource extends ProviderGroup {

    public static final String ENTITY_TYPE = "DataResource";
    public static final String ENTITY_PREFIX = "dr";

    @Column(columnDefinition = "text")
    private String rights;

    @Column(columnDefinition = "text")
    private String citation;

    @Column(name = "license_type", length = 45)
    private String licenseType = "other";

    @Column(name = "license_version", length = 45)
    private String licenseVersion;

    @Column(name = "resource_type", length = 255)
    private String resourceType = "records";

    @Column(length = 45)
    private String provenance;

    @Column(name = "information_withheld", columnDefinition = "text")
    private String informationWithheld;

    @Column(name = "data_generalizations", columnDefinition = "text")
    private String dataGeneralizations;

    @Column(name = "permissions_document", columnDefinition = "text")
    private String permissionsDocument;

    @Column(name = "permissions_document_type", length = 23)
    private String permissionsDocumentType = "Other";

    @Column(name = "risk_assessment")
    private boolean riskAssessment;

    @Column
    private boolean filed;

    @Column(length = 45)
    private String status = "identified";

    @Column(name = "harvesting_notes", columnDefinition = "text")
    private String harvestingNotes;

    @Column(name = "mobilisation_notes", columnDefinition = "text")
    private String mobilisationNotes;

    @Column(name = "harvest_frequency")
    private int harvestFrequency = 0;

    @Column(name = "last_checked")
    private LocalDateTime lastChecked;

    @Column(name = "data_currency")
    private LocalDateTime dataCurrency;

    @Column(name = "connection_parameters", columnDefinition = "text")
    private String connectionParameters;

    @Column(name = "image_metadata", columnDefinition = "text")
    private String imageMetadata;

    @Column(name = "default_darwin_core_values", columnDefinition = "text")
    private String defaultDarwinCoreValues;

    @Column(name = "download_limit")
    private int downloadLimit = 0;

    @Column(name = "content_types", length = 2048)
    private String contentTypes;

    @Column(name = "public_archive_available")
    private boolean publicArchiveAvailable;

    @Column(name = "gbif_dataset")
    private Boolean gbifDataset = false;

    @Column(name = "is_shareable_withgbif")
    private Boolean isShareableWithGBIF = true;

    @ManyToOne
    @JoinColumn(name = "data_provider_id")
    private DataProvider dataProvider;

    @ManyToOne
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Column(name = "make_contact_public")
    private Boolean makeContactPublic = true;

    @Column(name = "is_private")
    private Boolean isPrivate = false;

    @Column(name = "repatriation_country", length = 255)
    private String repatriationCountry;

    @Column(name = "data_collection_protocol_name", columnDefinition = "text")
    private String dataCollectionProtocolName;

    @Column(name = "data_collection_protocol_doc", columnDefinition = "text")
    private String dataCollectionProtocolDoc;

    @Column(name = "suitable_for", columnDefinition = "text")
    private String suitableFor;

    @Column(name = "suitable_for_other_detail", columnDefinition = "text")
    private String suitableForOtherDetail;

    @Column(columnDefinition = "text")
    private String purpose;

    @Column(name = "geographic_description", columnDefinition = "text")
    private String geographicDescription;

    @Column(name = "west_bounding_coordinate", length = 255)
    private String westBoundingCoordinate;

    @Column(name = "east_bounding_coordinate", length = 255)
    private String eastBoundingCoordinate;

    @Column(name = "north_bounding_coordinate", length = 255)
    private String northBoundingCoordinate;

    @Column(name = "south_bounding_coordinate", length = 255)
    private String southBoundingCoordinate;

    @Column(name = "begin_date", length = 255)
    private String beginDate;

    @Column(name = "end_date", length = 255)
    private String endDate;

    @Column(name = "method_step_description", columnDefinition = "text")
    private String methodStepDescription;

    @Column(name = "quality_control_description", columnDefinition = "text")
    private String qualityControlDescription;

    @Column(name = "gbif_doi", length = 255)
    private String gbifDoi;

    @Column(name = "created_byid")
    private String createdByID;

    @ManyToMany
    @JoinTable(
            name = "data_resource_institution",
            joinColumns = @JoinColumn(name = "data_resource_id"),
            inverseJoinColumns = @JoinColumn(name = "institution_id")
    )
    private Set<Institution> consumerInstitutions = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "data_resource_collection",
            joinColumns = @JoinColumn(name = "data_resource_id"),
            inverseJoinColumns = @JoinColumn(name = "collection_id")
    )
    private Set<Collection> consumerCollections = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "data_resource_external_identifier",
            joinColumns = @JoinColumn(name = "data_resource_external_identifiers_id"),
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
        return "dataResource";
    }
}
