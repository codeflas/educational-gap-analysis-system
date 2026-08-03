package ie.ul.egas.competency.infrastructure.web;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.competency.application.RegisterFrameworkCommand;
import ie.ul.egas.competency.domain.metamodel.CompetencyMetamodel;
import ie.ul.egas.competency.domain.model.CompetencyFramework;
import ie.ul.egas.competency.domain.model.FrameworkSummary;
import ie.ul.egas.competency.infrastructure.web.dto.FrameworkDetailResponse;
import ie.ul.egas.competency.infrastructure.web.dto.FrameworkSummaryResponse;
import ie.ul.egas.competency.infrastructure.web.dto.RegisterFrameworkRequest;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hand-written web mapping (MapStruct was considered and rejected: an annotation processor buys
 * little at this size and obscures the EObject traversal, which is the interesting part).
 * Rendering the detail response interprets the M1 graph through the metamodel façade — a small
 * model-to-text-style projection performed in the adapter, which is exactly where
 * representation concerns belong. Metadata comes from the typed descriptor, structure from the
 * model.
 */
@Component
class FrameworkWebMapper {

    private final CompetencyMetamodel mm = CompetencyMetamodel.instance();

    RegisterFrameworkCommand toCommand(RegisterFrameworkRequest request) {
        return new RegisterFrameworkCommand(
                request.name(),
                request.version(),
                request.description(),
                request.source(),
                request.levels().stream()
                        .map(l -> new RegisterFrameworkCommand.Level(l.code(), l.name(), l.ordinal()))
                        .toList(),
                request.areas().stream()
                        .map(a -> new RegisterFrameworkCommand.Area(
                                a.code(), a.name(), a.description(),
                                a.competencies().stream()
                                        .map(c -> new RegisterFrameworkCommand.Competency(
                                                c.code(), c.name(), c.description(),
                                                c.prerequisites(),
                                                c.levelDescriptors().stream()
                                                        .map(d -> new RegisterFrameworkCommand.LevelDescriptor(
                                                                d.levelCode(), d.descriptor()))
                                                        .toList()))
                                        .toList()))
                        .toList());
    }

    FrameworkSummaryResponse toSummary(CompetencyFramework framework) {
        return new FrameworkSummaryResponse(
                framework.id().value(),
                framework.descriptor().name().value(),
                framework.descriptor().version().value(),
                framework.descriptor().source(),
                framework.status(),
                framework.registeredAt());
    }

    FrameworkSummaryResponse toSummary(FrameworkSummary summary) {
        return new FrameworkSummaryResponse(
                summary.id().value(),
                summary.name().value(),
                summary.version().value(),
                summary.source(),
                summary.status(),
                summary.registeredAt());
    }

    FrameworkDetailResponse toDetail(CompetencyFramework framework) {
        EObject root = framework.modelRoot();
        return new FrameworkDetailResponse(
                framework.id().value(),
                framework.descriptor().name().value(),
                framework.descriptor().version().value(),
                framework.descriptor().description(),
                framework.descriptor().source(),
                framework.status(),
                framework.registeredAt(),
                many(root, mm.frameworkLevels()).stream().map(this::toLevel).toList(),
                many(root, mm.frameworkAreas()).stream()
                        .map(area -> toArea(area, framework.id()))
                        .toList());
    }

    private FrameworkDetailResponse.LevelResponse toLevel(EObject level) {
        return new FrameworkDetailResponse.LevelResponse(
                str(level, mm.levelCode()),
                str(level, mm.levelName()),
                (Integer) level.eGet(mm.levelOrdinal()));
    }

    private FrameworkDetailResponse.AreaResponse toArea(EObject area, CompetencyFrameworkId frameworkId) {
        return new FrameworkDetailResponse.AreaResponse(
                str(area, mm.areaCode()),
                str(area, mm.areaName()),
                str(area, mm.areaDescription()),
                many(area, mm.areaCompetencies()).stream()
                        .map(competency -> toCompetency(competency, frameworkId))
                        .toList());
    }

    /** The framework id is threaded through solely to derive competency identity (ADR-019 A1). */
    private FrameworkDetailResponse.CompetencyResponse toCompetency(EObject competency,
                                                                    CompetencyFrameworkId frameworkId) {
        return new FrameworkDetailResponse.CompetencyResponse(
                CompetencyId.forCompetency(frameworkId, str(competency, mm.competencyCode())).value(),
                str(competency, mm.competencyCode()),
                str(competency, mm.competencyName()),
                str(competency, mm.competencyDescription()),
                many(competency, mm.competencyPrerequisites()).stream()
                        .map(p -> str(p, mm.competencyCode()))
                        .toList(),
                many(competency, mm.competencyLevelDescriptors()).stream()
                        .map(this::toLevelDescriptor)
                        .toList());
    }

    private FrameworkDetailResponse.LevelDescriptorResponse toLevelDescriptor(EObject descriptor) {
        EObject level = (EObject) descriptor.eGet(mm.levelDescriptorLevel());
        return new FrameworkDetailResponse.LevelDescriptorResponse(
                level == null ? null : str(level, mm.levelCode()),
                str(descriptor, mm.levelDescriptorText()));
    }

    private String str(EObject object, EAttribute attribute) {
        Object value = object.eGet(attribute);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<EObject> many(EObject owner, EReference reference) {
        return (List<EObject>) owner.eGet(reference);
    }
}
