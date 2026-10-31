package org.example.springwithai.alien;

import java.util.List;

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

@RestController
@RequestMapping("/api/aliens")
@RequiredArgsConstructor
public class AlienController {

    private final AlienService service;

    @GetMapping
    public List<Alien> list(@RequestParam(required = false) String type,
                            @RequestParam(required = false) String origin) {
        return service.search(type, origin);
    }

    @GetMapping("/{id}")
    public Alien get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Alien create(@Valid @RequestBody Alien alien) {
        return service.create(alien);
    }

    @PutMapping("/{id}")
    public Alien update(@PathVariable Long id, @Valid @RequestBody Alien alien) {
        return service.update(id, alien);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
