package de.qaware.maven.plugin.offline;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicDependencyTest {

    private DynamicDependency dependency;

    @BeforeEach
    void setUp() {
        dependency = new DynamicDependency();
        dependency.setGroupId("org.example");
        dependency.setArtifactId("example");
        dependency.setVersion("1.0");
        dependency.setRepositoryType(RepositoryType.MAIN);
    }

    @Test
    void validateAcceptsCompleteConfiguration() {
        assertDoesNotThrow(dependency::validate);
    }

    @Test
    void validateAcceptsMissingOptionalFields() {
        dependency.setClassifier(null);
        dependency.setType(null);
        assertDoesNotThrow(dependency::validate);
    }

    @Test
    void validateRejectsMissingArtifactId() {
        dependency.setArtifactId(null);
        assertValidationFails("artifactId");
        dependency.setArtifactId("");
        assertValidationFails("artifactId");
    }

    @Test
    void validateRejectsMissingGroupId() {
        dependency.setGroupId(null);
        assertValidationFails("groupId");
        dependency.setGroupId("");
        assertValidationFails("groupId");
    }

    @Test
    void validateRejectsMissingVersion() {
        dependency.setVersion(null);
        assertValidationFails("version");
        dependency.setVersion("");
        assertValidationFails("version");
    }

    @Test
    void validateRejectsMissingRepositoryType() {
        dependency.setRepositoryType(null);
        assertValidationFails("repositoryType");
    }

    @Test
    void typeDefaultsToJar() {
        dependency.setType(null);
        assertEquals("jar", dependency.getType());
        dependency.setType("");
        assertEquals("jar", dependency.getType());
    }

    @Test
    void typeReturnsConfiguredValue() {
        dependency.setType("zip");
        assertEquals("zip", dependency.getType());
    }

    private void assertValidationFails(String expectedField) {
        MojoExecutionException e = assertThrows(MojoExecutionException.class, dependency::validate);
        assertEquals(DynamicDependency.CONFIGURATION_ERROR_MESSAGE, e.getMessage());
        assertTrue(e.getLongMessage().contains(expectedField), "Long message should mention " + expectedField + ": " + e.getLongMessage());
    }
}
