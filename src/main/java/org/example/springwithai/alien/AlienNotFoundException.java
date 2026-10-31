package org.example.springwithai.alien;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AlienNotFoundException extends RuntimeException {

    public AlienNotFoundException(Long id) {
        super("Alien not found: " + id);
    }
}
