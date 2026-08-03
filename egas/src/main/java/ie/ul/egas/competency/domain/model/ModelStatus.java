package ie.ul.egas.competency.domain.model;

/**
 * Lifecycle state of a registered framework model. Registration yields {@link #DRAFT};
 * the {@code publish} transition (with its {@code ModelPublished} integration event feeding the
 * Gap Analysis read projection, ADR-007) arrives in W6. Establishing the state now avoids a
 * schema migration when it does.
 */
public enum ModelStatus {
    DRAFT,
    PUBLISHED
}
