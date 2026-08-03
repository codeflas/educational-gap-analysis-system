/**
 * Gap Analysis context — the core domain. Computes typed skill gaps between a target competency
 * model and a learner profile, and will own the compiled read-side projection of validated
 * models (CQRS-lite, ADR-007), rebuilt from Competency Modelling integration events.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Gap Analysis",
        allowedDependencies = {"competency :: api", "learner :: api"})
package ie.ul.egas.gapanalysis;
