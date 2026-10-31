package org.example.springwithai.alien;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or fully replacing an alien. Has no id: the id comes from the URL or is generated.
 * Values are stripped of surrounding whitespace on construction, before validation, so stored values match
 * the (also stripped) search filters.
 */
public record AlienRequest(
        @NotBlank @Size(max = Alien.MAX_LENGTH) String name,
        @NotBlank @Size(max = Alien.MAX_LENGTH) String origin,
        @NotBlank @Size(max = Alien.MAX_LENGTH) String color,
        @NotBlank @Size(max = Alien.MAX_LENGTH) String type) {

    public AlienRequest {
        name = strip(name);
        origin = strip(origin);
        color = strip(color);
        type = strip(type);
    }

    Alien toEntity() {
        return new Alien(name, origin, color, type);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
