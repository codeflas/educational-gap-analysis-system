/**
 * Platform module — cross-cutting runtime configuration (security baseline, web concerns).
 *
 * <p>Not a business module: it contributes beans to the container but exposes no API, and no
 * other module may depend on it (empty {@code allowedDependencies}, no named interfaces).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Platform",
        allowedDependencies = {})
package ie.ul.egas.platform;
