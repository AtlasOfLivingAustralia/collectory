package au.org.ala.collectory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Image {

    @Column(nullable = true)
    private String file;

    @Column(nullable = true)
    private String caption;

    @Column(nullable = true)
    private String attribution;

    @Column(nullable = true)
    private String copyright;
}
