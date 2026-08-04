package ie.ul.egas.gapanalysis.infrastructure.persistence;

import ie.ul.egas.gapanalysis.domain.model.GapSeverity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Persistence mapping entity for one finding — adapter-private, never a domain object.
 *
 * <p><b>The attainment columns are nullable together or populated together.</b> That is not a
 * mapping convenience but ADR-021's absent-attainment rule expressed in storage: a competency
 * nothing has been measured for holds no attainment at all, not a zero-ordinal one, and
 * {@code ck_skill_gap_attainment_complete} in V401 makes a half-written row impossible. The adapter
 * reconstructs either a whole {@code AttainmentSnapshot} or none, so the distinction survives a
 * round trip rather than being flattened by it.
 *
 * <p>{@code severity} is stored by name, never by ordinal: a reordered enum constant would otherwise
 * silently rewrite the meaning of every historical row. It is stored at all — rather than recomputed
 * on read — because re-running a {@code GapSeverityPolicy} on load would rewrite a historical
 * judgement whenever the configured rule changed, which is the same non-behaviour ADR-018 records
 * for level resolution.
 *
 * <p>{@code competencyId} is a plain column with no foreign key, not even to V400's
 * {@code projected_competency}: a report must stay explicable after the framework it measured has
 * been revised or removed, and a key to a derived, rebuildable table would make history hostage to a
 * projection replay.
 */
@Entity
@Table(name = "skill_gap", schema = "gap_analysis",
        uniqueConstraints = @UniqueConstraint(name = "uq_skill_gap_report_competency",
                columnNames = {"report_id", "competency_id"}))
class SkillGapJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private GapReportJpaEntity report;

    @Column(name = "competency_id", nullable = false)
    private UUID competencyId;

    @Column(name = "competency_code", nullable = false, length = 50)
    private String competencyCode;

    @Column(name = "competency_name", length = 200)
    private String competencyName;

    @Column(name = "target_level_code", nullable = false, length = 50)
    private String targetLevelCode;

    @Column(name = "target_ordinal", nullable = false)
    private int targetOrdinal;

    /** Boxed, unlike the target ordinal: absence is a state this column must be able to hold. */
    @Column(name = "attained_ordinal")
    private Integer attainedOrdinal;

    @Column(name = "attained_level_code", length = 50)
    private String attainedLevelCode;

    @Column(name = "attainment_resolved_at")
    private Instant attainmentResolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GapSeverity severity;

    /** A {@code Set} for the same reason as the report's findings: two bags cannot be fetched
     * together. {@code @OrderBy("seq asc")} preserves the order the evidence was copied in. */
    @OneToMany(mappedBy = "gap", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq asc")
    private Set<GapEvidenceJpaEntity> evidence = new LinkedHashSet<>();

    protected SkillGapJpaEntity() {
        // JPA
    }

    SkillGapJpaEntity(UUID id, UUID competencyId, String competencyCode, String competencyName,
                      String targetLevelCode, int targetOrdinal, GapSeverity severity) {
        this.id = id;
        this.competencyId = competencyId;
        this.competencyCode = competencyCode;
        this.competencyName = competencyName;
        this.targetLevelCode = targetLevelCode;
        this.targetOrdinal = targetOrdinal;
        this.severity = severity;
    }

    /**
     * Records the attainment snapshot. Called only when there is one — the three columns move
     * together, so there is deliberately no setter for any of them alone.
     */
    void recordAttainment(int ordinal, String levelCode, Instant resolvedAt) {
        this.attainedOrdinal = ordinal;
        this.attainedLevelCode = levelCode;
        this.attainmentResolvedAt = resolvedAt;
    }

    void assignTo(GapReportJpaEntity owner) {
        this.report = owner;
    }

    /** Appends one observation, setting both sides of the association and its explicit sequence. */
    void addEvidence(GapEvidenceJpaEntity record) {
        record.assignTo(this, evidence.size());
        evidence.add(record);
    }

    UUID getId() { return id; }
    UUID getCompetencyId() { return competencyId; }
    String getCompetencyCode() { return competencyCode; }
    String getCompetencyName() { return competencyName; }
    String getTargetLevelCode() { return targetLevelCode; }
    int getTargetOrdinal() { return targetOrdinal; }
    Integer getAttainedOrdinal() { return attainedOrdinal; }
    String getAttainedLevelCode() { return attainedLevelCode; }
    Instant getAttainmentResolvedAt() { return attainmentResolvedAt; }
    GapSeverity getSeverity() { return severity; }
    Set<GapEvidenceJpaEntity> getEvidence() { return evidence; }
}
