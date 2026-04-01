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
@Table(name = "attribution")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Attribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @NotNull
    @Column(length = 256, nullable = false)
    private String name;

    @NotNull
    @Column(length = 20, nullable = false)
    private String uid;

    @Column(length = 256)
    private String url;
}
