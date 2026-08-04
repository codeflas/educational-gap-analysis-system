package ie.ul.egas.learner.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data internals of the persistence adapter — invisible outside this package.
 *
 * <p>Two access paths, deliberately different. Aggregate loads use an {@link EntityGraph} that
 * fetches assertions and their evidence in one query, because a profile is only ever useful whole
 * and lazy collections would otherwise issue one query per assertion. Listings use an interface
 * projection whose assertion count is computed by the database, so the evidence graph is never
 * fetched and never mapped — the same column-only discipline the framework listing follows.
 */
interface LearnerProfileSpringDataRepository extends JpaRepository<LearnerProfileJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"assertions", "assertions.evidence"})
    Optional<LearnerProfileJpaEntity> findWithAssertionsById(UUID id);

    @EntityGraph(attributePaths = {"assertions", "assertions.evidence"})
    Optional<LearnerProfileJpaEntity> findWithAssertionsByAuthSubject(String authSubject);

    boolean existsByAuthSubject(String authSubject);

    /**
     * The identifier alone, for the published identity contract. Deliberately not
     * {@code findWithAssertionsByAuthSubject}: resolving a principal to a learner reads one column,
     * and hydrating an assertion graph to discard it would be the opposite of the projection
     * discipline every other listing here follows.
     */
    @Query("select p.id from LearnerProfileJpaEntity p where p.authSubject = :authSubject")
    Optional<UUID> findIdByAuthSubject(@Param("authSubject") String authSubject);

    @Query("""
            select p.id as id,
                   p.displayName as displayName,
                   size(p.assertions) as assertionCount,
                   p.createdAt as createdAt
            from LearnerProfileJpaEntity p
            order by p.createdAt desc
            """)
    List<LearnerProfileSummaryView> findAllSummaries();

    interface LearnerProfileSummaryView {
        UUID getId();
        String getDisplayName();
        int getAssertionCount();
        Instant getCreatedAt();
    }
}
