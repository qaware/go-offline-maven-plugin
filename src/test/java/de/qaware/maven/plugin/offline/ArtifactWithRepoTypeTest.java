package de.qaware.maven.plugin.offline;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ArtifactWithRepoTypeTest {

    private static final DefaultArtifact ARTIFACT = new DefaultArtifact("org.example:example:1.0");

    @Test
    void equalArtifactAndRepositoryTypeAreEqual() {
        ArtifactWithRepoType a = new ArtifactWithRepoType(ARTIFACT, RepositoryType.MAIN);
        ArtifactWithRepoType b = new ArtifactWithRepoType(new DefaultArtifact("org.example:example:1.0"), RepositoryType.MAIN);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void sameArtifactInDifferentRepositoryTypesIsNotEqual() {
        ArtifactWithRepoType main = new ArtifactWithRepoType(ARTIFACT, RepositoryType.MAIN);
        ArtifactWithRepoType plugin = new ArtifactWithRepoType(ARTIFACT, RepositoryType.PLUGIN);

        assertNotEquals(main, plugin);

        // the mojo relies on this to download an artifact once per repository context
        Set<ArtifactWithRepoType> set = new HashSet<>();
        set.add(main);
        set.add(plugin);
        set.add(new ArtifactWithRepoType(ARTIFACT, RepositoryType.MAIN));
        assertEquals(2, set.size());
    }

    @Test
    void differentArtifactsAreNotEqual() {
        assertNotEquals(new ArtifactWithRepoType(ARTIFACT, RepositoryType.MAIN),
                new ArtifactWithRepoType(new DefaultArtifact("org.example:example:2.0"), RepositoryType.MAIN));
    }

    @Test
    void repositoryTypesHaveDistinctRequestContexts() {
        assertEquals("project", RepositoryType.MAIN.getRequestContext());
        assertEquals("plugin", RepositoryType.PLUGIN.getRequestContext());
    }
}
