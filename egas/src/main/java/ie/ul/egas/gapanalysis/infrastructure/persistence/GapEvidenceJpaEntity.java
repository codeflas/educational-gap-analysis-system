package ie.ul.egas.gapanalysis.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence mapping entity for one copied observation — adapter-private, never a domain object.
 *
 * <p>{@code type} is a {@code String} column, not the enum mapping its learner-side counterpart
 * uses. Learner Profiling flattens its {@code EvidenceType} at its own published boundary, so what
 * arrives here is already a string; re-typing it would assert a shared vocabulary these contexts
 * deliberately do not have, and would make an unrecognised value from a future producer a load
 * failure rather than a faithfully-recorded fact.
 *
 * <p>{@code confidence} is {@link BigDecimal} against a {@code numeric(4,3)} column rather than a
 * float, matching the learner side: it is part of the justification a stored report offers, and
 * binary drift would change what the report is understood to have said.
 *
 * <p>{@code source} is nullable where the learner column is not, because {@code EvidenceSnapshot}
 * admits a null source. Refusing the record over a missing citation would discard the rest of the
 * provenance to enforce a field the domain does not require.
 */
@Entity
@Table(name = "gap_evidence", schema = "gap_analysis",
        uniqueConstraints = @UniqueConstraint(name = "uq_gap_evidence_skill_gap_seq",
                columnNames = {"skill_gap_id", "seq"}))
class GapEvidenceJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_gap_id", nullable = false)
    private SkillGapJpaEntity gap;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "claimed_ordinal", nullable = false)
    private int claimedOrdinal;

    @Column(name = "claimed_level_code", nullable = false, length = 50)
    private String claimedLevelCode;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(length = 500)
    private String source;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "seq", nullable = false)
    private int seq;

    protected GapEvidenceJpaEntity() {
        // JPA
    }

    GapEvidenceJpaEntity(UUID id, String type, int claimedOrdinal, String claimedLevelCode,
                         BigDecimal confidence, String source, Instant recordedAt) {
        this.id = id;
        this.type = type;
        this.claimedOrdinal = claimedOrdinal;
        this.claimedLevelCode = claimedLevelCode;
        this.confidence = confidence;
        this.source = source;
        this.recordedAt = recordedAt;
    }

    void assignTo(SkillGapJpaEntity owner, int sequence) {
        this.gap = owner;
        this.seq = sequence;
    }

    String getType() { return type; }
    int getClaimedOrdinal() { return claimedOrdinal; }
    String getClaimedLevelCode() { return claimedLevelCode; }
    BigDecimal getConfidence() { return confidence; }
    String getSource() { return source; }
    Instant getRecordedAt() { return recordedAt; }
    int getSeq() { return seq; }
}
