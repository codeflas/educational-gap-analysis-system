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

    public record CompetencyResponse(String code, String name, String description,
                                     List<String> prerequisites,
                                     List<LevelDescriptorResponse> levelDescriptors) {
    }

    public record LevelDescriptorResponse(String levelCode, String descriptor) {
    }
}
