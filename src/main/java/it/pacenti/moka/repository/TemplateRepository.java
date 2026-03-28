package it.pacenti.moka.repository;

import it.pacenti.moka.scheduling.WeeklyScheduleTemplate;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository {

    void save(String name, WeeklyScheduleTemplate template);

    Optional<WeeklyScheduleTemplate> findByName(String name);

    List<String> findAllNames();

    boolean deleteByName(String name);
}
