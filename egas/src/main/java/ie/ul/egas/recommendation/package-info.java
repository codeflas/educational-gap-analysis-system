/**
 * Recommendation context — learning pathway synthesis over competency prerequisite graphs with
 * pluggable strategies (ADR-006). Explanation generation is isolated behind an ExplanationPort
 * with template/Ollama/OpenAI adapters so the core pathway computation stays deterministic and
 * the system degrades gracefully without an LLM.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Recommendation",
        allowedDependencies = {"competency :: api", "gapanalysis :: api", "catalogue :: api"})
package ie.ul.egas.recommendation;
