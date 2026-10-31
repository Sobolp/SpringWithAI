package org.example.springwithai.alien;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class AlienRepositoryTest {

    @Autowired
    private AlienRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void seedDummyData() {
        new AlienDataLoader(repository).run();
    }

    @Test
    void loaderSeedsAllDummyAliens() {
        assertThat(repository.findAll())
                .hasSize(AlienDataLoader.DUMMY_ALIENS.size())
                .extracting(Alien::getName)
                .contains("Zorg", "Xylo", "Glorp");
    }

    @Test
    void loaderDoesNotSeedTwice() {
        new AlienDataLoader(repository).run();

        assertThat(repository.count()).isEqualTo(AlienDataLoader.DUMMY_ALIENS.size());
    }

    @Test
    void findByTypeIgnoresCase() {
        assertThat(repository.findByTypeIgnoreCase("humanoid"))
                .extracting(Alien::getName)
                .containsExactlyInAnyOrder("Zorg", "Quarx");
    }

    @Test
    void findByOriginIgnoresCase() {
        assertThat(repository.findByOriginIgnoreCase("MARS"))
                .singleElement()
                .extracting(Alien::getColor)
                .isEqualTo("Green");
    }

    @Test
    void findByTypeAndOriginRequiresBothToMatch() {
        assertThat(repository.findByTypeIgnoreCaseAndOriginIgnoreCase("humanoid", "mars"))
                .extracting(Alien::getName)
                .containsExactly("Zorg");
    }

    @Test
    void findByTypeAndOriginReturnsEmptyWhenOnlyOneMatches() {
        assertThat(repository.findByTypeIgnoreCaseAndOriginIgnoreCase("Humanoid", "Titan")).isEmpty();
    }

    @Test
    void savingBlankEntityIsRejectedEvenWithoutTheApi() {
        long before = repository.count();

        assertThatThrownBy(() -> repository.saveAndFlush(new Alien("", "Mars", "Green", "Humanoid")))
                .isInstanceOfSatisfying(ConstraintViolationException.class, e -> assertThat(e.getConstraintViolations())
                        .singleElement()
                        .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("name")));

        // Hibernate forbids reusing a session after this exception; detach the rejected entity before querying.
        entityManager.clear();
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void paddedRequestValueIsFoundByItsOwnFilter() {
        repository.save(new AlienRequest("Nova", " Pluto ", "White", "Humanoid ").toEntity());

        assertThat(repository.findByTypeIgnoreCaseAndOriginIgnoreCase("humanoid", "pluto"))
                .extracting(Alien::getName)
                .containsExactly("Nova");
    }
}
