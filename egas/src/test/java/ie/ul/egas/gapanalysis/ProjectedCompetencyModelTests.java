package ie.ul.egas.gapanalysis;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetency;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetencyModel;
import ie.ul.egas.gapanalysis.domain.model.ProjectedLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The projection read model, in isolation — no Spring, no database, no EMF.
 *
 * <p>These types exist to answer the two questions gap computation will ask of a framework: what
 * does it define, and what ordinal does a level code mean. Phase 2 stops short of computing
 * anything, but the lookups the computation depends on are the projection's contract and are
 * asserted here.
 */
class ProjectedCompetencyModelTests {

    private static final Instant REGISTERED = Instant.parse("2026-08-04T09:00:00Z");
    private static final Instant PROJECTED = Instant.parse("2026-08-04T09:00:05Z");

    @Test
    void resolvesALevelCodeToItsOrdinal() {
        // A target arrives from a request as a code; a gap is measured in ordinals. Holding both is
        // the projection's reason for carrying levels at all.
        ProjectedCompetencyModel model = model(
                List.of(new ProjectedLevel("L1", "Foundation", 1),
                        new ProjectedLevel("L3", "Advanced", 3)),
                List.of());

        assertThat(model.levelByCode("L3")).contains(new ProjectedLevel("L3", "Advanced", 3));
        assertThat(model.levelByCode("L2"))
                .as("a code the framework does not define resolves to nothing, not to zero")
                .isEmpty();
    }

    @Test
    void reportsTheHighestDefinedLevel() {
        // The default analysis target when a request omits one (ADR-021).
        ProjectedCompetencyModel model = model(
                List.of(new ProjectedLevel("L1", "Foundation", 1),
                        new ProjectedLevel("L3", "Advanced", 3),
                        new ProjectedLevel("L2", "Intermediate", 2)),
                List.of());

        assertThat(model.highestLevel()).map(ProjectedLevel::code).contains("L3");
    }

    @Test
    void aFrameworkDefiningNoLevelsHasNoHighest() {
        assertThat(model(List.of(), List.of()).highestLevel()).isEmpty();
    }

    @Test
    void levelOrderingIsTotalAndConsistentWithEquals() {
        ProjectedLevel l2 = new ProjectedLevel("L2", "Intermediate", 2);
        ProjectedLevel sfia2 = new ProjectedLevel("SFIA-2", "Assist", 2);

        // Ordering by ordinal alone would report these tied while equals reports them different —
        // the AttainedLevel defect of Step 4, avoided here by construction.
        assertThat(l2).isNotEqualTo(sfia2);
        assertThat(l2.compareTo(sfia2)).isNotZero();
        assertThat(new TreeSet<>(List.of(l2, sfia2))).hasSize(2);
        assertThat(new ProjectedLevel("L3", "Advanced", 3)).isGreaterThan(l2);
    }

    @Test
    void aCompetencyMayDefineNoLevels() {
        // Availability, not requirement: a competency with no descriptors is a meaningful state and
        // not a gap of zero (ADR-021).
        ProjectedCompetency competency = new ProjectedCompetency(
                CompetencyId.random(), "SE-ARC", "Software Architecture", "DES", null);

        assertThat(competency.definedLevelCodes()).isEmpty();
    }

    @Test
    void collectionsAreDefensivelyCopiedAndValuesValidated() {
        ProjectedCompetencyModel model = model(
                List.of(new ProjectedLevel("L1", "Foundation", 1)),
                List.of(new ProjectedCompetency(CompetencyId.random(), "SE-DSN", "Design", "DES",
                        List.of("L1"))));

        assertThat(model.levels()).isUnmodifiable();
        assertThat(model.competencies()).isUnmodifiable();
        assertThat(model.competencies().get(0).definedLevelCodes()).isUnmodifiable();

        assertThatThrownBy(() -> new ProjectedLevel(" ", "Blank", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectedLevel("L1", "Negative", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ProjectedCompetencyModel model(List<ProjectedLevel> levels,
                                           List<ProjectedCompetency> competencies) {
        return new ProjectedCompetencyModel(
                CompetencyFrameworkId.random(), "Projection Framework", "1.0",
                REGISTERED, PROJECTED, levels, competencies);
    }
}
