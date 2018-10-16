package de.qaware.maven.plugin.offline;

/**
 * Configuration used to declare extra dependencies for the {@link ResolveDependenciesMojo}.
 * <p>
 * Users can declare extra dependencies that do not appear in any dependency tree of the project but are still needed for the build to work.
 * <p>
 * The most common examples are artifacts that are dynamically loaded by plugins during build runtime
 *
 * @author Andreas Janning andreas.janning@qaware.de
 */
public class DynamicDependency {

    private String artifactId;
    private String groupId;
    private String version;
    private RepositoryType repositoryType;

    /**
     * @return The artifactId of the {@link DynamicDependency}
     */
    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * @return The groupId of the {@link DynamicDependency}
     */
    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * @return The version of the {@link DynamicDependency}
     */
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @return from which type of remoteRepository this dependency must be downloaded
     */
    public RepositoryType getRepositoryType() {
        return repositoryType;
    }

    public void setRepositoryType(RepositoryType repositoryType) {
        this.repositoryType = repositoryType;
    }
}
