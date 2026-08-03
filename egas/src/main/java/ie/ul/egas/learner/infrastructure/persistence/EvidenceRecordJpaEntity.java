package ie.ul.egas.learner.infrastructure.persistence;

import ie.ul.egas.learner.domain.model.EvidenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Persistence mapping entity for one evidence record — adapter-private, never a domain object.
 *
 * <p>{@code type} is stored by name, never by ordinal: a reordered enum constant would otherwise
 * silently rewrite the meaning of every historical row.
 *
 * <p>{@code confidence} is {@link BigDecimal} against a {@code numeric(4,3)} column rather than a
 * float, because it is an input to level resolution and binary drift there would change a stored
 * judgement. {@code seq} carries insertion order explicitly, since evidence is exposed oldest-first
 * and {@code recorded_at} is not unique (ADR-018).
 */
@Entity
@Table(name = "evidence_record", schema = "learner",
        uniqueConstraints = @UniqueConstraint(name = "uq_evidence_assertion_seq",
                columnNames = {"assertion_id", "seq"}))
class EvidenceRecordJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assertion_id", nullable = false)
    private ProficiencyAssertionJpaEntity assertion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvidenceType type;

    @Column(name = "level_ordinal", nullable = false)
    private int levelOrdinal;

    @Column(name = "level_code", nullable = false, length = 50)
    private String levelCode;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(nullable = false, length = 500)
    private String source;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "seq", nullable = false)
    private int seq;

    protected EvidenceRecordJpaEntity() {
        // JPA
    }

    EvidenceRecordJpaEntity(UUID id, EvidenceType type, int levelOrdinal, String levelCode,
                            BigDecimal confidence, String source, Instant recordedAt) {
        this.id = id;
        this.type = type;
        this.levelOrdinal = levelOrdinal;
        this.levelCode = levelCode;
        this.confidence = confidence;
        this.source = source;
        this.recordedAt = recordedAt;
    }

    void assignTo(ProficiencyAssertionJpaEntity owner, int sequence) {
        this.assertion = owner;
        this.seq = sequence;
    }

    EvidenceType getType() { return type; }
    int getLevelOrdinal() { return levelOrdinal; }
    String getLevelCode() { return levelCode; }
    BigDecimal getConfidence() { return confidence; }
    String getSource() { return source; }
    Instant getRecordedAt() { return recordedAt; }
    int getSeq() { return seq; }
}
