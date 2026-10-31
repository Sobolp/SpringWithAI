package org.example.springwithai.alien;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlienService {

    private final AlienRepository repository;

    public List<Alien> findAll() {
        return repository.findAll();
    }

    public Alien findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new AlienNotFoundException(id));
    }

    /**
     * Finds aliens matching every given filter (case-insensitive).
     * A {@code null} or blank filter is ignored; with no filters, all aliens are returned.
     */
    public List<Alien> search(String type, String origin) {
        boolean byType = StringUtils.hasText(type);
        boolean byOrigin = StringUtils.hasText(origin);
        if (byType && byOrigin) {
            return repository.findByTypeIgnoreCaseAndOriginIgnoreCase(type.strip(), origin.strip());
        }
        if (byType) {
            return repository.findByTypeIgnoreCase(type.strip());
        }
        if (byOrigin) {
            return repository.findByOriginIgnoreCase(origin.strip());
        }
        return repository.findAll();
    }

    @Transactional
    public Alien create(AlienRequest request) {
        return repository.save(request.toEntity());
    }

    /** Full replacement (PUT semantics): every field is overwritten with the request's values. */
    @Transactional
    public Alien update(Long id, AlienRequest request) {
        Alien alien = findById(id);
        alien.setName(request.name());
        alien.setOrigin(request.origin());
        alien.setColor(request.color());
        alien.setType(request.type());
        return alien;
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(findById(id));
    }
}
