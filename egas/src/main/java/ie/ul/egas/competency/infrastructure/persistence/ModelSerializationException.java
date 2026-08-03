package ie.ul.egas.competency.infrastructure.persistence;

/**
 * Infrastructure failure while (de)serialising a model to/from its jsonb representation.
 * Indicates a programming or data-corruption error, not a client error — surfaces as HTTP 500.
 */
class ModelSerializationException extends RuntimeException {

    ModelSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
