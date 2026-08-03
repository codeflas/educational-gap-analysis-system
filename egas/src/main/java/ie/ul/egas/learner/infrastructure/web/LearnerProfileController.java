package ie.ul.egas.learner.infrastructure.web;

import ie.ul.egas.learner.api.LearnerId;
import ie.ul.egas.learner.application.LearnerProfileService;
import ie.ul.egas.learner.domain.model.AuthSubject;
import ie.ul.egas.learner.domain.model.LearnerProfile;
import ie.ul.egas.learner.infrastructure.web.dto.CreateLearnerProfileRequest;
import ie.ul.egas.learner.infrastructure.web.dto.LearnerProfileResponse;
import ie.ul.egas.learner.infrastructure.web.dto.LearnerProfileSummaryResponse;
import ie.ul.egas.learner.infrastructure.web.dto.RecordEvidenceRequest;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Driving web adapter for learner profiles. Thin by design: it binds and validates, extracts the
 * two things only this layer can know, delegates, and shapes the response.
 *
 * <p><b>Identity comes from the token, never the body.</b> The subject is read from the validated
 * JWT and handed to the mapper as an explicit argument (ADR-016). No request record carries an
 * {@code authSubject} field, so a caller cannot act for someone else even by supplying one.
 *
 * <p><b>Role interpretation stops here.</b> This adapter resolves "may this caller read any
 * profile?" to a boolean and passes that inward; the application layer enforces the answer without
 * knowing how it was reached (ADR-015 Amendment 1). The authority names below are literals rather
 * than a reference to {@code platform.security.Role}, because {@code learner} declares
 * {@code allowedDependencies = {"competency :: api"}} and may not depend on {@code platform} —
 * the duplication is the accepted cost of that boundary, and the role-matrix tests are its guard.
 * It is the same class of trade-off ADR-015 already records for URL paths, which are likewise
 * named in both the filter chain and the handler.
 *
 * <p><b>Ownership is not decided here.</b> A profile the caller may not see is reported as absent
 * by the application layer, so this controller has no forbidden branch to write — which is exactly
 * why the endpoint cannot be used to enumerate learner identifiers.
 */
@RestController
@RequestMapping("/api/learners")
@Tag(name = "Learner Profiles",
        description = "Evidence-backed learner proficiency profiles (ADR-017, ADR-018)")
class LearnerProfileController {

    private static final String ROLE_EDUCATOR = "ROLE_EDUCATOR";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final LearnerProfileService service;
    private final LearnerWebMapper mapper;

    LearnerProfileController(LearnerProfileService service, LearnerWebMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/me")
    @Operation(summary = "Provision the caller's own learner profile",
            description = "Provisioning is an explicit act, never a side effect of reading "
                    + "(ADR-017). The subject is taken from the token; any authSubject supplied in "
                    + "the body is ignored.")
    @ApiResponse(responseCode = "201", description = "Profile created")
    @ApiResponse(responseCode = "400", description = "Malformed request payload")
    @ApiResponse(responseCode = "409", description = "The caller already has a profile")
    ResponseEntity<LearnerProfileResponse> createOwnProfile(
            @Valid @RequestBody CreateLearnerProfileRequest request,
            @AuthenticationPrincipal Jwt jwt,
            UriComponentsBuilder uriBuilder) {

        LearnerProfile profile = service.createProfile(mapper.toCommand(request, jwt.getSubject()));
        URI location = uriBuilder.path("/api/learners/{id}")
                .buildAndExpand(profile.id().value())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(profile));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the caller's own profile",
            description = "A valid token does not imply enrolment: a caller with no profile "
                    + "receives 404, which is an ordinary outcome rather than an error condition.")
    @ApiResponse(responseCode = "200", description = "Profile found")
    @ApiResponse(responseCode = "404", description = "The caller has no profile")
    LearnerProfileResponse getOwnProfile(@AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(service.getOwnProfile(new AuthSubject(jwt.getSubject())));
    }

    @PostMapping("/me/evidence")
    @Operation(summary = "Record evidence against a competency on the caller's own profile",
            description = "Appends the observation and re-resolves the affected assertion across "
                    + "its whole evidence set. The recording timestamp is assigned by the server "
                    + "(ADR-018 Amendment 1).")
    @ApiResponse(responseCode = "200", description = "Evidence recorded; updated profile returned")
    @ApiResponse(responseCode = "400", description = "Malformed request payload")
    @ApiResponse(responseCode = "404", description = "The caller has no profile")
    @ApiResponse(responseCode = "422", description = "Evidence conflicts with the existing assertion")
    LearnerProfileResponse recordEvidence(@Valid @RequestBody RecordEvidenceRequest request,
                                          @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(service.recordEvidence(mapper.toCommand(request, jwt.getSubject())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a profile by identifier",
            description = "Readable by its owner, or by an educator or administrator. A caller who "
                    + "may not read it receives 404 rather than 403, so that a profile's existence "
                    + "is not disclosed (ADR-015 Amendment 1).")
    @ApiResponse(responseCode = "200", description = "Profile found and readable")
    @ApiResponse(responseCode = "404", description = "No such profile, or not readable by this caller")
    LearnerProfileResponse getProfile(@PathVariable UUID id,
                                      @AuthenticationPrincipal Jwt jwt,
                                      Authentication authentication) {
        return mapper.toResponse(service.getProfileForReader(
                new LearnerId(id),
                new AuthSubject(jwt.getSubject()),
                mayReadAnyProfile(authentication)));
    }

    @GetMapping
    @Operation(summary = "List profile summaries",
            description = "Metadata and assertion counts only; evidence graphs are never loaded. "
                    + "Restricted to EDUCATOR and ADMIN by the filter chain — a coarse, role-shaped "
                    + "question that discloses nothing about any particular profile.")
    List<LearnerProfileSummaryResponse> listProfiles() {
        return service.listProfiles().stream().map(mapper::toSummary).toList();
    }

    /**
     * Resolves the caller's role question to the single boolean the application layer consumes.
     * Insufficient role for <em>this</em> predicate is not an error — it narrows what the caller
     * may see, and the application layer reports the narrowing as absence.
     */
    private boolean mayReadAnyProfile(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> ROLE_EDUCATOR.equals(authority) || ROLE_ADMIN.equals(authority));
    }
}
