package ie.ul.egas.learner.domain.policy;

import ie.ul.egas.learner.domain.model.AttainedLevel;
import ie.ul.egas.learner.domain.model.EvidenceRecord;

import java.util.Comparator;
import java.util.List;

/**
 * Default {@link LevelResolutionPolicy}: the most confident claim wins (ADR-018).
 *
 * <p>Ties are broken by recency, then by the higher ordinal, and finally by level code — the last
 * of these supplied by {@link AttainedLevel}'s own ordering, which is total because it agrees with
 * its {@code equals}. The tie-breaks exist to make the outcome <em>total</em>: without them, two
 * equally confident claims recorded in the same instant would resolve according to list order, and
 * an assertion's level would depend on the sequence in which its evidence happened to be persisted.
 * Determinism is part of the port's contract, not an incidental property of this implementation.
 *
 * <p>Records that tie on all four criteria necessarily carry the <em>same</em> claimed level, so
 * the resolved value is identical whichever of them is selected. That is what closes the contract:
 * the winning record may be order-dependent, the answer never is.
 *
 * <p>Choosing confidence as the primary criterion rather than, say, the highest claimed level is a
 * substantive judgement: it means a hesitant claim of level 5 loses to a certain claim of level 3,
 * which is the conservative reading appropriate to a system whose downstream consumer computes
 * skill gaps and recommends remediation. Over-stating attainment suppresses a gap that exists;
 * under-stating it surfaces one that may not. The asymmetry favours the second error — and where
 * an institution disagrees, ADR-018's port is the seam at which it substitutes its own rule.
 */
public final class HighestConfidenceResolutionPolicy implements LevelResolutionPolicy {

    private static final Comparator<EvidenceRecord> MOST_AUTHORITATIVE_LAST =
            Comparator.comparing(EvidenceRecord::confidence)
                    .thenComparing(EvidenceRecord::recordedAt)
                    .thenComparing(EvidenceRecord::claimedLevel);

    @Override
    public AttainedLevel resolve(List<EvidenceRecord> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            throw new UnresolvableEvidenceException(
                    "Cannot resolve a proficiency level from an empty evidence set");
        }
        return evidence.stream()
                .max(MOST_AUTHORITATIVE_LAST)
                .orElseThrow(() -> new UnresolvableEvidenceException(
                        "Cannot resolve a proficiency level from the supplied evidence"))
                .claimedLevel();
    }
}
