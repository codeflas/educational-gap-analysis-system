package ie.ul.egas.competency;

import ie.ul.egas.competency.domain.model.FrameworkDescriptor;
import ie.ul.egas.competency.domain.model.FrameworkName;
import ie.ul.egas.competency.domain.model.FrameworkSource;
import ie.ul.egas.competency.domain.model.FrameworkVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Value objects: validating, normalising, equality by value. */
class FrameworkValueObjectTests {

    @Test
    void frameworkNameTrimsAndValidates() {
        assertThat(new FrameworkName("  SFIA  ").value()).isEqualTo("SFIA");
        assertThatThrownBy(() -> new FrameworkName("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FrameworkName("x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void frameworkVersionAcceptsRealWorldVersionLabels() {
        // Deliberately permissive (RQ1): SFIA "8", ESCO "v1.1.0", year-based "2023".
        assertThat(new FrameworkVersion("8").value()).isEqualTo("8");
        assertThat(new FrameworkVersion("v1.1.0").value()).isEqualTo("v1.1.0");
        assertThat(new FrameworkVersion("2023").value()).isEqualTo("2023");
        assertThatThrownBy(() -> new FrameworkVersion("has spaces"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FrameworkVersion(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptorNormalisesBlankDescriptionToNull() {
        var descriptor = new FrameworkDescriptor(
                new FrameworkName("SFIA"), new FrameworkVersion("8"), FrameworkSource.SFIA, "   ");
        assertThat(descriptor.description()).isNull();
    }
}
