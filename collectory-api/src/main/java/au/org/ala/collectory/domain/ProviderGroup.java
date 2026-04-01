package au.org.ala.collectory.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;


import java.time.LocalDateTime;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class ProviderGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 256)
    private String guid;

    @NotBlank
    @Column(length = 20, nullable = false)
    private String uid;

    @NotBlank
    @Column(length = 1024, nullable = false)
    private String name;

    @Column(length = 45)
    private String acronym;

    @Column(name = "pub_short_description", length = 100)
    private String pubShortDescription;

    @Column(name = "pub_description", columnDefinition = "text")
    private String pubDescription;

    @Column(name = "tech_description", columnDefinition = "text")
    private String techDescription;

    @Column(columnDefinition = "text")
    private String focus;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "address_street")),
            @AttributeOverride(name = "postBox", column = @Column(name = "address_post_box")),
            @AttributeOverride(name = "city", column = @Column(name = "address_city")),
            @AttributeOverride(name = "state", column = @Column(name = "address_state")),
            @AttributeOverride(name = "postcode", column = @Column(name = "address_postcode")),
            @AttributeOverride(name = "country", column = @Column(name = "address_country"))
    })
    private Address address;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column
    private String altitude;

    @Column(length = 45)
    private String state;

    @Column(name = "website_url", length = 256)
    private String websiteUrl;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "file", column = @Column(name = "logo_ref_file")),
            @AttributeOverride(name = "caption", column = @Column(name = "logo_ref_caption")),
            @AttributeOverride(name = "attribution", column = @Column(name = "logo_ref_attribution")),
            @AttributeOverride(name = "copyright", column = @Column(name = "logo_ref_copyright"))
    })
    private Image logoRef;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "file", column = @Column(name = "image_ref_file")),
            @AttributeOverride(name = "caption", column = @Column(name = "image_ref_caption")),
            @AttributeOverride(name = "attribution", column = @Column(name = "image_ref_attribution")),
            @AttributeOverride(name = "copyright", column = @Column(name = "image_ref_copyright"))
    })
    private Image imageRef;

    @Column(length = 256)
    private String email;

    @Column(length = 200)
    private String phone;

    @Column(name = "isalapartner")
    private boolean isALAPartner;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "network_membership", columnDefinition = "text")
    private String networkMembership;

    @Column(length = 256)
    private String attributions;

    @Column(name = "taxonomy_hints", columnDefinition = "text")
    private String taxonomyHints;

    @Column(columnDefinition = "text")
    private String keywords;

    @Column(name = "gbif_registry_key", length = 36)
    private String gbifRegistryKey;

    @CreatedDate
    @Column(name = "date_created")
    private LocalDateTime dateCreated;

    @LastModifiedDate
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "user_last_modified", length = 255)
    private String userLastModified;

    @Version
    private Long version;

    // Abstract methods
    public abstract String entityType();
    public abstract Long dbId();
    public abstract String urlForm();

    // Utility methods ported from Groovy trait

    private static final int ABSTRACT_LENGTH = 250;

    /**
     * Returns an abbreviated version of the description.
     * Accumulates text from pubDescription, techDescription, or focus.
     * Breaks at first newline, includes second token if first is short (<40 chars).
     * Trims at a word/sentence boundary if too long.
     */
    public String makeAbstract(int length) {
        // accumulate text
        String chunk = "";
        if (pubDescription != null && !pubDescription.isEmpty()) {
            chunk = pubDescription;
        } else if (techDescription != null && !techDescription.isEmpty()) {
            chunk = techDescription;
        } else if (focus != null && !focus.isEmpty()) {
            chunk = focus;
        }
        if (chunk.isEmpty()) {
            return "";
        }
        // break at first newline
        String[] chunks = chunk.split("\n");
        chunk = chunks[0];
        // add second token if first is short
        if (chunk.length() < 40 && chunks.length > 1) {
            chunk += " " + chunks[1];
        }
        // trim if still too long
        if (chunk.length() < length) {
            return chunk;
        } else {
            return trimLength(chunk, length);
        }
    }

    public String makeAbstract() {
        return makeAbstract(ABSTRACT_LENGTH);
    }

    /**
     * Trims a string to the given length, breaking at the last sentence or word boundary.
     */
    private String trimLength(String trimString, int stringLength) {
        String ellipsis = "...";
        if (trimString == null || trimString.length() <= stringLength) {
            return trimString;
        }
        trimString = trimString.substring(0, stringLength - ellipsis.length());
        // try to break at a period or space
        int lastPeriod = trimString.lastIndexOf('.');
        int lastSpace = trimString.lastIndexOf(' ');
        int breakAt = Math.max(lastPeriod, lastSpace);
        if (breakAt > 0) {
            trimString = trimString.substring(0, breakAt);
        }
        return trimString + ellipsis;
    }

    /**
     * Returns true if this entity has enough location data to be mapped.
     */
    public boolean canBeMapped() {
        return latitude != null && latitude != 0.0 && latitude != -1.0
                && longitude != null && longitude != 0.0 && longitude != -1.0;
    }

    /**
     * Generates a permalink for the entity.
     */
    public String generatePermalink() {
        return uid;
    }

    /**
     * Builds a URI from the base server URL and uid.
     */
    public String buildUri(String baseUrl) {
        return baseUrl + "/" + urlForm() + "/" + uid;
    }

    /**
     * Builds the public URL for this entity.
     */
    public String buildPublicUrl(String baseUrl) {
        return baseUrl + "/public/show/" + uid;
    }

    /**
     * Builds the logo URL for this entity.
     */
    public String buildLogoUrl(String baseUrl) {
        if (logoRef != null && logoRef.getFile() != null && !logoRef.getFile().isEmpty()) {
            return baseUrl + "/data/" + urlForm() + "/" + logoRef.getFile();
        }
        return null;
    }
}
