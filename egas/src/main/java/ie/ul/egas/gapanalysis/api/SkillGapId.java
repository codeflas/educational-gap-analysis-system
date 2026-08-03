package ie.ul.egas.gapanalysis.api;

import ie.ul.egas.shared.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Identity of a computed skill gap (one element of a gap analysis result set). */
public record SkillGapId(UUID value) implements Identifier {

    public SkillGapId {
        Objects.requireNonNull(value, "SkillGapId requires a non-null UUID");
    }

    public static SkillGapId random() {
        return new SkillGapId(UUID.randomUUID());
    }

    public static SkillGapId of(String raw) {
        return new SkillGapId(UUID.fromString(raw));
    }
}
