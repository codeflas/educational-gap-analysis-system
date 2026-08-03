package ie.ul.egas.competency.application;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.api.CompetencyModelRegistered;
import ie.ul.egas.competency.domain.CompetencyFrameworkRepository;
import ie.ul.egas.competency.domain.model.CompetencyFramework;
import ie.ul.egas.competency.domain.model.DuplicateFrameworkException;
import ie.ul.egas.competency.domain.model.FrameworkDescriptor;
import ie.ul.egas.competency.domain.model.FrameworkName;
import ie.ul.egas.competency.domain.model.FrameworkNotFoundException;
import ie.ul.egas.competency.domain.model.FrameworkSummary;
import ie.ul.egas.competency.domain.model.FrameworkVersion;
import ie.ul.egas.competency.domain.validation.ConformanceValidator;
import org.eclipse.emf.ecore.EObject;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Application service for the framework-model lifecycle: the transaction boundary and the
 * orchestration of assembler (T2M injection), aggregate invariant enforcement and persistence.
 * Contains no business rules of its own — those live in the aggregate and the validator.
 *
 * <p>The duplicate check here is a fast-path courtesy; the authoritative guard is the database
 * unique constraint, whose violation the persistence adapter translates to the same domain
 * exception (check-then-act race closed).
 */
@Service
public class CompetencyFrameworkService {

    private final CompetencyFrameworkRepository repository;
    private final ConformanceValidator validator;
    private final FrameworkModelAssembler assembler;
    private final CompetencyModelCompiler compiler;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CompetencyFrameworkService(CompetencyFrameworkRepository repository,
                                      ConformanceValidator validator,
                                      FrameworkModelAssembler assembler,
                                      CompetencyModelCompiler compiler,
                                      ApplicationEventPublisher events,
                                      Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.assembler = assembler;
        this.compiler = compiler;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CompetencyFramework register(RegisterFrameworkCommand command) {
        FrameworkDescriptor descriptor = new FrameworkDescriptor(
                new FrameworkName(command.name()),
                new FrameworkVersion(command.version()),
                command.source(),
                command.description());

        if (repository.existsByNameAndVersion(descriptor.name(), descriptor.version())) {
            throw new DuplicateFrameworkException(descriptor.name(), descriptor.version());
        }

        EObject modelRoot = assembler.assemble(command);
        CompetencyFramework framework = CompetencyFramework.register(descriptor, modelRoot, validator, clock);
        CompetencyFramework saved = repository.save(framework);

        // Announced, not delivered: Gap Analysis compiles its own projection from this (ADR-007).
        // Published inside the transaction so registration and announcement commit together; the
        // durable registry then makes delivery recoverable if a consumer fails (ADR-022).
        events.publishEvent(new CompetencyModelRegistered(
                saved.id(), saved.registeredAt(), compiler.compile(saved)));

        return saved;
    }

    @Transactional(readOnly = true)
    public CompetencyFramework getById(CompetencyFrameworkId id) {
        return repository.findById(id).orElseThrow(() -> new FrameworkNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<FrameworkSummary> list() {
        return repository.findAllSummaries();
    }
}
