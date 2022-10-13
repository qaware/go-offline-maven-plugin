package de.qaware.maven.plugin.offline;

import org.eclipse.aether.artifact.Artifact;

import java.util.Objects;

/**
 * Artifact with associated repository type for artifact resolution.
 *
 * @author Andreas Janning
 */
public class ArtifactWithRepoType {

    private final Artifact artifact;
    private final RepositoryType repositoryType;

    /**
     * Create a new ArtifactWithRepoType.
     * 
     * @param artifact - The artifact to add a repository type to
     * @param repositoryType - The repository type to associate with the artifact
     */
    public ArtifactWithRepoType(Artifact artifact, RepositoryType repositoryType) {
        this.artifact = artifact;
        this.repositoryType = repositoryType;
    }

    /**
     * Get the artifact associated with this ArtifactWithRepoType
     * @return the artifact associated with this ArtifactWithRepoType
     */
    public Artifact getArtifact() {
        return artifact;
    }

    /**
     * Get the repository type associated with this ArtifactWithRepoType
     * @return the repository type associated with this ArtifactWithRepoType
     */
    public RepositoryType getRepositoryType() {
        return repositoryType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtifactWithRepoType that = (ArtifactWithRepoType) o;
        return artifact.equals(that.artifact) &&
                repositoryType == that.repositoryType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifact, repositoryType);
    }

    @Override
    public String toString() {
        return "ArtifactWithRepoType{" +
                "artifact=" + artifact +
                ", repositoryType=" + repositoryType +
                '}';
    }
}
