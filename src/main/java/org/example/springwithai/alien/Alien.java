package org.example.springwithai.alien;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Alien {

    /** Column length of every text field; {@link AlienRequest} validates against the same limit. */
    public static final int MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = MAX_LENGTH)
    @Column(length = MAX_LENGTH)
    private String name;

    @NotBlank
    @Size(max = MAX_LENGTH)
    @Column(length = MAX_LENGTH)
    private String origin;

    @NotBlank
    @Size(max = MAX_LENGTH)
    @Column(length = MAX_LENGTH)
    private String color;

    @NotBlank
    @Size(max = MAX_LENGTH)
    @Column(length = MAX_LENGTH)
    private String type;

    public Alien(String name, String origin, String color, String type) {
        this(null, name, origin, color, type);
    }
}
