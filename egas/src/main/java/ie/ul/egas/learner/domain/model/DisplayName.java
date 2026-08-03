package ie.ul.egas.learner.domain.model;

import java.util.Objects;

/**
 * The name a learner is shown by. The only personal data the profile holds beyond its
 * {@link AuthSubject}, deliberately: the model carries no email, date of birth, or institutional
 * identifier, so the context's GDPR surface stays as small as the use cases allow.
 */
public record DisplayName(String value) {

    private static final int MAX_LENGTH = 200;

    public DisplayName {
        Objects.requireNonNull(value, "Display name must not be null");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Display name must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Display name must not exceed " + MAX_LENGTH + " characters");
        }
    }
}
