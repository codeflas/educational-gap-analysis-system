package ie.ul.egas.learner.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence mapping entity for a learner profile — an adapter-private artifact, never a domain
 * object (the domain aggregate is {@code LearnerProfile}). Relational rather than a jsonb
 * document per ADR-020.
 *
 * <p>Assertions cascade from here with {@code orphanRemoval}, because the profile is the
 * consistency boundary: nothing outside it addresses an assertion, which is why {@code AssertionId}
 * lives in {@code domain.model} rather than the published {@code api} package.
 */
@Entity
@Table(name = "profile", schema = "learner",
        uniqueConstraints = @UniqueConstraint(name = "uq_learner_auth_subject",
                columnNames = "auth_subject"))
class LearnerProfileJpaEntity {

    @Id
    private UUID id;

    @Column(name = "auth_subject", nullable = false, length = 200)
    private String authSubject;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * A {@code Set}, not a {@code List}, so that this collection and the evidence collection below
     * it can be fetched in one query: Hibernate refuses to fetch two bags simultaneously, and a
     * {@code List} without an order column is a bag. {@code @OrderBy} still applies — Hibernate
     * materialises an ordered {@code LinkedHashSet}.
     *
     * <p>The ordering carries {@code id} as a secondary key because {@code resolvedAt} is not
     * unique: assertions resolved in the same instant would otherwise come back in whatever order
     * the database happened to return, which is the same partial-order trap {@code AttainedLevel}
     * fell into. Nothing depends on assertion order today — {@code assertionFor} is a keyed
     * lookup — and making it total now is cheaper than discovering later that something does.
     */
    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("resolvedAt asc, id asc")
    private Set<ProficiencyAssertionJpaEntity> assertions = new LinkedHashSet<>();

    protected LearnerProfileJpaEntity() {
        // JPA
    }

    LearnerProfileJpaEntity(UUID id, String authSubject, String displayName, Instant createdAt) {
        this.id = id;
        this.authSubject = authSubject;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }

    /** Adds an assertion and sets both sides of the association, as JPA requires. */
    void addAssertion(ProficiencyAssertionJpaEntity assertion) {
        assertion.assignTo(this);
        assertions.add(assertion);
    }

    UUID getId() { return id; }
    String getAuthSubject() { return authSubject; }
    String getDisplayName() { return displayName; }
    Instant getCreatedAt() { return createdAt; }
    Set<ProficiencyAssertionJpaEntity> getAssertions() { return assertions; }
}
