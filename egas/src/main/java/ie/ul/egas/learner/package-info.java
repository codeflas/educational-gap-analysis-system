/**
 * Learner Profiling context — evidence ingestion and resolution of proficiency levels against
 * competencies. References the Competency Modelling context by {@code CompetencyId} only.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Learner Profiling",
        allowedDependencies = {"competency :: api"})
package ie.ul.egas.learner;
