package org.example.springwithai.alien;

/**
 * Alien as returned by the API. Decoupled from the {@link Alien} entity so persistence changes
 * don't leak into the JSON contract.
 */
public record AlienResponse(Long id, String name, String origin, String color, String type) {

    static AlienResponse from(Alien alien) {
        return new AlienResponse(alien.getId(), alien.getName(), alien.getOrigin(), alien.getColor(), alien.getType());
    }
}
