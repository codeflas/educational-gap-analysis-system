package ie.ul.egas.platform.security;

/**
 * Startup-fatal JWT key configuration failure. Raised by {@link JwtKeyMaterial} to enforce the
 * amendment-A5 fail-fast policy (ADR-013): an instance outside the dev profile must never
 * silently self-sign, so missing, partial, unparseable, or mismatched key material aborts boot
 * with an actionable message instead of degrading quietly.
 */
class JwtKeyConfigurationException extends IllegalStateException {

    JwtKeyConfigurationException(String message) {
        super(message);
    }

    JwtKeyConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
