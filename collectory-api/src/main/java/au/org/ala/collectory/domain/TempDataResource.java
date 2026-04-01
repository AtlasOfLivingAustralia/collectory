package au.org.ala.collectory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "temp_data_resource",
        indexes = @Index(name = "temp_data_resource_uid_idx", columnList = "uid"))
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TempDataResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(length = 20)
    private String uid;

    @Column(length = 1024)
    private String name;

    @Column(length = 256)
    private String email;

    @Column(name = "first_name", length = 255)
    private String firstName;

    @Column(name = "last_name", length = 255)
    private String lastName;

    @Column(name = "ala_id", length = 256)
    private String alaId;

    @CreatedDate
    @Column(name = "date_created")
    private LocalDateTime dateCreated;

    @LastModifiedDate
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "number_of_records")
    private int numberOfRecords;

    @Column(name = "webservice_url", length = 255)
    private String webserviceUrl;

    @Column(name = "ui_url", length = 255)
    private String uiUrl;

    @Column(length = 16)
    private String status = "draft";

    @Column(name = "is_contact_public")
    private Boolean isContactPublic;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "data_generalisations", columnDefinition = "text")
    private String dataGeneralisations;

    @Column(name = "information_withheld", columnDefinition = "text")
    private String informationWithheld;

    @Column(length = 10)
    private String license;

    @Column(columnDefinition = "text")
    private String citation;

    @Column(name = "source_file", length = 255)
    private String sourceFile;

    @Column(name = "prod_uid", length = 20)
    private String prodUid;

    @Column(name = "key_fields", length = 255)
    private String keyFields;

    @Column(name = "csv_separator", length = 10)
    private String csvSeparator;

    @Column(length = 45)
    private String type;
}
