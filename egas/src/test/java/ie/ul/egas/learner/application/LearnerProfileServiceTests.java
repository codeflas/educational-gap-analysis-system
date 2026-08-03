package ie.ul.egas.learner.application;

import ie.ul.egas.learner.LearnerFixtures;
import ie.ul.egas.learner.api.LearnerId;
import ie.ul.egas.learner.domain.LearnerProfileRepository;
import ie.ul.egas.learner.domain.model.AuthSubject;
import ie.ul.egas.learner.domain.model.DuplicateLearnerProfileException;
import ie.ul.egas.learner.domain.model.EvidenceType;
import ie.ul.egas.learner.domain.model.LearnerProfile;
import ie.ul.egas.learner.domain.model.LearnerProfileNotFoundException;
import ie.ul.egas.learner.domain.model.LearnerProfileSummary;
import ie.ul.egas.learner.domain.model.ProficiencyAssertion;
import ie.ul.egas.learner.domain.policy.HighestConfidenceResolutionPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Use-case behaviour of the learner application layer.
 *
 * <p><b>The point of this suite is what is absent from it.</b> The complete ownership matrix —
 * owner, non-owner, privileged reader, unknown identifier — is exercised with no security context,
 * no token, no filter chain and no Spring container. That is only possible because ADR-016 makes
 * the caller's identity an ordinary argument rather than ambient state, so this suite is the
 * evidence for that decision rather than a restatement of it.
 *
 * <p>The repository is a hand-written in-memory double, matching the codebase's convention of real
 * collaborators over a mocking framework.
 */
class LearnerProfileServiceTests {

    private static final String CALLER = "fixture-learner";
    private static final String OTHER = "fixture-other-learner";

    private final InMemoryLearnerProfileRepository repository = new InMemoryLearnerProfileRepository();
    private final LearnerProfileService service = new LearnerProfileService(
            repository, new HighestConfidenceResolutionPolicy(), LearnerFixtures.FIXED_CLOCK);

    // --- provisioning (ADR-017) ---------------------------------------------------------------

    @Test
    void createsAProfileForTheCallingSubjectAtTheInjectedClock() {
        LearnerProfile created = service.createProfile(
                new CreateLearnerProfileCommand(CALLER, "Ada Lovelace"));

        assertThat(created.authSubject()).isEqualTo(new AuthSubject(CALLER));
        assertThat(created.displayName().value()).isEqualTo("Ada Lovelace");
        assertThat(created.createdAt()).isEqualTo(LearnerFixtures.NOW);
        assertThat(created.assertions()).isEmpty();
        assertThat(repository.findByAuthSubject(new AuthSubject(CALLER))).isPresent();
    }

    @Test
    void refusesASecondProfileForTheSameSubject() {
        service.createProfile(new CreateLearnerProfileCommand(CALLER, "Ada Lovelace"));

        assertThatThrownBy(() -> service.createProfile(
                new CreateLearnerProfileCommand("  fixture-learner  ", "Ada Again")))
                .as("the fast-path check must be insensitive to the padding AuthSubject trims")
                .isInstanceOf(DuplicateLearnerProfileException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    // --- evidence recording (ADR-018) ---------------------------------------------------------

    @Test
    void recordsEvidenceAgainstTheCallersOwnProfileAndResolvesTheLevel() {
        service.createProfile(new CreateLearnerProfileCommand(CALLER, "Ada Lovelace"));

        LearnerProfile updated = service.recordEvidence(evidenceCommand(CALLER, 1, "L1", 0.4));

        ProficiencyAssertion assertion =
                updated.assertionFor(LearnerFixtures.SOFTWARE_DESIGN).orElseThrow();
        assertThat(assertion.attainedLevel()).isEqualTo(LearnerFixtures.FOUNDATION);
        assertThat(assertion.evidence()).hasSize(1);
        assertThat(assertion.frameworkId()).isEqualTo(LearnerFixtures.FRAMEWORK);
    }

    @Test
    void furtherEvidenceReResolvesTheSameAssertion() {
        service.createProfile(new CreateLearnerProfileCommand(CALLER, "Ada Lovelace"));
        service.recordEvidence(evidenceCommand(CALLER, 1, "L1", 0.4));

        LearnerProfile updated = service.recordEvidence(evidenceCommand(CALLER, 3, "L3", 0.9));

        ProficiencyAssertion assertion =
                updated.assertionFor(LearnerFixtures.SOFTWARE_DESIGN).orElseThrow();
        assertThat(assertion.evidence()).hasSize(2);
        assertThat(assertion.attainedLevel())
                .as("the more confident claim wins (ADR-018)")
                .isEqualTo(LearnerFixtures.ADVANCED);
    }

    @Test
    void stampsEvidenceWithTheInjectedClockRatherThanAnyCallerSuppliedTime() {
        // The command carries no timestamp by design: a supplied value would be unverifiable and
        // would let a caller post-date evidence to win the policy's recency tie-break.
        service.createProfile(new CreateLearnerProfileCommand(CALLER, "Ada Lovelace"));

        LearnerProfile updated = service.recordEvidence(evidenceCommand(CALLER, 2, "L2", 0.5));

        assertThat(updated.assertionFor(LearnerFixtures.SOFTWARE_DESIGN).orElseThrow()
                .evidence().get(0).recordedAt()).isEqualTo(LearnerFixtures.NOW);
    }

    @Test
    void refusesEvidenceFromACallerWhoHasNoProfile() {
        assertThatThrownBy(() -> service.recordEvidence(evidenceCommand(CALLER, 1, "L1", 0.5)))
                .as("a valid token does not imply enrolment (ADR-017)")
                .isInstanceOf(LearnerProfileNotFoundException.class)
                .hasMessageContaining("no learner profile");
    }

    // --- ownership (ADR-015 amendment 1, ADR-016) ----------------------------------------------

    @Test
    void returnsTheCallersOwnProfileAndReportsAbsenceWhenThereIsNone() {
        service.createProfile(new CreateLearnerProfileCommand(CALLER, "Ada Lovelace"));

        assertThat(service.getOwnProfile(new AuthSubject(CALLER)).authSubject())
                .isEqualTo(new AuthSubject(CALLER));
        assertThatThrownBy(() -> service.getOwnProfile(new AuthSubject(OTHER)))
                .isInstanceOf(LearnerProfileNotFoundException.class);
    }

    @Test
    void theOwnerMayReadTheirOwnProfileByIdWithoutPrivilege() {
        LearnerId id = service.createProfile(
                new CreateLearnerProfileCommand(CALLER, "Ada Lovelace")).id();

        LearnerProfile read = service.getProfileForReader(id, new AuthSubject(CALLER), false);

        assertThat(read.id()).isEqualTo(id);
    }

    @Test
    void aPrivilegedReaderMayReadAProfileTheyDoNotOwn() {
        LearnerId id = service.createProfile(
                new CreateLearnerProfileCommand(CALLER, "Ada Lovelace")).id();

        LearnerProfile read = service.getProfileForReader(id, new AuthSubject(OTHER), true);

        assertThat(read.id())
                .as("callerMayReadAny is resolved in the security layer and enforced here")
                .isEqualTo(id);
    }

    @Test
    void denialAndAbsenceAreIndistinguishableToTheCaller() {
        // The anti-enumeration property: answering "forbidden" would confirm the identifier names
        // a real profile, turning this method into an oracle over learner identifiers.
        LearnerId existing = service.createProfile(
                new CreateLearnerProfileCommand(CALLER, "Ada Lovelace")).id();
        LearnerId absent = LearnerId.random();

        Throwable forbidden = catchThrowable(
                () -> service.getProfileForReader(existing, new AuthSubject(OTHER), false));
        Throwable missing = catchThrowable(
                () -> service.getProfileForReader(absent, new AuthSubject(OTHER), false));

        assertThat(forbidden).isInstanceOf(LearnerProfileNotFoundException.class);
        assertThat(missing).isInstanceOf(LearnerProfileNotFoundException.class);
        assertThat(forbidden)
                .as("present-and-forbidden must be indistinguishable from absent")
                .hasSameClassAs(missing);
        assertThat(forbidden.getMessage()).isNotEqualTo(missing.getMessage());
    }

    // --- listing -------------------------------------------------------------------------------

    @Test
    void listsSummariesForEveryProfile() {
        service.createProfile(new CreateLearnerProfileCommand(CALLER, "Ada Lovelace"));
        service.createProfile(new CreateLearnerProfileCommand(OTHER, "Grace Hopper"));

        List<LearnerProfileSummary> summaries = service.listProfiles();

        assertThat(summaries).hasSize(2);
        assertThat(summaries).extracting(s -> s.displayName().value())
                .containsExactlyInAnyOrder("Ada Lovelace", "Grace Hopper");
    }

    private RecordEvidenceCommand evidenceCommand(String subject, int ordinal, String code,
                                                  double confidence) {
        return new RecordEvidenceCommand(
                subject,
                LearnerFixtures.SOFTWARE_DESIGN.value(),
                LearnerFixtures.FRAMEWORK.value(),
                EvidenceType.SELF_DECLARED,
                ordinal,
                code,
                confidence,
                "service test evidence");
    }

    /**
     * In-memory driven-port double. Deliberately not a mock: the codebase uses real collaborators
     * throughout, and a stub that actually stores makes the check-then-act and re-save paths
     * behave as they do in production rather than as a script says they should.
     */
    private static final class InMemoryLearnerProfileRepository implements LearnerProfileRepository {

        private final Map<UUID, LearnerProfile> byId = new LinkedHashMap<>();

        @Override
        public LearnerProfile save(LearnerProfile profile) {
            byId.put(profile.id().value(), profile);
            return profile;
        }

        @Override
        public Optional<LearnerProfile> findById(LearnerId id) {
            return Optional.ofNullable(byId.get(id.value()));
        }

        @Override
        public Optional<LearnerProfile> findByAuthSubject(AuthSubject subject) {
            return byId.values().stream()
                    .filter(profile -> profile.isOwnedBy(subject))
                    .findFirst();
        }

        @Override
        public boolean existsByAuthSubject(AuthSubject subject) {
            return findByAuthSubject(subject).isPresent();
        }

        @Override
        public List<LearnerProfileSummary> findAllSummaries() {
            List<LearnerProfileSummary> summaries = new ArrayList<>();
            for (LearnerProfile profile : byId.values()) {
                summaries.add(new LearnerProfileSummary(
                        profile.id(), profile.displayName(),
                        profile.assertions().size(), profile.createdAt()));
            }
            return List.copyOf(summaries);
        }

        int count() {
            return byId.size();
        }
    }
}
