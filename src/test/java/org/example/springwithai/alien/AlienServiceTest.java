package org.example.springwithai.alien;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlienServiceTest {

    @Mock
    private AlienRepository repository;

    @InjectMocks
    private AlienService service;

    // Alien is a mutable entity: build fresh instances per test so no test can leak state into another.
    private static Alien zorg() {
        return new Alien(1L, "Zorg", "Mars", "Green", "Humanoid");
    }

    private static Alien xylo() {
        return new Alien(2L, "Xylo", "Andromeda", "Blue", "Reptilian");
    }

    @Nested
    class Queries {

        @Test
        void findAllReturnsAllAliens() {
            Alien zorg = zorg();
            Alien xylo = xylo();
            when(repository.findAll()).thenReturn(List.of(zorg, xylo));

            assertThat(service.findAll()).containsExactly(zorg, xylo);
        }

        @Test
        void findAllReturnsEmptyListWhenNoAliens() {
            when(repository.findAll()).thenReturn(List.of());

            assertThat(service.findAll()).isEmpty();
        }

        @Test
        void findByIdReturnsAlien() {
            Alien zorg = zorg();
            when(repository.findById(1L)).thenReturn(Optional.of(zorg));

            assertThat(service.findById(1L)).isSameAs(zorg);
        }

        @Test
        void findByIdThrowsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(99L))
                    .isInstanceOf(AlienNotFoundException.class)
                    .hasMessage("Alien not found: 99");
        }

        @Test
        void searchByTypeOnly() {
            Alien zorg = zorg();
            when(repository.findByTypeIgnoreCase("humanoid")).thenReturn(List.of(zorg));

            assertThat(service.search("humanoid", null)).containsExactly(zorg);
        }

        @Test
        void searchByOriginOnly() {
            Alien xylo = xylo();
            when(repository.findByOriginIgnoreCase("Andromeda")).thenReturn(List.of(xylo));

            assertThat(service.search(null, "Andromeda")).containsExactly(xylo);
        }

        @Test
        void searchByTypeAndOriginAppliesBothFilters() {
            Alien zorg = zorg();
            when(repository.findByTypeIgnoreCaseAndOriginIgnoreCase("Humanoid", "Mars")).thenReturn(List.of(zorg));

            assertThat(service.search("Humanoid", "Mars")).containsExactly(zorg);
        }

        @Test
        void searchStripsSurroundingWhitespace() {
            Alien zorg = zorg();
            when(repository.findByTypeIgnoreCase("Humanoid")).thenReturn(List.of(zorg));

            assertThat(service.search("  Humanoid ", null)).containsExactly(zorg);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        void searchIgnoresBlankFilters(String blank) {
            Alien zorg = zorg();
            Alien xylo = xylo();
            when(repository.findAll()).thenReturn(List.of(zorg, xylo));

            assertThat(service.search(blank, blank)).containsExactly(zorg, xylo);
        }

        @Test
        void searchIgnoresBlankTypeButKeepsOrigin() {
            Alien zorg = zorg();
            when(repository.findByOriginIgnoreCase("Mars")).thenReturn(List.of(zorg));

            assertThat(service.search("", "Mars")).containsExactly(zorg);
        }
    }

    @Nested
    class Commands {

        @Test
        void createIgnoresClientSuppliedIdAndSaves() {
            Alien input = new Alien(42L, "Blib", "Europa", "Purple", "Amphibian");
            when(repository.save(any(Alien.class))).thenAnswer(inv -> inv.getArgument(0));

            Alien saved = service.create(input);

            assertThat(saved)
                    .extracting(Alien::getId, Alien::getName, Alien::getOrigin, Alien::getColor, Alien::getType)
                    .containsExactly(null, "Blib", "Europa", "Purple", "Amphibian");
        }

        @Test
        void updateCopiesAllFieldsAndKeepsId() {
            when(repository.findById(1L)).thenReturn(Optional.of(zorg()));

            Alien updated = service.update(1L, new Alien(7L, "Zorgon", "Venus", "Teal", "Cyborg"));

            assertThat(updated)
                    .extracting(Alien::getId, Alien::getName, Alien::getOrigin, Alien::getColor, Alien::getType)
                    .containsExactly(1L, "Zorgon", "Venus", "Teal", "Cyborg");
        }

        @Test
        void updateThrowsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99L, zorg()))
                    .isInstanceOf(AlienNotFoundException.class);
        }

        @Test
        void deleteRemovesExistingAlien() {
            Alien zorg = zorg();
            when(repository.findById(1L)).thenReturn(Optional.of(zorg));

            service.delete(1L);

            verify(repository).delete(zorg);
        }

        @Test
        void deleteThrowsWhenMissingAndDeletesNothing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(AlienNotFoundException.class);
            verify(repository, never()).delete(any());
        }
    }
}
