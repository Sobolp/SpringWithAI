package org.example.springwithai.alien;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlienController.class)
class AlienControllerTest {

    private static final String ZORG_JSON = """
            {"name":"Zorg","origin":"Mars","color":"Green","type":"Humanoid"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AlienService service;

    private static Alien zorg() {
        return new Alien(1L, "Zorg", "Mars", "Green", "Humanoid");
    }

    @Test
    void listReturnsAllAliensWithoutFilters() throws Exception {
        when(service.search(null, null)).thenReturn(List.of(zorg(), new Alien(2L, "Xylo", "Andromeda", "Blue", "Reptilian")));

        mvc.perform(get("/api/aliens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Zorg"))
                .andExpect(jsonPath("$[1].origin").value("Andromeda"));
    }

    @Test
    void listPassesBothFiltersToService() throws Exception {
        when(service.search("Humanoid", "Mars")).thenReturn(List.of(zorg()));

        mvc.perform(get("/api/aliens").param("type", "Humanoid").param("origin", "Mars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Zorg"));
    }

    @Test
    void listPassesSingleFilterToService() throws Exception {
        when(service.search(null, "Mars")).thenReturn(List.of(zorg()));

        mvc.perform(get("/api/aliens").param("origin", "Mars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origin").value("Mars"));
    }

    @Test
    void getReturnsAlien() throws Exception {
        when(service.findById(1L)).thenReturn(zorg());

        mvc.perform(get("/api/aliens/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.color").value("Green"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(service.findById(99L)).thenThrow(new AlienNotFoundException(99L));

        mvc.perform(get("/api/aliens/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201WithSavedAlien() throws Exception {
        when(service.create(any(Alien.class))).thenReturn(zorg());

        mvc.perform(post("/api/aliens").contentType(MediaType.APPLICATION_JSON).content(ZORG_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Zorg"));
    }

    @Test
    void updateReturnsUpdatedAlien() throws Exception {
        when(service.update(eq(1L), any(Alien.class))).thenReturn(zorg());

        mvc.perform(put("/api/aliens/1").contentType(MediaType.APPLICATION_JSON).content(ZORG_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Zorg"));
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
    void createRejectsInvalidAlienWith400(String body) throws Exception {
        mvc.perform(post("/api/aliens").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void updateRejectsPartialBodyWith400() throws Exception {
        mvc.perform(put("/api/aliens/1").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Zorgon"}
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/aliens/1"))
                .andExpect(status().isNoContent());

        verify(service).delete(1L);
    }

    @Test
    void deleteReturns404WhenMissing() throws Exception {
        doThrow(new AlienNotFoundException(99L)).when(service).delete(99L);

        mvc.perform(delete("/api/aliens/99"))
                .andExpect(status().isNotFound());
    }
}
