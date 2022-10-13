package de.qaware.maven.plugin.offline;

import org.apache.maven.plugin.MojoExecutionException;

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

    /** The config error message */
    public static final String CONFIGURATION_ERROR_MESSAGE = "DynamicDependency configuration error";
    
    private String artifactId;
    private String groupId;
    private String version;
    private String classifier;
    private String type;
    private RepositoryType repositoryType;

    DynamicDependency() {
        // Noop
    }

    /**
     * Validate that all required parameters are set.
     *
     * @throws MojoExecutionException if any required parameter is not set
     */
    public void validate() throws MojoExecutionException {
        if (artifactId == null || artifactId.isEmpty()) {
            throw new MojoExecutionException(this, CONFIGURATION_ERROR_MESSAGE, "Invalid " + this + ": The artifactId must not empty");
        }
        if (groupId == null || groupId.isEmpty()) {
            throw new MojoExecutionException(this, CONFIGURATION_ERROR_MESSAGE, "Invalid " + this + ": The groupId must not empty");
        }
        if (version == null || version.isEmpty()) {
            throw new MojoExecutionException(this, CONFIGURATION_ERROR_MESSAGE, "Invalid " + this + ": The version must not empty");
        }
        if (repositoryType == null) {
            throw new MojoExecutionException(this, CONFIGURATION_ERROR_MESSAGE, "Invalid " + this + ": The repositoryType must be defined");
        }
    }

    /**
     * Returns the artifactId of the {@link DynamicDependency}
     * 
     * @return The artifactId of the {@link DynamicDependency}
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Set the artifactId
     * 
     * @param artifactId - Id to set
     */
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * Returns the groupId of the {@link DynamicDependency}
     * 
     * @return The groupId of the {@link DynamicDependency}
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Set the groupId
     * 
     * @param groupId - Id to set
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * Returns the version of the {@link DynamicDependency}
     * 
     * @return The version of the {@link DynamicDependency}
     */
    public String getVersion() {
        return version;
    }

    /**
     * Set the version
     * 
     * @param version - Version to set
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Either returns the user entered value or "jar" as a default if the user provided no value.
     * 
     * @return The type of the {@link DynamicDependency}.
     */
    public String getType() {
        if (type == null || type.isEmpty()) {
            return "jar";
        }
        return type;
    }

    /**
     * Set the type
     * 
     * @param type - Type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the classifier of the {@link DynamicDependency}. May be null.
     * 
     * @return The classifier of the {@link DynamicDependency}. May be null.
     */
    public String getClassifier() {
        return classifier;
    }

    /**
     * Set the classifier
     * 
     * @param classifier - classifier to set
     */
    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    /**
     * Returns from which type of remoteRepository this dependency must be downloaded
     * 
     * @return from which type of remoteRepository this dependency must be downloaded
     */
    public RepositoryType getRepositoryType() {
        return repositoryType;
    }

    /**
     * Set the type of remoteRepository from which the dependency should be downloaded
     * 
     * @param repositoryType - The repository type to set
     */
    public void setRepositoryType(RepositoryType repositoryType) {
        this.repositoryType = repositoryType;
    }

    @Override
    public String toString() {
        return "DynamicDependency{" +
                "artifactId='" + artifactId + '\'' +
                ", groupId='" + groupId + '\'' +
                ", version='" + version + '\'' +
                ", classifier='" + classifier + '\'' +
                ", repositoryType=" + repositoryType +
                '}';
    }
}
