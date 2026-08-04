package ie.ul.egas.gapanalysis;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.gapanalysis.domain.model.AnalysisTarget;
import ie.ul.egas.gapanalysis.domain.model.AttainmentSnapshot;
import ie.ul.egas.gapanalysis.domain.model.EvidenceSnapshot;
import ie.ul.egas.learner.api.LearnerId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Shared builders for the Gap Analysis domain suites, mirroring {@code LearnerFixtures}: one
 * canonical shape plus the targeted variants the rules need.
 *
 * <p>Identifiers are derived rather than random so a target and an attainment for the same
 * competency actually match — the join ADR-019 Amendment 1 exists to make possible — and the clock
 * is fixed so timestamps are assertable rather than merely non-null.
 */
public final class GapFixtures {

    public static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");
    public static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    public static final CompetencyFrameworkId FRAMEWORK =
            CompetencyFrameworkId.of("11111111-1111-4111-8111-111111111111");

    public static final LearnerId LEARNER =
            LearnerId.of("22222222-2222-4222-8222-222222222222");

    public static final CompetencyId SOFTWARE_DESIGN =
            CompetencyId.forCompetency(FRAMEWORK, "SE-DSN");
    public static final CompetencyId SOFTWARE_TESTING =
            CompetencyId.forCompetency(FRAMEWORK, "SE-TST");

    private GapFixtures() {
    }

    /** A target at the given ordinal, level code derived as {@code L<ordinal>}. */
    public static AnalysisTarget target(int targetOrdinal) {
        return target(SOFTWARE_DESIGN, "SE-DSN", targetOrdinal);
    }

    public static AnalysisTarget target(CompetencyId competencyId, String code, int targetOrdinal) {
        return new AnalysisTarget(competencyId, code, "Software Design",
                "L" + targetOrdinal, targetOrdinal);
    }

    /** An attainment at the given ordinal, supported by one self-declared observation. */
    public static AttainmentSnapshot attainment(int attainedOrdinal) {
        return new AttainmentSnapshot(attainedOrdinal, "L" + attainedOrdinal, NOW,
                List.of(evidence(attainedOrdinal, 0.8)));
    }

    public static AttainmentSnapshot attainment(int attainedOrdinal, List<EvidenceSnapshot> evidence) {
        return new AttainmentSnapshot(attainedOrdinal, "L" + attainedOrdinal, NOW, evidence);
    }

    public static EvidenceSnapshot evidence(int claimedOrdinal, double confidence) {
        return new EvidenceSnapshot("SELF_DECLARED", claimedOrdinal, "L" + claimedOrdinal,
                confidence, "fixture evidence", NOW);
    }
}
