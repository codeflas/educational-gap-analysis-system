package ie.ul.egas.gapanalysis.infrastructure.persistence;

import ie.ul.egas.TestcontainersConfiguration;
import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.gapanalysis.domain.CompetencyModelProjectionRepository;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetency;
import ie.ul.egas.gapanalysis.domain.model.ProjectedCompetencyModel;
import ie.ul.egas.gapanalysis.domain.model.ProjectedLevel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The projection adapter against real PostgreSQL: round-trip fidelity, replace-on-rewrite, and the
 * query cost of reading a projection back.
 *
 * <p>The assertion that matters most is idempotency. Modulith's registry resubmits incomplete
 * publications, so the listener will run more than once for the same event in production; a write
 * that appended rather than replaced would silently double a framework's competencies and every
 * gap computed from it. That property is proven here rather than assumed of the delivery mechanism.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaCompetencyModelProjectionRepository.class})
class JpaCompetencyModelProjectionRepositoryTests {

    private static final Instant REGISTERED = Instant.parse("2026-08-04T09:00:00Z");
    private static final Instant PROJECTED = Instant.parse("2026-08-04T09:00:05Z");

    @Autowired
    CompetencyModelProjectionRepository projection;

    @Autowired
    JdbcClient jdbc;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void projectsAndReadsBackAModelFieldForField() {
        CompetencyFrameworkId frameworkId = CompetencyFrameworkId.random();
        ProjectedCompetencyModel written = model(frameworkId);

        projection.project(written);
        entityManager.flush();
        entityManager.clear();

        ProjectedCompetencyModel read = projection.findByFrameworkId(frameworkId).orElseThrow();

        assertThat(read.frameworkName()).isEqualTo("Projection Framework");
        assertThat(read.frameworkVersion()).isEqualTo("1.0");
        assertThat(read.registeredAt()).isEqualTo(REGISTERED);
        assertThat(read.projectedAt()).isEqualTo(PROJECTED);
        assertThat(read.levels())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(written.levels());
        assertThat(read.competencies())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(written.competencies());
    }

    @Test
    void reProjectingTheSameFrameworkReplacesRatherThanAccumulates() {
        // Redelivery is expected: Modulith resubmits incomplete publications. An appending write
        // would double every competency and every gap computed from them.
        CompetencyFrameworkId frameworkId = CompetencyFrameworkId.random();
        projection.project(model(frameworkId));
        entityManager.flush();
        entityManager.clear();

        projection.project(model(frameworkId));
        entityManager.flush();
        entityManager.clear();

        ProjectedCompetencyModel read = projection.findByFrameworkId(frameworkId).orElseThrow();
        assertThat(read.competencies()).hasSize(2);
        assertThat(read.levels()).hasSize(3);
        assertThat(countRows("projected_competency")).isEqualTo(2);
        assertThat(countRows("projected_level")).isEqualTo(3);
        // One row, not two: SE-DSN defines L2 and SE-ARC defines no levels at all.
        assertThat(countRows("projected_competency_level")).isEqualTo(1);
    }

    @Test
    void reProjectingAdoptsTheNewShapeAndDropsWhatIsGone() {
        CompetencyFrameworkId frameworkId = CompetencyFrameworkId.random();
        projection.project(model(frameworkId));
        entityManager.flush();
        entityManager.clear();

        // The source model changed: one competency, one level, and a renamed framework.
        projection.project(new ProjectedCompetencyModel(
                frameworkId, "Renamed Framework", "2.0", REGISTERED, PROJECTED,
                List.of(new ProjectedLevel("L1", "Foundation", 1)),
                List.of(new ProjectedCompetency(CompetencyId.forCompetency(frameworkId, "SE-NEW"),
                        "SE-NEW", "New Competency", "QUA", List.of("L1")))));
        entityManager.flush();
        entityManager.clear();

        ProjectedCompetencyModel read = projection.findByFrameworkId(frameworkId).orElseThrow();
        assertThat(read.frameworkName()).isEqualTo("Renamed Framework");
        assertThat(read.competencies()).singleElement()
                .extracting(ProjectedCompetency::code).isEqualTo("SE-NEW");
        assertThat(read.levels()).singleElement()
                .extracting(ProjectedLevel::code).isEqualTo("L1");
        assertThat(countRows("projected_competency_level"))
                .as("the departed competency's level codes must cascade away")
                .isEqualTo(1);
    }

    @Test
    void reportsWhetherAFrameworkHasBeenProjected() {
        CompetencyFrameworkId projected = CompetencyFrameworkId.random();
        projection.project(model(projected));
        entityManager.flush();

        assertThat(projection.existsForFramework(projected)).isTrue();
        assertThat(projection.existsForFramework(CompetencyFrameworkId.random())).isFalse();
        assertThat(projection.findByFrameworkId(CompetencyFrameworkId.random())).isEmpty();
    }

    @Test
    void readingAProjectionCostsAFixedNumberOfQueries() {
        // Levels ride with the framework; competencies are a second query; their level codes are
        // batched into a third. The count must not grow with the number of competencies, which is
        // the N+1 this adapter's @BatchSize exists to prevent.
        CompetencyFrameworkId frameworkId = CompetencyFrameworkId.random();
        projection.project(model(frameworkId));
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        ProjectedCompetencyModel read = projection.findByFrameworkId(frameworkId).orElseThrow();
        read.competencies().forEach(competency -> competency.definedLevelCodes().size());

        assertThat(statistics.getPrepareStatementCount())
                .as("one query per collection, not one per competency")
                .isLessThanOrEqualTo(3);
    }

    private long countRows(String table) {
        return jdbc.sql("select count(*) from gap_analysis." + table).query(Long.class).single();
    }

    private ProjectedCompetencyModel model(CompetencyFrameworkId frameworkId) {
        return new ProjectedCompetencyModel(
                frameworkId, "Projection Framework", "1.0", REGISTERED, PROJECTED,
                List.of(new ProjectedLevel("L1", "Foundation", 1),
                        new ProjectedLevel("L2", "Intermediate", 2),
                        new ProjectedLevel("L3", "Advanced", 3)),
                List.of(
                        new ProjectedCompetency(CompetencyId.forCompetency(frameworkId, "SE-DSN"),
                                "SE-DSN", "Software Design", "DES", List.of("L2")),
                        new ProjectedCompetency(CompetencyId.forCompetency(frameworkId, "SE-ARC"),
                                "SE-ARC", "Software Architecture", "DES", List.of())));
    }
}
