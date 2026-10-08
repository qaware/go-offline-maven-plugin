package de.qaware.maven.plugin.offline;

import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ReactorArtifactTest {

    @Test
    void mavenAndAetherArtifactsWithSameCoordinatesAreEqual() {
        ReactorArtifact fromMaven = new ReactorArtifact(mavenArtifact("org.example", "example", "1.0", "jar", null));
        ReactorArtifact fromAether = new ReactorArtifact(new DefaultArtifact("org.example", "example", "jar", "1.0"));

        assertEquals(fromMaven, fromAether);
        assertEquals(fromMaven.hashCode(), fromAether.hashCode());
    }

    @Test
    void typeAndClassifierAreIgnored() {
        ReactorArtifact jar = new ReactorArtifact(new DefaultArtifact("org.example", "example", "jar", "1.0"));
        ReactorArtifact pom = new ReactorArtifact(new DefaultArtifact("org.example", "example", "pom", "1.0"));
        ReactorArtifact sources = new ReactorArtifact(new DefaultArtifact("org.example", "example", "sources", "jar", "1.0"));
        ReactorArtifact mavenTestJar = new ReactorArtifact(mavenArtifact("org.example", "example", "1.0", "test-jar", "tests"));

        assertEquals(jar, pom);
        assertEquals(jar, sources);
        assertEquals(jar, mavenTestJar);
    }

    @Test
    void timestampedSnapshotMatchesBaseVersion() {
        ReactorArtifact reactor = new ReactorArtifact(mavenArtifact("org.example", "example", "1.0-SNAPSHOT", "jar", null));
        ReactorArtifact timestamped = new ReactorArtifact(new DefaultArtifact("org.example", "example", "jar", "1.0-20240101.120000-1"));

        assertEquals(reactor, timestamped);
    }

    @Test
    void differentCoordinatesAreNotEqual() {
        ReactorArtifact base = new ReactorArtifact(new DefaultArtifact("org.example", "example", "jar", "1.0"));

        assertNotEquals(base, new ReactorArtifact(new DefaultArtifact("org.other", "example", "jar", "1.0")));
        assertNotEquals(base, new ReactorArtifact(new DefaultArtifact("org.example", "other", "jar", "1.0")));
        assertNotEquals(base, new ReactorArtifact(new DefaultArtifact("org.example", "example", "jar", "2.0")));
        assertNotEquals(base, null);
        assertNotEquals(base, "org.example:example:1.0");
    }

    static org.apache.maven.artifact.Artifact mavenArtifact(String groupId, String artifactId, String version, String type, String classifier) {
        return new org.apache.maven.artifact.DefaultArtifact(groupId, artifactId, version, null, type, classifier, new DefaultArtifactHandler(type));
    }
}
