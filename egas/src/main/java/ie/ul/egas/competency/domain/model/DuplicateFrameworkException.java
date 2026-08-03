package ie.ul.egas.competency.domain.model;

/**
 * Raised when a framework with the same (name, version) already exists. Thrown by the
 * application service on the fast-path check and by the persistence adapter when the database
 * unique constraint fires (closing the check-then-act race).
 */
public class DuplicateFrameworkException extends RuntimeException {

    private final FrameworkName name;
    private final FrameworkVersion version;

    public DuplicateFrameworkException(FrameworkName name, FrameworkVersion version) {
        super("A competency framework named '%s' with version '%s' is already registered"
                .formatted(name.value(), version.value()));
        this.name = name;
        this.version = version;
    }

    public FrameworkName name() { return name; }
    public FrameworkVersion version() { return version; }
}
