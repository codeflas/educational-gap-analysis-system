package ie.ul.egas.learner.infrastructure.persistence;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyId;
import ie.ul.egas.learner.api.AttainedCompetency;
import ie.ul.egas.learner.api.LearnerAttainmentQuery;
import ie.ul.egas.learner.api.LearnerId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adapter implementing the published attainment contract (ADR-022).
 *
 * <p>It reads the same rows the aggregate does, through the same fetch-joined query, but maps
 * straight from mapping entities to published records rather than rehydrating a
 * {@code LearnerProfile} first. Building the aggregate would resolve nothing here: this is a read
 * with no invariant to enforce, and the domain object's behaviour — recording evidence, deciding
 * ownership — is irrelevant to a caller that only wants levels and their provenance.
 *
 * <p>Adding it required no schema change and no change to the aggregate: the projection discipline
 * already in place is reused as-is.
 */
@Component
class JpaLearnerAttainmentQuery implements LearnerAttainmentQuery {

    private final LearnerProfileSpringDataRepository springData;

    JpaLearnerAttainmentQuery(LearnerProfileSpringDataRepository springData) {
        this.springData = springData;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttainedCompetency> attainmentsFor(LearnerId learnerId) {
        return springData.findWithAssertionsById(learnerId.value())
                .map(profile -> profile.getAssertions().stream().map(this::toAttainment).toList())
                .orElseGet(List::of);
    }

    private AttainedCompetency toAttainment(ProficiencyAssertionJpaEntity assertion) {
        return new AttainedCompetency(
                new CompetencyId(assertion.getCompetencyId()),
                new CompetencyFrameworkId(assertion.getFrameworkId()),
                assertion.getLevelOrdinal(),
                assertion.getLevelCode(),
                assertion.getResolvedAt(),
                assertion.getEvidence().stream().map(this::toEvidence).toList());
    }

    private AttainedCompetency.EvidenceSummary toEvidence(EvidenceRecordJpaEntity record) {
        return new AttainedCompetency.EvidenceSummary(
                record.getType().name(),
                record.getLevelOrdinal(),
                record.getLevelCode(),
                record.getConfidence().doubleValue(),
                record.getSource(),
                record.getRecordedAt());
    }
}
