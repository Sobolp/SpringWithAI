package org.example.springwithai.alien;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AlienController.class)
class AlienControllerTest {

    private static final String ZORG_JSON = """
            {"name":"Zorg","origin":"Mars","color":"Green","type":"Humanoid"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AlienService service;

    private static Alien zorg() {
        return new Alien(1L, "Zorg", "Mars", "Green", "Humanoid");
    }

    @Test
    void listReturnsAllAliensWithoutFilters() {
        when(service.search(null, null))
                .thenReturn(List.of(zorg(), new Alien(2L, "Xylo", "Andromeda", "Blue", "Reptilian")));

        assertThat(mvc.get().uri("/api/aliens"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"name":"Zorg","origin":"Mars"}, {"name":"Xylo","origin":"Andromeda"}]
                        """)
                .extractingPath("$[*].name").asArray().containsExactly("Zorg", "Xylo");
    }

    @Test
    void listPassesBothFiltersToService() {
        when(service.search("Humanoid", "Mars")).thenReturn(List.of(zorg()));

        assertThat(mvc.get().uri("/api/aliens").param("type", "Humanoid").param("origin", "Mars"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name").asArray().containsExactly("Zorg");
    }

    @Test
    void listPassesSingleFilterToService() {
        when(service.search(null, "Mars")).thenReturn(List.of(zorg()));

        assertThat(mvc.get().uri("/api/aliens").param("origin", "Mars"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].origin").asArray().containsExactly("Mars");
    }

    @Test
    void getReturnsAlien() {
        when(service.findById(1L)).thenReturn(zorg());

        assertThat(mvc.get().uri("/api/aliens/1"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"id":1,"color":"Green"}
                        """);
    }

    @Test
    void getReturns404WhenMissing() {
        when(service.findById(99L)).thenThrow(new AlienNotFoundException(99L));

        assertThat(mvc.get().uri("/api/aliens/99"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private static final AlienRequest ZORG_REQUEST = new AlienRequest("Zorg", "Mars", "Green", "Humanoid");

    @Test
    void createReturns201WithSavedAlien() {
        when(service.create(ZORG_REQUEST)).thenReturn(zorg());

        assertThat(mvc.post().uri("/api/aliens").contentType(MediaType.APPLICATION_JSON).content(ZORG_JSON))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"id":1,"name":"Zorg"}
                        """);
    }

    @Test
    void createIgnoresClientSuppliedId() {
        when(service.create(ZORG_REQUEST)).thenReturn(zorg());

        assertThat(mvc.post().uri("/api/aliens").contentType(MediaType.APPLICATION_JSON).content("""
                        {"id":42,"name":"Zorg","origin":"Mars","color":"Green","type":"Humanoid"}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.id").isEqualTo(1);
    }

    @Test
    void createStripsWhitespaceBeforeCallingService() {
        when(service.create(ZORG_REQUEST)).thenReturn(zorg());

        assertThat(mvc.post().uri("/api/aliens").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":" Zorg","origin":"Mars ","color":"  Green  ","type":"Humanoid\\t"}
                        """))
                .hasStatus(HttpStatus.CREATED);

        verify(service).create(ZORG_REQUEST);
    }

    @Test
    void createAcceptsValueAtMaxLength() {
        String name = "x".repeat(Alien.MAX_LENGTH);
        when(service.create(any(AlienRequest.class))).thenReturn(zorg());

        assertThat(mvc.post().uri("/api/aliens").contentType(MediaType.APPLICATION_JSON).content(json(name)))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createRejectsValueOverMaxLengthWith400() {
        String name = "x".repeat(Alien.MAX_LENGTH + 1);

        assertThat(mvc.post().uri("/api/aliens").contentType(MediaType.APPLICATION_JSON).content(json(name)))
                .hasStatus(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(service);
    }

    @Test
    void responseContainsExactlyTheApiFields() {
        when(service.findById(1L)).thenReturn(zorg());

        assertThat(mvc.get().uri("/api/aliens/1"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"id":1,"name":"Zorg","origin":"Mars","color":"Green","type":"Humanoid"}
                        """);
    }

    @Test
    void updatePassesPathIdAndRequestToService() {
        when(service.update(1L, ZORG_REQUEST)).thenReturn(zorg());

        assertThat(mvc.put().uri("/api/aliens/1").contentType(MediaType.APPLICATION_JSON).content(ZORG_JSON))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.name").isEqualTo("Zorg");
    }

    @Test
    void updateRejectsValueOverMaxLengthWith400() {
        String name = "x".repeat(Alien.MAX_LENGTH + 1);

        assertThat(mvc.put().uri("/api/aliens/1").contentType(MediaType.APPLICATION_JSON).content(json(name)))
                .hasStatus(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(service);
    }

    private static String json(String name) {
        return """
                {"name":"%s","origin":"Mars","color":"Green","type":"Humanoid"}
                """.formatted(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            """
            {"name":"","origin":"Mars","color":"Green","type":"Humanoid"}
            """,
            """
            {"name":"Zorg","origin":"Mars","color":"Green"}
            """
    })
    void createRejectsInvalidAlienWith400(String body) {
        assertThat(mvc.post().uri("/api/aliens").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(service);
    }

    @Test
    void updateRejectsPartialBodyWith400() {
        assertThat(mvc.put().uri("/api/aliens/1").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Zorgon"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(service);
    }

    @Test
    void deleteReturns204() {
        assertThat(mvc.delete().uri("/api/aliens/1"))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(service).delete(1L);
    }

    @Test
    void deleteReturns404WhenMissing() {
        doThrow(new AlienNotFoundException(99L)).when(service).delete(99L);

        assertThat(mvc.delete().uri("/api/aliens/99"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
