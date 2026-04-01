package au.org.ala.collectory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "contact")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(length = 20)
    private String title;

    @Column(name = "first_name", length = 255)
    private String firstName;

    @Column(name = "last_name", length = 255)
    private String lastName;

    @Column(length = 45)
    private String phone;

    @Column(length = 45)
    private String mobile;

    @Column(length = 128)
    private String email;

    @Column(length = 45)
    private String fax;

    @Column(name = "organization_name", length = 255)
    private String organizationName;

    @Column(name = "position_name", length = 255)
    private String positionName;

    @Column(name = "user_id", length = 255)
    private String userId;

    @Column(length = 1024)
    private String notes;

    @Column
    private boolean publish = true;

    @CreatedDate
    @Column(name = "date_created")
    private LocalDateTime dateCreated;

    @LastModifiedDate
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "user_last_modified", length = 256)
    private String userLastModified;

    /**
     * Builds the display name, falling back through available fields.
     */
    public String buildName() {
        if (lastName != null && !lastName.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (title != null && !title.isEmpty()) sb.append(title).append(" ");
            if (firstName != null && !firstName.isEmpty()) sb.append(firstName).append(" ");
            sb.append(lastName);
            return sb.toString().trim();
        } else if (organizationName != null && !organizationName.isEmpty()) {
            return organizationName;
        } else if (positionName != null && !positionName.isEmpty()) {
            return positionName;
        } else if (email != null && !email.isEmpty()) {
            return email;
        } else if (userId != null && !userId.isEmpty()) {
            return userId;
        } else if (phone != null && !phone.isEmpty()) {
            return phone;
        } else if (mobile != null && !mobile.isEmpty()) {
            return mobile;
        } else if (fax != null && !fax.isEmpty()) {
            return fax;
        }
        return "";
    }
}
