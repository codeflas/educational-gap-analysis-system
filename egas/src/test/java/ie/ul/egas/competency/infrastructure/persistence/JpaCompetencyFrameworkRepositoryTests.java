package ie.ul.egas.competency.infrastructure.persistence;

import ie.ul.egas.TestcontainersConfiguration;
import ie.ul.egas.competency.FrameworkFixtures;
import ie.ul.egas.competency.application.FrameworkModelAssembler;
import ie.ul.egas.competency.domain.CompetencyFrameworkRepository;
import ie.ul.egas.competency.domain.model.CompetencyFramework;
import ie.ul.egas.competency.domain.model.DuplicateFrameworkException;
import ie.ul.egas.competency.domain.model.FrameworkDescriptor;
import ie.ul.egas.competency.domain.model.FrameworkName;
import ie.ul.egas.competency.domain.model.FrameworkSource;
import ie.ul.egas.competency.domain.model.FrameworkVersion;
import ie.ul.egas.competency.domain.validation.EmfConformanceValidator;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adapter contract against real PostgreSQL 16 (Testcontainers): Flyway schema, jsonb round-trip
 * with structural model equality, column-only summaries, and translation of the unique
 * constraint into the domain's DuplicateFrameworkException.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaCompetencyFrameworkRepository.class, EmfJsonModelSerializer.class})
class JpaCompetencyFrameworkRepositoryTests {

    @Autowired
    CompetencyFrameworkRepository repository;

    private final FrameworkModelAssembler assembler = new FrameworkModelAssembler();
    private final EmfConformanceValidator validator = new EmfConformanceValidator();

    @Test
    void savesAndRehydratesAFrameworkWithStructurallyEqualModelContent() {
        CompetencyFramework saved = repository.save(aggregate("Round Trip Framework", "1.0"));

        var reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(reloaded).isEqualTo(saved);
        assertThat(reloaded.descriptor()).isEqualTo(saved.descriptor());
        assertThat(reloaded.status()).isEqualTo(saved.status());
        assertThat(EcoreUtil.equals(reloaded.modelRoot(), saved.modelRoot()))
                .as("jsonb round-trip must preserve the M1 graph structurally")
                .isTrue();
    }

    @Test
    void listsSummariesWithoutHydratingModels() {
        repository.save(aggregate("Summary Framework A", "1.0"));
        repository.save(aggregate("Summary Framework B", "1.0"));

        var summaries = repository.findAllSummaries();

        assertThat(summaries).extracting(s -> s.name().value())
                .contains("Summary Framework A", "Summary Framework B");
    }

    @Test
    void reportsExistenceByNameAndVersion() {
        repository.save(aggregate("Existence Framework", "3.1"));

        assertThat(repository.existsByNameAndVersion(
                new FrameworkName("Existence Framework"), new FrameworkVersion("3.1"))).isTrue();
        assertThat(repository.existsByNameAndVersion(
                new FrameworkName("Existence Framework"), new FrameworkVersion("9.9"))).isFalse();
    }

    @Test
    void translatesTheUniqueConstraintIntoTheDomainException() {
        repository.save(aggregate("Constraint Framework", "1.0"));

        assertThatThrownBy(() -> repository.save(aggregate("Constraint Framework", "1.0")))
                .isInstanceOf(DuplicateFrameworkException.class);
    }

    private CompetencyFramework aggregate(String name, String version) {
        return CompetencyFramework.register(
                new FrameworkDescriptor(
                        new FrameworkName(name), new FrameworkVersion(version),
                        FrameworkSource.BESPOKE, null),
                assembler.assemble(FrameworkFixtures.validCommand(name, version)),
                validator,
                Clock.systemUTC());
    }
}
