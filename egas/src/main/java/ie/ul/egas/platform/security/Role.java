package ie.ul.egas.platform.security;

/**
 * The closed role set of the system (Step 3 plan §1.3). Held as an enum so a mistyped role in
 * configuration fails binding at startup rather than silently producing a principal whose
 * authority matches no authorisation rule — the "silent 403" failure mode recorded as a
 * Med/High risk in the plan's risk register.
 *
 * <p>Names are the authority names <em>without</em> the {@code ROLE_} prefix: they travel in the
 * {@code roles} claim as-is, and the prefix is applied by the single {@code
 * JwtAuthenticationConverter} in the resource-server phase.
 */
public enum Role {
    EDUCATOR,
    LEARNER,
    ADMIN
}
