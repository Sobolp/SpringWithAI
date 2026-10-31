package org.example.springwithai.alien;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlienRepository extends JpaRepository<Alien, Long> {

    List<Alien> findByTypeIgnoreCase(String type);

    List<Alien> findByOriginIgnoreCase(String origin);

    List<Alien> findByTypeIgnoreCaseAndOriginIgnoreCase(String type, String origin);
}
