package ie.ul.egas.competency.application;

import ie.ul.egas.competency.domain.model.FrameworkSource;

import java.util.List;

/**
 * Use-case input for framework registration. Pure records, framework-free: transport concerns
 * (Jackson, Bean Validation) stay in the web adapter, so this type — and everything beneath it —
 * is reusable from any future driving adapter (bulk import, M2M transformation pipeline in W10).
 * Nested collections are null-normalised so downstream code never branches on null.
 */
public record RegisterFrameworkCommand(
        String name,
        String version,
        String description,
        FrameworkSource source,
        List<Level> levels,
        List<Area> areas) {

    public RegisterFrameworkCommand {
        levels = levels == null ? List.of() : List.copyOf(levels);
        areas = areas == null ? List.of() : List.copyOf(areas);
    }

    public record Level(String code, String name, int ordinal) {
    }

    public record Area(String code, String name, String description, List<Competency> competencies) {
        public Area {
            competencies = competencies == null ? List.of() : List.copyOf(competencies);
        }
    }

    public record Competency(String code, String name, String description,
                             List<String> prerequisites, List<LevelDescriptor> levelDescriptors) {
        public Competency {
            prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
            levelDescriptors = levelDescriptors == null ? List.of() : List.copyOf(levelDescriptors);
        }
    }

    public record LevelDescriptor(String levelCode, String descriptor) {
    }
}
