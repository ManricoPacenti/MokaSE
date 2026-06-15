package it.pacenti.moka.persistence.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.pacenti.moka.persistence.json.model.TemplateData;
import it.pacenti.moka.persistence.json.model.TemplateDocument;
import it.pacenti.moka.persistence.json.model.TemplateSlotData;
import it.pacenti.moka.repository.TemplateRepository;
import it.pacenti.moka.scheduling.ShiftSlot;
import it.pacenti.moka.scheduling.TimeRange;
import it.pacenti.moka.scheduling.WeeklyScheduleTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JsonTemplateRepository implements TemplateRepository {

    private final Path filePath;
    private final ObjectMapper objectMapper;

    public JsonTemplateRepository(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "File path cannot be null");
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    @Override
    public void save(String name, WeeklyScheduleTemplate template) {
        String normalizedName = normalizeName(name);
        Objects.requireNonNull(template, "Template cannot be null");

        try {
            TemplateDocument document = readDocument();

            List<TemplateData> templates = new ArrayList<>(document.getTemplates());
            templates.removeIf(existing -> normalizeName(existing.getName()).equals(normalizedName));
            templates.add(toData(name.trim(), template));
            templates.sort(Comparator.comparing(TemplateData::getName, String.CASE_INSENSITIVE_ORDER));

            document.setTemplates(templates);
            writeDocument(document);

        } catch (IOException e) {
            throw new JsonPersistenceException("Failed to save template: " + name, e);
        }
    }

    @Override
    public Optional<WeeklyScheduleTemplate> findByName(String name) {
        String normalizedName = normalizeName(name);

        try {
            TemplateDocument document = readDocument();

            return document.getTemplates()
                    .stream()
                    .filter(template -> normalizeName(template.getName()).equals(normalizedName))
                    .findFirst()
                    .map(this::toDomain);

        } catch (IOException e) {
            throw new JsonPersistenceException("Failed to read template: " + name, e);
        }
    }

    @Override
    public List<String> findAllNames() {
        try {
            TemplateDocument document = readDocument();

            return document.getTemplates()
                    .stream()
                    .map(TemplateData::getName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

        } catch (IOException e) {
            throw new JsonPersistenceException("Failed to read template names", e);
        }
    }

    @Override
    public boolean deleteByName(String name) {
        String normalizedName = normalizeName(name);

        try {
            TemplateDocument document = readDocument();
            List<TemplateData> templates = new ArrayList<>(document.getTemplates());

            boolean removed = templates.removeIf(existing ->
                    normalizeName(existing.getName()).equals(normalizedName)
            );

            if (!removed) {
                return false;
            }

            document.setTemplates(templates);
            writeDocument(document);
            return true;

        } catch (IOException e) {
            throw new JsonPersistenceException("Failed to delete template: " + name, e);
        }
    }

    private TemplateDocument readDocument() throws IOException {
        ensureParentDirectoryExists();

        if (Files.notExists(filePath) || Files.size(filePath) == 0) {
            return new TemplateDocument();
        }

        return objectMapper.readValue(filePath.toFile(), TemplateDocument.class);
    }

    private void writeDocument(TemplateDocument document) throws IOException {
        ensureParentDirectoryExists();

        Path tempFile = Files.createTempFile(
                filePath.getParent(),
                "templates-",
                ".tmp"
        );

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), document);

            Files.move(
                    tempFile,
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException atomicMoveFailure) {
            Files.move(
                    tempFile,
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void ensureParentDirectoryExists() throws IOException {
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private TemplateData toData(String name, WeeklyScheduleTemplate template) {
        List<TemplateSlotData> slots = template.getSlots()
                .stream()
                .map(slot -> new TemplateSlotData(
                        slot.getDay(),
                        slot.getRange().getStart(),
                        slot.getRange().getEnd(),
                        slot.getRequiredSkill()
                ))
                .toList();

        return new TemplateData(name, template.getWeekStart(), slots);
    }

    private WeeklyScheduleTemplate toDomain(TemplateData data) {
        WeeklyScheduleTemplate template = new WeeklyScheduleTemplate(data.getWeekStart());

        if (data.getSlots() != null) {
            for (TemplateSlotData slotData : data.getSlots()) {
                template.addSlot(new ShiftSlot(
                        slotData.getDay(),
                        new TimeRange(slotData.getStart(), slotData.getEnd()),
                        slotData.getRequiredSkill()
                ));
            }
        }

        return template;
    }

    private String normalizeName(String name) {
        Objects.requireNonNull(name, "Template name cannot be null");
        String normalized = name.trim();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Template name cannot be blank");
        }

        return normalized.toLowerCase();
    }
}