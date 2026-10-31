package org.example.springwithai.alien;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlienResponseTest {

    @Test
    void fromCopiesAllEntityFields() {
        AlienResponse response = AlienResponse.from(new Alien(1L, "Zorg", "Mars", "Green", "Humanoid"));

        assertThat(response).isEqualTo(new AlienResponse(1L, "Zorg", "Mars", "Green", "Humanoid"));
    }

    @Test
    void fromKeepsNullIdOfUnsavedEntity() {
        assertThat(AlienResponse.from(new Alien("Zorg", "Mars", "Green", "Humanoid")).id()).isNull();
    }
}
