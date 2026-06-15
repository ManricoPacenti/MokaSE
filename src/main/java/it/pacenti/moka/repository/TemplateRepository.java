package it.pacenti.moka.repository;

import it.pacenti.moka.scheduling.WeeklyScheduleTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Contract for storing and retrieving weekly schedule templates.
 */
public interface TemplateRepository {

    void save(String name, WeeklyScheduleTemplate template);

    Optional<WeeklyScheduleTemplate> findByName(String name);

    List<String> findAllNames();

    boolean deleteByName(String name);
}
