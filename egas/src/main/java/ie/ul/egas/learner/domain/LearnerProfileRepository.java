package ie.ul.egas.learner.domain;

import ie.ul.egas.learner.api.LearnerId;
import ie.ul.egas.learner.domain.model.AuthSubject;
import ie.ul.egas.learner.domain.model.LearnerProfile;
import ie.ul.egas.learner.domain.model.LearnerProfileSummary;

import java.util.List;
import java.util.Optional;

/**
 * Driven port for learner profile persistence. Declared in the domain, implemented by an adapter;
 * the domain neither knows nor cares that the current adapter is JPA over PostgreSQL.
 *
 * <p>{@link #findByAuthSubject} is the mapping ADR-017 settles: the correspondence between an
 * authenticated principal and a domain profile is resolved here, at the application boundary,
 * rather than being derived from the subject or held by the security layer — which the module DAG
 * forbids this context from reaching anyway.
 *
 * <p>{@link #findAllSummaries} exists so that listing profiles never loads assertion graphs, the
 * same column-only projection discipline that kept the framework listing off the model-hydration
 * path in Step 2.
 */
public interface LearnerProfileRepository {

    LearnerProfile save(LearnerProfile profile);

    Optional<LearnerProfile> findById(LearnerId id);

    Optional<LearnerProfile> findByAuthSubject(AuthSubject subject);

    boolean existsByAuthSubject(AuthSubject subject);

    List<LearnerProfileSummary> findAllSummaries();
}
