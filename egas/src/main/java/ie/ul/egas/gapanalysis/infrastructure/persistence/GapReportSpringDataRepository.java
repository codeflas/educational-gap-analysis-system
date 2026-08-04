package ie.ul.egas.gapanalysis.infrastructure.persistence;

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
 * <p>Two access paths, deliberately different, mirroring the learner adapter. A report load uses an
 * {@link EntityGraph} that fetches findings and their evidence in one query, because a report is
 * only ever useful whole and lazy collections would issue one query per finding. Listings use an
 * interface projection whose gap count is computed by the database, so no finding and no observation
 * is ever fetched or mapped on that path.
 */
interface GapReportSpringDataRepository extends JpaRepository<GapReportJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"gaps", "gaps.evidence"})
    Optional<GapReportJpaEntity> findWithGapsById(UUID id);

    @Query("""
            select r.id as id,
                   r.frameworkId as frameworkId,
                   r.generatedAt as generatedAt,
                   size(r.gaps) as gapCount
            from GapReportJpaEntity r
            where r.learnerId = :learnerId
            order by r.generatedAt desc, r.id desc
            """)
    List<GapReportSummaryView> findSummariesByLearnerId(@Param("learnerId") UUID learnerId);

    interface GapReportSummaryView {
        UUID getId();
        UUID getFrameworkId();
        Instant getGeneratedAt();
        int getGapCount();
    }
}
