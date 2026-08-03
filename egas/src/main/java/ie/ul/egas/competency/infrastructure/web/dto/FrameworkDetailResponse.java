package ie.ul.egas.competency.infrastructure.web.dto;

import ie.ul.egas.competency.domain.model.FrameworkSource;
import ie.ul.egas.competency.domain.model.ModelStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full framework representation: metadata plus the rendered model tree. */
public record FrameworkDetailResponse(
        UUID id,
        String name,
        String version,
        String description,
        FrameworkSource source,
        ModelStatus status,
        Instant registeredAt,
        List<LevelResponse> levels,
        List<AreaResponse> areas) {

    public record LevelResponse(String code, String name, int ordinal) {
    }

    public record AreaResponse(String code, String name, String description,
                               List<CompetencyResponse> competencies) {
    }

    /**
     * {@code id} is the derived competency identity (ADR-019 Amendment 1). It is exposed so a
     * client recording learner evidence obtains a real identifier instead of inventing one — until
     * it was published, nothing a caller could see corresponded to a competency any model would
     * recognise. Additive: existing consumers ignore the new field.
     */
    public record CompetencyResponse(UUID id, String code, String name, String description,
                                     List<String> prerequisites,
                                     List<LevelDescriptorResponse> levelDescriptors) {
    }

    public record LevelDescriptorResponse(String levelCode, String descriptor) {
    }
}
