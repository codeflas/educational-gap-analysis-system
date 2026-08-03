package ie.ul.egas.competency.infrastructure.web.dto;

import ie.ul.egas.competency.domain.model.FrameworkSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Registration payload. Bean Validation here covers transport shape only (tier 1 → HTTP 400);
 * model-level rules (uniqueness, reference resolution, acyclicity) are conformance validation
 * (tier 2 → HTTP 422) and are deliberately NOT duplicated as annotations — the metamodel and
 * its validator are the single source of truth for them.
 *
 * <p>Optional nested lists are null-normalised in compact constructors; mandatory ones
 * ({@code areas}, {@code competencies}) are left untouched so {@code @NotEmpty} reports a clean
 * validation error instead of a construction-time NPE.
 */
public record RegisterFrameworkRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 50) String version,
        @Size(max = 4000) String description,
        @NotNull FrameworkSource source,
        List<@Valid LevelRequest> levels,
        @NotEmpty List<@Valid AreaRequest> areas) {

    public RegisterFrameworkRequest {
        levels = levels == null ? List.of() : List.copyOf(levels);
    }

    public record LevelRequest(
            @NotBlank @Size(max = 50) String code,
            @Size(max = 200) String name,
            @PositiveOrZero int ordinal) {
    }

    public record AreaRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotEmpty List<@Valid CompetencyRequest> competencies) {
    }

    public record CompetencyRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            List<String> prerequisites,
            List<@Valid LevelDescriptorRequest> levelDescriptors) {

        public CompetencyRequest {
            prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
            levelDescriptors = levelDescriptors == null ? List.of() : List.copyOf(levelDescriptors);
        }
    }

    public record LevelDescriptorRequest(
            @NotBlank @Size(max = 50) String levelCode,
            @Size(max = 2000) String descriptor) {
    }
}
