package ie.ul.egas.competency.domain.model;

import ie.ul.egas.competency.api.CompetencyFrameworkId;

/** Raised when a framework id does not resolve to a registered framework model. */
public class FrameworkNotFoundException extends RuntimeException {

    private final CompetencyFrameworkId id;

    public FrameworkNotFoundException(CompetencyFrameworkId id) {
        super("No competency framework registered with id '%s'".formatted(id.value()));
        this.id = id;
    }

    public CompetencyFrameworkId id() { return id; }
}
