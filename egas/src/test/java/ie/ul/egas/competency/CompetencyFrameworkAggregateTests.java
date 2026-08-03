package ie.ul.egas.competency;

import ie.ul.egas.competency.application.FrameworkModelAssembler;
import ie.ul.egas.competency.domain.model.CompetencyFramework;
import ie.ul.egas.competency.domain.model.FrameworkDescriptor;
import ie.ul.egas.competency.domain.model.FrameworkName;
import ie.ul.egas.competency.domain.model.FrameworkSource;
import ie.ul.egas.competency.domain.model.FrameworkVersion;
import ie.ul.egas.competency.domain.model.ModelStatus;
import ie.ul.egas.competency.domain.validation.ConformanceReport;
import ie.ul.egas.competency.domain.validation.ConformanceValidator;
import ie.ul.egas.competency.domain.validation.ConformanceViolation;
import ie.ul.egas.competency.domain.validation.ModelConformanceException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The aggregate invariant, in isolation: registration is refused for non-conforming models.
 * The validator seam is stubbed with lambdas — no EMF fixtures needed to test the rule itself.
 */
class CompetencyFrameworkAggregateTests {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-02T12:00:00Z"), ZoneOffset.UTC);
    private static final ConformanceValidator PASSING = root -> new ConformanceReport(List.of());
    private static final ConformanceValidator FAILING = root -> new ConformanceReport(List.of(
            ConformanceViolation.error("TEST_RULE", "does not conform", "somewhere")));

    private final FrameworkModelAssembler assembler = new FrameworkModelAssembler();

    @Test
    void registrationOfAConformingModelYieldsADraftWithIdentityAndTimestamp() {
        var framework = CompetencyFramework.register(
                descriptor(), assembler.assemble(FrameworkFixtures.validCommand()), PASSING, FIXED_CLOCK);

        assertThat(framework.id()).isNotNull();
        assertThat(framework.status()).isEqualTo(ModelStatus.DRAFT);
        assertThat(framework.registeredAt()).isEqualTo(Instant.parse("2026-08-02T12:00:00Z"));
    }

    @Test
    void registrationOfANonConformingModelIsRefusedWithTheFullReport() {
        var modelRoot = assembler.assemble(FrameworkFixtures.validCommand());

        assertThatThrownBy(() -> CompetencyFramework.register(descriptor(), modelRoot, FAILING, FIXED_CLOCK))
                .isInstanceOfSatisfying(ModelConformanceException.class, e ->
                        assertThat(e.report().violations())
                                .extracting(ConformanceViolation::code)
                                .containsExactly("TEST_RULE"));
    }

    @Test
    void aggregateEqualityIsByIdentity() {
        var modelRoot = assembler.assemble(FrameworkFixtures.validCommand());
        var first = CompetencyFramework.register(descriptor(), modelRoot, PASSING, FIXED_CLOCK);
        var second = CompetencyFramework.register(descriptor(), modelRoot, PASSING, FIXED_CLOCK);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo(CompetencyFramework.reconstitute(
                first.id(), first.descriptor(), first.status(), modelRoot, first.registeredAt()));
    }

    private FrameworkDescriptor descriptor() {
        return new FrameworkDescriptor(
                new FrameworkName("Software Engineering Core"),
                new FrameworkVersion("1.0"),
                FrameworkSource.BESPOKE,
                null);
    }
}
