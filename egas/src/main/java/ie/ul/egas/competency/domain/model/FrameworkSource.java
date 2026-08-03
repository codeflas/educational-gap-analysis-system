package ie.ul.egas.competency.domain.model;

/**
 * Origin of a framework model. Mirrors the M2 EEnum {@code FrameworkSourceKind} by literal name;
 * the assembler maps between the two. This duplication is a known, accepted cost of the
 * dynamic-EMF phase (ADR-003) — after the W3 freeze the generated enum becomes the single type.
 */
public enum FrameworkSource {
    BESPOKE,
    SFIA,
    ESCO,
    OTHER
}
