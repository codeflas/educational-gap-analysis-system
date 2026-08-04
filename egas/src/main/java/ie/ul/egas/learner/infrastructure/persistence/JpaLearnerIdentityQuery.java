package ie.ul.egas.learner.infrastructure.persistence;

import ie.ul.egas.learner.api.LearnerId;
import ie.ul.egas.learner.api.LearnerIdentityQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Adapter implementing the published identity contract (ADR-017 Amendment 1).
 *
 * <p><b>An adapter rather than an application service, deliberately.</b> ADR-017 calls the mapping
 * an application-<em>boundary</em> concern, meaning it is resolved inside Learner Profiling rather
 * than derived by the security layer or reached for through another context's schema — which this
 * satisfies, since the only thing crossing the module boundary is a published port. What it is not
 * is a use case: there is no orchestration, no policy, no transaction script, and nothing to decide.
 * Routing it through {@code LearnerProfileService} would add a layer that forwards a call, and doing
 * so through {@code findByAuthSubject} would hydrate an entire assertion graph to read one
 * identifier. {@code JpaLearnerAttainmentQuery} sits here for exactly the same reasons.
 *
 * <p>The placement is not permanent. Should resolution ever acquire a rule — several credentials per
 * profile, or a renamed principal, both anticipated in ADR-017's future evolution — it moves to the
 * application layer and this port's signature does not change.
 */
@Component
class JpaLearnerIdentityQuery implements LearnerIdentityQuery {

    private final LearnerProfileSpringDataRepository springData;

    JpaLearnerIdentityQuery(LearnerProfileSpringDataRepository springData) {
        this.springData = springData;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LearnerId> learnerIdFor(String authSubject) {
        if (authSubject == null) {
            return Optional.empty();
        }
        return springData.findIdByAuthSubject(authSubject).map(LearnerId::new);
    }
}
