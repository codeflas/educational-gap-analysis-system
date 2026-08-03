package ie.ul.egas.learner.application;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.learner.api.LearnerId;
import ie.ul.egas.learner.domain.LearnerProfileRepository;
import ie.ul.egas.learner.domain.model.AttainedLevel;
import ie.ul.egas.learner.domain.model.AuthSubject;
import ie.ul.egas.learner.domain.model.Confidence;
import ie.ul.egas.learner.domain.model.DisplayName;
import ie.ul.egas.learner.domain.model.DuplicateLearnerProfileException;
import ie.ul.egas.learner.domain.model.EvidenceRecord;
import ie.ul.egas.learner.domain.model.LearnerProfile;
import ie.ul.egas.learner.domain.model.LearnerProfileNotFoundException;
import ie.ul.egas.learner.domain.model.LearnerProfileSummary;
import ie.ul.egas.learner.domain.policy.LevelResolutionPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Application service for the learner-profile lifecycle: the transaction boundary and the
 * orchestration of provisioning, evidence recording and ownership-aware reads. It holds no
 * business rules of its own — invariants live in the aggregate, and the rule that collapses
 * evidence into a level lives behind {@link LevelResolutionPolicy}.
 *
 * <p><b>Identity arrives as data, never from ambient state.</b> Every ownership-sensitive method
 * takes the caller's subject as an explicit argument (ADR-016). Nothing here reads
 * {@code SecurityContextHolder} — a rule the {@code applicationStaysOutOfAdapters} fitness
 * function now enforces rather than merely documents. The payoff is that the whole ownership
 * matrix is exercisable with no security context, no token, and no Spring container at all, which
 * {@code LearnerProfileServiceTests} demonstrates rather than asserts.
 *
 * <p><b>{@code callerMayReadAny} is a decision, not a role.</b> The {@code learner} module declares
 * {@code allowedDependencies = {"competency :: api"}} and so cannot reference {@code Role}, which
 * lives in {@code platform}. Passing role names inward would carry the security vocabulary across
 * the boundary in all but name; passing a resolved boolean keeps the <em>policy</em> ("do educators
 * see every profile?") in the security layer and the <em>enforcement</em> ("may this caller see
 * this resource?") here, where the resource is in hand. This is the concrete form of the ADR-015
 * amendment.
 */
@Service
public class LearnerProfileService {

    private final LearnerProfileRepository repository;
    private final LevelResolutionPolicy resolutionPolicy;
    private final Clock clock;

    public LearnerProfileService(LearnerProfileRepository repository,
                                 LevelResolutionPolicy resolutionPolicy,
                                 Clock clock) {
        this.repository = repository;
        this.resolutionPolicy = resolutionPolicy;
        this.clock = clock;
    }

    /**
     * Provisions the caller's profile. Provisioning is an explicit act rather than a side effect of
     * first access (ADR-017), so the moment a person entered the system is a moment the system can
     * point at.
     *
     * <p>The existence check is a fast-path courtesy; the authoritative guard is
     * {@code uq_learner_auth_subject}, whose violation the persistence adapter translates into the
     * same exception. That is what closes the check-then-act race, exactly as framework
     * registration does in Step 2.
     *
     * @throws DuplicateLearnerProfileException if the caller already has a profile
     */
    @Transactional
    public LearnerProfile createProfile(CreateLearnerProfileCommand command) {
        AuthSubject subject = new AuthSubject(command.authSubject());
        if (repository.existsByAuthSubject(subject)) {
            throw new DuplicateLearnerProfileException();
        }
        LearnerProfile profile = LearnerProfile.create(
                subject, new DisplayName(command.displayName()), clock);
        return repository.save(profile);
    }

    /**
     * Records one observation against a competency on the caller's own profile, re-resolving the
     * affected assertion across its whole evidence set.
     *
     * <p>{@code recordedAt} comes from the injected {@link Clock}, never from the caller: a
     * supplied timestamp is unverifiable and would let a caller post-date evidence to win the
     * recency tie-break in the resolution policy.
     *
     * @throws LearnerProfileNotFoundException if the caller has no profile — authentication and
     *                                         enrolment are different events (ADR-017)
     */
    @Transactional
    public LearnerProfile recordEvidence(RecordEvidenceCommand command) {
        LearnerProfile profile = repository.findByAuthSubject(new AuthSubject(command.authSubject()))
                .orElseThrow(LearnerProfileNotFoundException::forCaller);

        EvidenceRecord evidence = new EvidenceRecord(
                command.type(),
                new AttainedLevel(command.claimedOrdinal(), command.claimedLevelCode()),
                new Confidence(command.confidence()),
                command.source(),
                clock.instant());

        profile.recordEvidence(
                new CompetencyId(command.competencyId()),
                new CompetencyFrameworkId(command.competencyFrameworkId()),
                evidence,
                resolutionPolicy,
                clock);

        return repository.save(profile);
    }

    /**
     * The caller's own profile. Absence is an ordinary outcome, not an error condition: a valid
     * token does not imply enrolment.
     */
    @Transactional(readOnly = true)
    public LearnerProfile getOwnProfile(AuthSubject caller) {
        return repository.findByAuthSubject(caller)
                .orElseThrow(LearnerProfileNotFoundException::forCaller);
    }

    /**
     * A profile addressed by identifier, subject to ownership.
     *
     * <p><b>Denial is indistinguishable from absence.</b> A caller who may not read this profile
     * receives exactly the exception a caller asking for a non-existent one receives. Answering
     * "forbidden" would confirm that the identifier names a real profile and turn this method into
     * an enumeration oracle over learner identifiers — the same reasoning that makes token
     * issuance answer an unknown user and a wrong password identically. Insufficient <em>role</em>
     * still yields 403 from the filter chain, because that discloses nothing about any particular
     * resource.
     *
     * @param callerMayReadAny resolved in the security layer from the caller's role; this layer
     *                         enforces the answer without knowing how it was reached
     */
    @Transactional(readOnly = true)
    public LearnerProfile getProfileForReader(LearnerId id, AuthSubject caller,
                                              boolean callerMayReadAny) {
        LearnerProfile profile = repository.findById(id)
                .orElseThrow(() -> LearnerProfileNotFoundException.forId(id));

        if (!callerMayReadAny && !profile.isOwnedBy(caller)) {
            throw LearnerProfileNotFoundException.forId(id);
        }
        return profile;
    }

    /**
     * Metadata summaries for every profile. Assertion graphs are never loaded on this path — the
     * projection discipline inherited from the Step 2 framework listing. Authorisation for this
     * operation is coarse and role-shaped, so it stays in the filter chain (ADR-015).
     */
    @Transactional(readOnly = true)
    public List<LearnerProfileSummary> listProfiles() {
        return repository.findAllSummaries();
    }
}
