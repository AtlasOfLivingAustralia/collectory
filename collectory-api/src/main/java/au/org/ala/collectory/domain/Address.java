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
public class Address {

    @Column(nullable = true)
    private String street;

    @Column(name = "post_box", nullable = true)
    private String postBox;

    @Column(nullable = true)
    private String city;

    @Column(nullable = true)
    private String state;

    @Column(nullable = true)
    private String postcode;

    @Column(nullable = true)
    private String country;
}
