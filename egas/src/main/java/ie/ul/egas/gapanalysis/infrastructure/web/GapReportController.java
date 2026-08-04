package ie.ul.egas.gapanalysis.infrastructure.web;

import ie.ul.egas.gapanalysis.application.GapAnalysisService;
import ie.ul.egas.gapanalysis.domain.model.GapReport;
import ie.ul.egas.gapanalysis.domain.model.GapReportId;
import ie.ul.egas.gapanalysis.infrastructure.web.dto.AnalyseGapRequest;
import ie.ul.egas.gapanalysis.infrastructure.web.dto.GapReportResponse;
import ie.ul.egas.gapanalysis.infrastructure.web.dto.GapReportSummaryResponse;
import ie.ul.egas.learner.api.LearnerId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Driving web adapter for gap reports. Thin by design: it binds and validates, extracts the two
 * things only this layer can know, delegates, and shapes the response.
 *
 * <p><b>Identity comes from the token, never the body.</b> The subject is read from the validated
 * JWT and handed to the mapper as an explicit argument (ADR-016). {@code AnalyseGapRequest} names a
 * learner to analyse but carries no {@code authSubject}, so naming someone else's learner identifier
 * buys a caller nothing — the application layer compares it against who the token says they are.
 *
 * <p><b>Role interpretation stops here.</b> This adapter resolves "may this caller act for any
 * learner?" to a boolean and passes that inward; the application layer enforces the answer without
 * knowing how it was reached (ADR-015 Amendment 2). The authority names are literals rather than a
 * reference to {@code platform.security.Role}, because {@code gapanalysis} declares
 * {@code allowedDependencies = {"competency :: api", "learner :: api"}} and may not depend on
 * {@code platform} — the same accepted duplication the learner adapter carries, guarded by the same
 * kind of role-matrix test.
 *
 * <p><b>Ownership is not decided here, and the two denials are not the adapter's choice.</b> A
 * report the caller may not read is reported as absent by the application layer, so this controller
 * has no forbidden branch to write for it; an operation scoped to a learner the caller may not act
 * for raises a distinct exception that the advice renders as 403. Both shapes are decided where the
 * reasoning lives (ADR-015 Amendment 2), not at the transport tier.
 *
 * <p><b>Why {@code /api/gap-reports} rather than a path nested under a learner.</b> Each context
 * owns its own URL prefix — {@code /api/frameworks}, {@code /api/learners} — which keeps the filter
 * chain readable as one block of rules per module. Nesting these under {@code /api/learners/**}
 * would place Gap Analysis's endpoints inside a prefix Learner Profiling's rules already govern.
 */
@RestController
@RequestMapping("/api/gap-reports")
@Tag(name = "Gap Reports",
        description = "Explainable skill-gap analysis against a competency framework (ADR-021)")
class GapReportController {

    private static final String ROLE_EDUCATOR = "ROLE_EDUCATOR";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final GapAnalysisService service;
    private final GapAnalysisWebMapper mapper;

    GapReportController(GapAnalysisService service, GapAnalysisWebMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Analyse a learner's gaps against a framework",
            description = "Computes and stores a report. A learner may analyse only themselves; an "
                    + "educator or administrator may analyse anyone. Recomputation adds a report "
                    + "rather than replacing one, because a report is a record of its instant "
                    + "(ADR-021).")
    @ApiResponse(responseCode = "201", description = "Report computed and stored")
    @ApiResponse(responseCode = "400", description = "Malformed payload, or a target level the framework does not define")
    @ApiResponse(responseCode = "403", description = "The caller may not analyse this learner")
    @ApiResponse(responseCode = "422", description = "No competency model is available for the framework")
    ResponseEntity<GapReportResponse> analyse(@Valid @RequestBody AnalyseGapRequest request,
                                              @AuthenticationPrincipal Jwt jwt,
                                              Authentication authentication,
                                              UriComponentsBuilder uriBuilder) {

        GapReport report = service.analyse(
                mapper.toCommand(request, jwt.getSubject(), mayActForAnyLearner(authentication)));

        URI location = uriBuilder.path("/api/gap-reports/{id}")
                .buildAndExpand(report.id().value())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(report));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a stored report by identifier",
            description = "Readable by the learner it is about, or by an educator or administrator. "
                    + "A caller who may not read it receives 404 rather than 403, so a report's "
                    + "existence — and the learner it names — is not disclosed (ADR-015 A2).")
    @ApiResponse(responseCode = "200", description = "Report found and readable")
    @ApiResponse(responseCode = "404", description = "No such report, or not readable by this caller")
    GapReportResponse getReport(@PathVariable UUID id,
                                @AuthenticationPrincipal Jwt jwt,
                                Authentication authentication) {
        return mapper.toResponse(service.getReportForReader(
                new GapReportId(id), jwt.getSubject(), mayActForAnyLearner(authentication)));
    }

    @GetMapping
    @Operation(summary = "List a learner's report history",
            description = "Metadata and gap counts only; findings and their provenance are never "
                    + "loaded on this path. Answers 403 rather than 404 for a learner the caller "
                    + "may not read, because the identifier is supplied by the caller and never "
                    + "looked up, so refusing discloses nothing (ADR-015 A2).")
    @ApiResponse(responseCode = "200", description = "History returned, newest first")
    @ApiResponse(responseCode = "403", description = "The caller may not read this learner's history")
    List<GapReportSummaryResponse> listReports(@RequestParam UUID learnerId,
                                               @AuthenticationPrincipal Jwt jwt,
                                               Authentication authentication) {
        return service.listReportsForLearner(
                        new LearnerId(learnerId), jwt.getSubject(), mayActForAnyLearner(authentication))
                .stream()
                .map(mapper::toSummary)
                .toList();
    }

    /**
     * Resolves the caller's role question to the single boolean the application layer consumes.
     *
     * <p>One predicate feeds two command positions — {@code callerMayAnalyseAnyLearner} and
     * {@code callerMayReadAny} — because today the same roles answer both. They stay separate
     * fields inward, since "who may run an analysis for someone else" and "who may read someone
     * else's report" are different questions that a later policy may answer differently.
     */
    private boolean mayActForAnyLearner(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> ROLE_EDUCATOR.equals(authority) || ROLE_ADMIN.equals(authority));
    }
}
