/**
 * Learning Catalogue context — learning resources and their mapping onto competencies
 * ({@code CompetencyId} references only).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Learning Catalogue",
        allowedDependencies = {"competency :: api"})
package ie.ul.egas.catalogue;
