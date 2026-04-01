package au.org.ala.collectory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "provider_map")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(exclude = {"collection", "institution", "collectionCodes", "institutionCodes"})
public class ProviderMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @OneToOne
    @JoinColumn(name = "collection_id", unique = true)
    private Collection collection;

    @ManyToOne
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Column
    private boolean exact;

    @Column(name = "match_any_collection_code")
    private boolean matchAnyCollectionCode;

    @Column(length = 255)
    private String warning;

    @ManyToMany
    @JoinTable(
            name = "provider_map_collection_codes",
            joinColumns = @JoinColumn(name = "provider_map_id"),
            inverseJoinColumns = @JoinColumn(name = "provider_code_id")
    )
    private Set<ProviderCode> collectionCodes = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "provider_map_institution_codes",
            joinColumns = @JoinColumn(name = "provider_map_id"),
            inverseJoinColumns = @JoinColumn(name = "provider_code_id")
    )
    private Set<ProviderCode> institutionCodes = new HashSet<>();

    @CreatedDate
    @Column(name = "date_created")
    private LocalDateTime dateCreated;

    @LastModifiedDate
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}
