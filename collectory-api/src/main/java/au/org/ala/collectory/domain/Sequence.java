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
@Table(name = "\"sequence\"")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Sequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @NotNull
    @Column(length = 255, nullable = false)
    private String name;

    @Column(name = "next_id")
    private long nextId;

    @NotNull
    @Column(length = 255, nullable = false)
    private String prefix;
}
