package ie.ul.egas.shared;

import java.util.UUID;

/**
 * Contract for strongly typed, UUID-backed identifiers.
 *
 * <p>Cross-context references are made exclusively by identifier — never by object reference —
 * keeping bounded contexts decoupled (Evans 2004; Vernon 2013). Each context defines its own
 * identifier records in its {@code api} package; this interface only fixes the representation so
 * that adapters (web serialisation, persistence) can treat identifiers uniformly.
 */
public interface Identifier {

    UUID value();
}
