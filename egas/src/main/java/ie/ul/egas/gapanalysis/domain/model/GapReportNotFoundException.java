package ie.ul.egas.gapanalysis.domain.model;

/**
 * Raised when a report cannot be resolved.
 *
 * <p>Also raised, deliberately, when a caller may not see a report that does exist. The reasoning is
 * the ADR-015 amendment's: answering "forbidden" would confirm that the identifier names a real
 * report, turning the endpoint into an enumeration oracle over report identifiers — and a report
 * discloses which learner it is about, so the leak would be worse than for a profile. Absent and
 * present-but-forbidden must be indistinguishable.
 *
 * <p>Contrast {@link ForbiddenLearnerScopeException}, which is raised where no lookup happens and
 * therefore discloses nothing.
 */
public class GapReportNotFoundException extends RuntimeException {

    private GapReportNotFoundException(String message) {
        super(message);
    }

    public static GapReportNotFoundException forId(GapReportId id) {
        return new GapReportNotFoundException(
                "No gap report is available with id '%s'".formatted(id.value()));
    }
}
