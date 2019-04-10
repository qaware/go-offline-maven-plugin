package de.qaware.maven.plugin.offline;

import org.eclipse.aether.artifact.Artifact;

import java.util.Objects;

public class ArtifactWithRepoType {

    private final Artifact artifact;
    private final RepositoryType repositoryType;

    public ArtifactWithRepoType(Artifact artifact, RepositoryType repositoryType) {
        this.artifact = artifact;
        this.repositoryType = repositoryType;
    }

    public Artifact getArtifact() {
        return artifact;
    }

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
