package ie.ul.egas.competency.infrastructure.web;

import ie.ul.egas.competency.api.CompetencyFrameworkId;
import ie.ul.egas.competency.application.CompetencyFrameworkService;
import ie.ul.egas.competency.infrastructure.web.dto.FrameworkDetailResponse;
import ie.ul.egas.competency.infrastructure.web.dto.FrameworkSummaryResponse;
import ie.ul.egas.competency.infrastructure.web.dto.RegisterFrameworkRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Driving web adapter for the framework model registry. Thin by design: HTTP concerns
 * (status codes, Location header, DTO mapping) only — orchestration lives in the application
 * service, rules in the domain. Endpoints require authentication (deny-by-default baseline);
 * role-differentiated authorisation arrives with ADR-010 in Step 3.
 */
@RestController
@RequestMapping("/api/frameworks")
@Tag(name = "Competency Frameworks",
        description = "Registry of competency framework models (M1) conforming to the EGAS competency metamodel (M2)")
class CompetencyFrameworkController {

    private final CompetencyFrameworkService service;
    private final FrameworkWebMapper mapper;

    CompetencyFrameworkController(CompetencyFrameworkService service, FrameworkWebMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Register a competency framework model",
            description = "Validates the submitted model against the competency metamodel and its "
                    + "well-formedness invariants; rejects non-conforming models with a violation report.")
    @ApiResponse(responseCode = "201", description = "Framework registered")
    @ApiResponse(responseCode = "400", description = "Malformed request payload")
    @ApiResponse(responseCode = "409", description = "A framework with this name and version already exists")
    @ApiResponse(responseCode = "422", description = "Model does not conform to the competency metamodel")
    ResponseEntity<FrameworkSummaryResponse> register(@Valid @RequestBody RegisterFrameworkRequest request,
                                                      UriComponentsBuilder uriBuilder) {
        var framework = service.register(mapper.toCommand(request));
        URI location = uriBuilder.path("/api/frameworks/{id}")
                .buildAndExpand(framework.id().value())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toSummary(framework));
    }

    @GetMapping
    @Operation(summary = "List registered frameworks",
            description = "Metadata summaries only; model content is never loaded on this path.")
    List<FrameworkSummaryResponse> list() {
        return service.list().stream().map(mapper::toSummary).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a framework with its full model tree")
    @ApiResponse(responseCode = "200", description = "Framework found")
    @ApiResponse(responseCode = "404", description = "No framework with this id")
    FrameworkDetailResponse get(@PathVariable UUID id) {
        return mapper.toDetail(service.getById(new CompetencyFrameworkId(id)));
    }
}
