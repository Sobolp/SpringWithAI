package org.example.springwithai.alien;

import java.util.List;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for aliens.
 * <p>
 * OpenAPI note: once a method declares any response ({@code @ApiResponse}, {@code @NotFoundResponse},
 * {@code @BadRequestResponse}), springdoc no longer infers its default 200 response. Methods without
 * {@code @ResponseStatus} must therefore declare their 200 explicitly; 201 and 204 come from {@code @ResponseStatus}.
 */
@RestController
@RequestMapping("/api/aliens")
@RequiredArgsConstructor
public class AlienController {

    private final AlienService service;

    @GetMapping
    public List<AlienResponse> list(@RequestParam(required = false) String type,
                                    @RequestParam(required = false) String origin) {
        return service.search(type, origin).stream().map(AlienResponse::from).toList();
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "The alien")
    @NotFoundResponse
    public AlienResponse get(@PathVariable Long id) {
        return AlienResponse.from(service.findById(id));
    }

    @PostMapping
    @BadRequestResponse
    @ResponseStatus(HttpStatus.CREATED)
    public AlienResponse create(@Valid @RequestBody AlienRequest request) {
        return AlienResponse.from(service.create(request));
    }

    @PutMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "The updated alien")
    @BadRequestResponse
    @NotFoundResponse
    public AlienResponse update(@PathVariable Long id, @Valid @RequestBody AlienRequest request) {
        return AlienResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @NotFoundResponse
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
