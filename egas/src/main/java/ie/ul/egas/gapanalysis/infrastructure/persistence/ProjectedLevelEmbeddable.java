package ie.ul.egas.gapanalysis.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * One projected proficiency level, embedded in its framework's collection table.
 *
 * <p>{@code equals} and {@code hashCode} are by code alone, matching the {@code (framework_id,
 * code)} primary key the migration declares: within one framework a code identifies a level, so
 * two embeddables sharing a code are the same row and must not both survive into the set.
 */
@Embeddable
class ProjectedLevelEmbeddable {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 200)
    private String name;

    @Column(nullable = false)
    private int ordinal;

    protected ProjectedLevelEmbeddable() {
        // JPA
    }

    ProjectedLevelEmbeddable(String code, String name, int ordinal) {
        this.code = code;
        this.name = name;
        this.ordinal = ordinal;
    }

    String getCode() { return code; }
    String getName() { return name; }
    int getOrdinal() { return ordinal; }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof ProjectedLevelEmbeddable that && Objects.equals(code, that.code));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}
