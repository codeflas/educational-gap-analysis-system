package ie.ul.egas.competency;

import ie.ul.egas.competency.application.RegisterFrameworkCommand;
import ie.ul.egas.competency.domain.model.FrameworkSource;
import ie.ul.egas.competency.infrastructure.web.dto.RegisterFrameworkRequest;

import java.util.List;

/**
 * Canonical test payloads: a small but structurally complete bespoke framework (levels, two
 * areas, prerequisites, level descriptors) plus targeted invalid variants. Names/versions are
 * parameterised so integration tests can guarantee uniqueness per test method.
 */
public final class FrameworkFixtures {

    private FrameworkFixtures() {
    }

    // ------------------------------------------------------------------ commands (unit tests)

    public static RegisterFrameworkCommand validCommand() {
        return validCommand("Software Engineering Core", "1.0");
    }

    public static RegisterFrameworkCommand validCommand(String name, String version) {
        return new RegisterFrameworkCommand(
                name, version,
                "Bespoke curriculum competency framework for software engineering.",
                FrameworkSource.BESPOKE,
                List.of(
                        new RegisterFrameworkCommand.Level("L1", "Foundation", 1),
                        new RegisterFrameworkCommand.Level("L2", "Intermediate", 2),
                        new RegisterFrameworkCommand.Level("L3", "Advanced", 3)),
                List.of(
                        new RegisterFrameworkCommand.Area("DES", "Design", "Design and architecture",
                                List.of(
                                        new RegisterFrameworkCommand.Competency("SE-DSN", "Software Design",
                                                "Designs maintainable software structures.",
                                                List.of(),
                                                List.of(new RegisterFrameworkCommand.LevelDescriptor(
                                                        "L2", "Applies established design patterns."))),
                                        new RegisterFrameworkCommand.Competency("SE-ARC", "Software Architecture",
                                                "Shapes system-level structure and trade-offs.",
                                                List.of("SE-DSN"),
                                                List.of()))),
                        new RegisterFrameworkCommand.Area("QUA", "Quality", null,
                                List.of(
                                        new RegisterFrameworkCommand.Competency("SE-TST", "Software Testing",
                                                null,
                                                List.of("SE-DSN"),
                                                List.of(new RegisterFrameworkCommand.LevelDescriptor(
                                                        "L1", "Writes unit tests for own code.")))))));
    }

    public static RegisterFrameworkCommand commandWithPrerequisiteCycle() {
        return new RegisterFrameworkCommand(
                "Cyclic Framework", "1.0", null, FrameworkSource.BESPOKE,
                List.of(),
                List.of(new RegisterFrameworkCommand.Area("A", "Area", null,
                        List.of(
                                new RegisterFrameworkCommand.Competency("C-A", "Alpha", null, List.of("C-B"), List.of()),
                                new RegisterFrameworkCommand.Competency("C-B", "Beta", null, List.of("C-A"), List.of())))));
    }

    public static RegisterFrameworkCommand commandWithUnknownPrerequisite() {
        return new RegisterFrameworkCommand(
                "Dangling Framework", "1.0", null, FrameworkSource.BESPOKE,
                List.of(),
                List.of(new RegisterFrameworkCommand.Area("A", "Area", null,
                        List.of(new RegisterFrameworkCommand.Competency(
                                "C-A", "Alpha", null, List.of("NO-SUCH"), List.of())))));
    }

    public static RegisterFrameworkCommand commandWithUnknownLevel() {
        return new RegisterFrameworkCommand(
                "Unknown Level Framework", "1.0", null, FrameworkSource.BESPOKE,
                List.of(new RegisterFrameworkCommand.Level("L1", null, 1)),
                List.of(new RegisterFrameworkCommand.Area("A", "Area", null,
                        List.of(new RegisterFrameworkCommand.Competency(
                                "C-A", "Alpha", null, List.of(),
                                List.of(new RegisterFrameworkCommand.LevelDescriptor("L9", "text")))))));
    }

    public static RegisterFrameworkCommand commandWithDuplicateCompetencyCodes() {
        return new RegisterFrameworkCommand(
                "Duplicate Codes Framework", "1.0", null, FrameworkSource.BESPOKE,
                List.of(),
                List.of(new RegisterFrameworkCommand.Area("A", "Area", null,
                        List.of(
                                new RegisterFrameworkCommand.Competency("C-A", "Alpha", null, List.of(), List.of()),
                                new RegisterFrameworkCommand.Competency("C-A", "Alpha Again", null, List.of(), List.of())))));
    }

    // ------------------------------------------------------------------ requests (API tests)

    public static RegisterFrameworkRequest validRequest(String name, String version) {
        return new RegisterFrameworkRequest(
                name, version,
                "Bespoke curriculum competency framework for software engineering.",
                FrameworkSource.BESPOKE,
                List.of(
                        new RegisterFrameworkRequest.LevelRequest("L1", "Foundation", 1),
                        new RegisterFrameworkRequest.LevelRequest("L2", "Intermediate", 2)),
                List.of(new RegisterFrameworkRequest.AreaRequest("DES", "Design", null,
                        List.of(
                                new RegisterFrameworkRequest.CompetencyRequest("SE-DSN", "Software Design",
                                        "Designs maintainable software structures.",
                                        List.of(),
                                        List.of(new RegisterFrameworkRequest.LevelDescriptorRequest(
                                                "L2", "Applies established design patterns."))),
                                new RegisterFrameworkRequest.CompetencyRequest("SE-ARC", "Software Architecture",
                                        null,
                                        List.of("SE-DSN"),
                                        List.of())))));
    }

    public static RegisterFrameworkRequest requestWithPrerequisiteCycle(String name) {
        return new RegisterFrameworkRequest(
                name, "1.0", null, FrameworkSource.BESPOKE,
                List.of(),
                List.of(new RegisterFrameworkRequest.AreaRequest("A", "Area", null,
                        List.of(
                                new RegisterFrameworkRequest.CompetencyRequest("C-A", "Alpha", null,
                                        List.of("C-B"), List.of()),
                                new RegisterFrameworkRequest.CompetencyRequest("C-B", "Beta", null,
                                        List.of("C-A"), List.of())))));
    }
}
