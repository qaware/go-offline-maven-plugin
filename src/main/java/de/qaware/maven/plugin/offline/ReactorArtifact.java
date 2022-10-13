package de.qaware.maven.plugin.offline;


import java.util.Objects;

/**
 * Describes an artifact in the current build reactor. Used to compare artifacts in a set.
 * <p>
 * To determine if an artifact to download is part of the current reactor (and thus should not be downloaded from the internet)
 * we have to test if an artifact with the same groupId, artifactId and version is part of the build reactor.
 * <p>
 * Since a maven project can output multiple artifacts with different types and classifiers, we explicitly do not test
 * for those properties. This is not 100% correct, since it theoretically possible to output an artifact with the same
 * groupId:artifactId:version identifier and different type/classifier from different projects. But the information on
 * which additional artifacts are produced by a project are not available to the go-offline-maven plugin, since they are
 * added dynamically at build time. So we have to live with this fuzziness.
 *
 * @author andreas.janning
 */
public class ReactorArtifact {

    private final String groupId;
    private final String artifactId;
    private final String version;

    /**
     * Convert a maven artifact to a ReactorArtifact
     *
     * @param mavenArtifact the artifact to create a ReactorArtifact for.
     */
    public ReactorArtifact(org.apache.maven.artifact.Artifact mavenArtifact) {
        this(mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(), mavenArtifact.getBaseVersion());
    }

    /**
     * Convert a aether artifact to a ReactorArtifact
     *
     * @param aetherArtifact the artifact to create a ReactorArtifact for.
     */
    public ReactorArtifact(org.eclipse.aether.artifact.Artifact aetherArtifact) {
        this(aetherArtifact.getGroupId(), aetherArtifact.getArtifactId(), aetherArtifact.getBaseVersion());
    }

    private ReactorArtifact(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReactorArtifact that = (ReactorArtifact) o;
        return groupId.equals(that.groupId) &&
                artifactId.equals(that.artifactId) &&
                version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ReactorArtifact{");
        sb.append("groupId='").append(groupId).append('\'');
        sb.append(", artifactId='").append(artifactId).append('\'');
        sb.append(", version='").append(version).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
