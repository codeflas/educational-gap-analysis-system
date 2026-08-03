package ie.ul.egas.learner.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence mapping entity for one proficiency assertion — adapter-private, never a domain
 * object.
 *
 * <p>{@code competencyId} and {@code frameworkId} are plain UUID columns with no foreign key:
 * ADR-011 prohibits a cross-schema key and ADR-019 accepts the reference as unvalidated. The
 * unique constraint on (profile, competency) is the one that matters — it is the aggregate's
 * central invariant made unviolatable at rest (ADR-020).
 *
 * <p>Level ordinal and code are both stored because neither is derivable from the other without
 * the competency model this context may not reach for (ADR-018).
 */
@Entity
@Table(name = "proficiency_assertion", schema = "learner",
        uniqueConstraints = @UniqueConstraint(name = "uq_assertion_profile_competency",
                columnNames = {"profile_id", "competency_id"}))
class ProficiencyAssertionJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private LearnerProfileJpaEntity profile;

    @Column(name = "competency_id", nullable = false)
    private UUID competencyId;

    @Column(name = "framework_id", nullable = false)
    private UUID frameworkId;

    @Column(name = "level_ordinal", nullable = false)
    private int levelOrdinal;

    @Column(name = "level_code", nullable = false, length = 50)
    private String levelCode;

    @Column(name = "resolved_at", nullable = false)
    private Instant resolvedAt;

    /** A {@code Set} for the same reason as the profile's assertions: two bags cannot be fetched
     * together. {@code @OrderBy("seq asc")} preserves append order in a {@code LinkedHashSet}. */
    @OneToMany(mappedBy = "assertion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq asc")
    private Set<EvidenceRecordJpaEntity> evidence = new LinkedHashSet<>();

    protected ProficiencyAssertionJpaEntity() {
        // JPA
    }

    ProficiencyAssertionJpaEntity(UUID id, UUID competencyId, UUID frameworkId,
                                  int levelOrdinal, String levelCode, Instant resolvedAt) {
        this.id = id;
        this.competencyId = competencyId;
        this.frameworkId = frameworkId;
        this.levelOrdinal = levelOrdinal;
        this.levelCode = levelCode;
        this.resolvedAt = resolvedAt;
    }

    void assignTo(LearnerProfileJpaEntity owner) {
        this.profile = owner;
    }

    /** Appends evidence, setting both sides of the association and its explicit sequence. */
    void addEvidence(EvidenceRecordJpaEntity record) {
        record.assignTo(this, evidence.size());
        evidence.add(record);
    }

    UUID getId() { return id; }
    UUID getCompetencyId() { return competencyId; }
    UUID getFrameworkId() { return frameworkId; }
    int getLevelOrdinal() { return levelOrdinal; }
    String getLevelCode() { return levelCode; }
    Instant getResolvedAt() { return resolvedAt; }
    Set<EvidenceRecordJpaEntity> getEvidence() { return evidence; }
}
