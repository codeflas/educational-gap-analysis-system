package ie.ul.egas.gapanalysis.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence mapping entity for a gap report — an adapter-private artifact, never a domain object
 * (the domain aggregate is {@code GapReport}). Relational rather than a jsonb document per ADR-020,
 * whose test this passes for the same reason a learner profile does: the shape is fixed by Java
 * types, and two of the constraints in V401 are domain invariants a document column could not
 * express.
 *
 * <p>Findings cascade from here with {@code orphanRemoval}, because the report is the consistency
 * boundary. That {@code SkillGapId} is nonetheless published in {@code api} is not a contradiction:
 * a gap is addressable by a downstream context as a thing to act on, while remaining owned by the
 * report that computed it — the ADR-021 shape where Recommendation consumes findings without
 * reaching back into the contexts that produced them.
 *
 * <p>{@code learnerId} and {@code frameworkId} are plain UUID columns with no foreign key and no
 * association: ADR-011 prohibits a cross-schema key and ADR-019 accepts the reference as
 * unvalidated. A report naming a learner who has since been removed stays readable, which is
 * correct for a historical record.
 */
@Entity
@Table(name = "gap_report", schema = "gap_analysis")
class GapReportJpaEntity {

    @Id
    private UUID id;

    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;

    @Column(name = "framework_id", nullable = false)
    private UUID frameworkId;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    /**
     * A {@code Set}, not a {@code List}, so that this collection and the evidence collection below
     * it can be fetched together: Hibernate refuses to fetch two bags simultaneously, and a
     * {@code List} without an order column is a bag. {@code @OrderBy} still applies — Hibernate
     * materialises an ordered {@code LinkedHashSet}.
     *
     * <p>Ordering by competency code makes a re-read deterministic, and carries {@code id} as a
     * secondary key because a code is unique within a report but the ordering must be total for the
     * database's own choice never to show through. Nothing in the domain depends on gap order —
     * {@code gapFor} is a keyed lookup — and making it total now is cheaper than discovering later
     * that a report rendered its findings differently on two consecutive reads.
     */
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("competencyCode asc, id asc")
    private Set<SkillGapJpaEntity> gaps = new LinkedHashSet<>();

    protected GapReportJpaEntity() {
        // JPA
    }

    GapReportJpaEntity(UUID id, UUID learnerId, UUID frameworkId, Instant generatedAt) {
        this.id = id;
        this.learnerId = learnerId;
        this.frameworkId = frameworkId;
        this.generatedAt = generatedAt;
    }

    /** Adds a finding and sets both sides of the association, as JPA requires. */
    void addGap(SkillGapJpaEntity gap) {
        gap.assignTo(this);
        gaps.add(gap);
    }

    UUID getId() { return id; }
    UUID getLearnerId() { return learnerId; }
    UUID getFrameworkId() { return frameworkId; }
    Instant getGeneratedAt() { return generatedAt; }
    Set<SkillGapJpaEntity> getGaps() { return gaps; }
}
