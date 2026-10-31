package org.example.springwithai.alien;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the database with dummy aliens on startup when it is empty.
 * Only active in the {@code dev} profile (the default, see application.properties).
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class AlienDataLoader implements CommandLineRunner {

    record Seed(String name, String origin, String color, String type) {

        Alien toEntity() {
            return new Alien(name, origin, color, type);
        }
    }

    static final List<Seed> DUMMY_ALIENS = List.of(
            new Seed("Zorg", "Mars", "Green", "Humanoid"),
            new Seed("Xylo", "Andromeda", "Blue", "Reptilian"),
            new Seed("Blib", "Europa", "Purple", "Amphibian"),
            new Seed("Kraal", "Betelgeuse", "Red", "Insectoid"),
            new Seed("Nimbus", "Neptune", "Silver", "Gaseous"),
            new Seed("Quarx", "Proxima Centauri", "Orange", "Humanoid"),
            new Seed("Vexa", "Sirius", "Gold", "Energy"),
            new Seed("Glorp", "Titan", "Yellow", "Blob")
    );

    private final AlienRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.count() == 0) {
            repository.saveAll(DUMMY_ALIENS.stream().map(Seed::toEntity).toList());
        }
    }
}
