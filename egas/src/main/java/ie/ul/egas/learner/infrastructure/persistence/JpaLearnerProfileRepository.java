package ie.ul.egas.learner.infrastructure.persistence;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.learner.api.LearnerId;
import ie.ul.egas.learner.domain.LearnerProfileRepository;
import ie.ul.egas.learner.domain.model.AssertionId;
import ie.ul.egas.learner.domain.model.AttainedLevel;
import ie.ul.egas.learner.domain.model.AuthSubject;
import ie.ul.egas.learner.domain.model.Confidence;
import ie.ul.egas.learner.domain.model.DisplayName;
import ie.ul.egas.learner.domain.model.DuplicateLearnerProfileException;
import ie.ul.egas.learner.domain.model.EvidenceRecord;
import ie.ul.egas.learner.domain.model.LearnerProfile;
import ie.ul.egas.learner.domain.model.LearnerProfileSummary;
import ie.ul.egas.learner.domain.model.ProficiencyAssertion;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter implementing the domain port. Maps aggregate ↔ mapping entities, delegates
 * SQL to Spring Data, and translates infrastructure exceptions into domain exceptions at the
 * boundary: the unique-constraint violation on {@code auth_subject} becomes
 * {@link DuplicateLearnerProfileException}, making the database the authoritative, race-free guard
 * behind the application service's fast-path check. {@code saveAndFlush} surfaces the violation
 * inside this method rather than at an arbitrary later flush point.
 *
 * <p><b>Rehydration goes exclusively through {@code reconstitute}</b>, never through
 * {@code create} or {@code recordEvidence}. Rebuilding an aggregate through its write path would
 * re-run {@code LevelResolutionPolicy} on every load, silently rewriting stored history whenever
 * the configured policy changed — which ADR-018 names as a deliberate non-behaviour.
 *
 * <p><b>This adapter is the trust boundary</b> that {@code LearnerProfile.reconstitute} documents.
 * Mapping is therefore total and mechanical: no filtering, no deduplication, no defaulting. Any
 * cleverness here is a place where what was written and what comes back could quietly diverge, and
 * the round-trip tests exist to prove they do not.
 */
@Repository
class JpaLearnerProfileRepository implements LearnerProfileRepository {

    /** Matches numeric(4,3); Confidence is bounded to [0,1] so only the scale needs fixing. */
    private static final int CONFIDENCE_SCALE = 3;

    /** The one violation this adapter claims to understand (V200). */
    private static final String AUTH_SUBJECT_CONSTRAINT = "uq_learner_auth_subject";

    private final LearnerProfileSpringDataRepository springData;

    JpaLearnerProfileRepository(LearnerProfileSpringDataRepository springData) {
        this.springData = springData;
    }

    @Override
    public LearnerProfile save(LearnerProfile profile) {
        try {
            springData.saveAndFlush(toEntity(profile));
            return profile;
        } catch (DataIntegrityViolationException e) {
            if (violates(e, AUTH_SUBJECT_CONSTRAINT)) {
                throw new DuplicateLearnerProfileException();
            }
            // Anything else is not a duplicate profile and must not be dressed up as one: a
            // mislabelled diagnostic sends the next reader to the wrong constraint entirely.
            throw e;
        }
    }

    /** True when this violation names the given constraint anywhere in its cause chain. */
    private static boolean violates(Throwable failure, String constraintName) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(constraintName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<LearnerProfile> findById(LearnerId id) {
        return springData.findWithAssertionsById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<LearnerProfile> findByAuthSubject(AuthSubject subject) {
        return springData.findWithAssertionsByAuthSubject(subject.value()).map(this::toDomain);
    }

    @Override
    public boolean existsByAuthSubject(AuthSubject subject) {
        return springData.existsByAuthSubject(subject.value());
    }

    @Override
    public List<LearnerProfileSummary> findAllSummaries() {
        return springData.findAllSummaries().stream()
                .map(view -> new LearnerProfileSummary(
                        new LearnerId(view.getId()),
                        new DisplayName(view.getDisplayName()),
                        view.getAssertionCount(),
                        view.getCreatedAt()))
                .toList();
    }

    private LearnerProfileJpaEntity toEntity(LearnerProfile profile) {
        LearnerProfileJpaEntity entity = new LearnerProfileJpaEntity(
                profile.id().value(),
                profile.authSubject().value(),
                profile.displayName().value(),
                profile.createdAt());

        for (ProficiencyAssertion assertion : profile.assertions()) {
            entity.addAssertion(toEntity(assertion));
        }
        return entity;
    }

    private ProficiencyAssertionJpaEntity toEntity(ProficiencyAssertion assertion) {
        AttainedLevel level = assertion.attainedLevel();
        ProficiencyAssertionJpaEntity entity = new ProficiencyAssertionJpaEntity(
                assertion.id().value(),
                assertion.competencyId().value(),
                assertion.frameworkId().value(),
                level.ordinal(),
                level.code(),
                assertion.resolvedAt());

        int sequence = 0;
        for (EvidenceRecord record : assertion.evidence()) {
            entity.addEvidence(toEntity(record, assertion.id(), sequence++));
        }
        return entity;
    }

    /**
     * Evidence rows have no domain identity — {@link EvidenceRecord} is a value object — so a row
     * id must be manufactured. It is <em>derived</em> from the owning assertion and the record's
     * position rather than generated randomly, because a random id would differ on every save: the
     * merge would insert a full replacement set beside the rows it was about to orphan, and the
     * new rows would collide with the old on {@code uq_evidence_assertion_seq}. Deriving the id
     * makes re-saving an unchanged record a no-op update in place, which is what allows an
     * existing profile to be saved at all.
     *
     * <p>Position is a safe basis precisely because ADR-018 makes evidence append-only: a record
     * never moves once written, so its derived id is stable for the lifetime of the row.
     */
    private EvidenceRecordJpaEntity toEntity(EvidenceRecord record, AssertionId owner, int sequence) {
        AttainedLevel claimed = record.claimedLevel();
        return new EvidenceRecordJpaEntity(
                evidenceRowId(owner, sequence),
                record.type(),
                claimed.ordinal(),
                claimed.code(),
                BigDecimal.valueOf(record.confidence().value())
                        .setScale(CONFIDENCE_SCALE, RoundingMode.HALF_UP),
                record.source(),
                record.recordedAt());
    }

    private static UUID evidenceRowId(AssertionId owner, int sequence) {
        return UUID.nameUUIDFromBytes(
                (owner.value() + ":" + sequence).getBytes(StandardCharsets.UTF_8));
    }

    private LearnerProfile toDomain(LearnerProfileJpaEntity entity) {
        List<ProficiencyAssertion> assertions = entity.getAssertions().stream()
                .map(this::toDomain)
                .toList();

        return LearnerProfile.reconstitute(
                new LearnerId(entity.getId()),
                new AuthSubject(entity.getAuthSubject()),
                new DisplayName(entity.getDisplayName()),
                entity.getCreatedAt(),
                assertions);
    }

    private ProficiencyAssertion toDomain(ProficiencyAssertionJpaEntity entity) {
        List<EvidenceRecord> evidence = entity.getEvidence().stream()
                .map(this::toDomain)
                .toList();

        return ProficiencyAssertion.reconstitute(
                new AssertionId(entity.getId()),
                new CompetencyId(entity.getCompetencyId()),
                new CompetencyFrameworkId(entity.getFrameworkId()),
                new AttainedLevel(entity.getLevelOrdinal(), entity.getLevelCode()),
                evidence,
                entity.getResolvedAt());
    }

    private EvidenceRecord toDomain(EvidenceRecordJpaEntity entity) {
        return new EvidenceRecord(
                entity.getType(),
                new AttainedLevel(entity.getLevelOrdinal(), entity.getLevelCode()),
                new Confidence(entity.getConfidence().doubleValue()),
                entity.getSource(),
                entity.getRecordedAt());
    }
}
