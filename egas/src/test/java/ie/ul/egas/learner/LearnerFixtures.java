package ie.ul.egas.learner;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.learner.domain.model.AttainedLevel;
import ie.ul.egas.learner.domain.model.AuthSubject;
import ie.ul.egas.learner.domain.model.Confidence;
import ie.ul.egas.learner.domain.model.DisplayName;
import ie.ul.egas.learner.domain.model.EvidenceRecord;
import ie.ul.egas.learner.domain.model.EvidenceType;
import ie.ul.egas.learner.domain.model.LearnerProfile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Shared builders for the Learner Profiling suites, mirroring {@code FrameworkFixtures} from
 * Step 2: one canonical valid shape plus the targeted variants the invariants need.
 *
 * <p>Identifiers are fixed rather than random so a failing assertion names a recognisable
 * competency, and the clock is fixed so timestamps are assertable rather than merely non-null.
 */
public final class LearnerFixtures {

    public static final Instant NOW = Instant.parse("2026-08-03T09:00:00Z");
    public static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    public static final CompetencyFrameworkId FRAMEWORK =
            CompetencyFrameworkId.of("11111111-1111-4111-8111-111111111111");
    public static final CompetencyFrameworkId OTHER_FRAMEWORK =
            CompetencyFrameworkId.of("22222222-2222-4222-8222-222222222222");
    public static final CompetencyId SOFTWARE_DESIGN =
            CompetencyId.of("33333333-3333-4333-8333-333333333333");
    public static final CompetencyId SOFTWARE_TESTING =
            CompetencyId.of("44444444-4444-4444-8444-444444444444");

    public static final AttainedLevel FOUNDATION = new AttainedLevel(1, "L1");
    public static final AttainedLevel INTERMEDIATE = new AttainedLevel(2, "L2");
    public static final AttainedLevel ADVANCED = new AttainedLevel(3, "L3");

    private LearnerFixtures() {
    }

    /**
     * Deliberately <em>not</em> {@code test-learner}: that name belongs to the security test
     * principal roster in {@code src/test/resources/application.properties}. These suites have no
     * relationship to it — the aggregate never sees a token — and an identical string would invite
     * a later reader to treat the two as connected and change one to match the other.
     */
    public static AuthSubject subject() {
        return new AuthSubject("fixture-learner");
    }

    public static AuthSubject otherSubject() {
        return new AuthSubject("fixture-other-learner");
    }

    public static LearnerProfile profile() {
        return LearnerProfile.create(subject(), new DisplayName("Test Learner"), FIXED_CLOCK);
    }

    public static LearnerProfile profileFor(String rawSubject) {
        return LearnerProfile.create(
                new AuthSubject(rawSubject), new DisplayName("Test Learner"), FIXED_CLOCK);
    }

    /** Evidence at the given level and confidence, recorded at the fixed clock instant. */
    public static EvidenceRecord evidence(AttainedLevel level, double confidence) {
        return evidence(EvidenceType.SELF_DECLARED, level, confidence, NOW);
    }

    public static EvidenceRecord evidence(EvidenceType type, AttainedLevel level,
                                          double confidence, Instant recordedAt) {
        return new EvidenceRecord(
                type, level, new Confidence(confidence), "fixture evidence", recordedAt);
    }

    public static CompetencyId randomCompetency() {
        return new CompetencyId(UUID.randomUUID());
    }
}
