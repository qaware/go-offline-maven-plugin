package de.qaware.maven.plugin.offline;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;

import java.util.Collections;
import java.util.List;

/**
 * Base class for mojos in the in the go-offline maven plugin.
 * <p>
 * Provides access to parameters and injected configuration needed by the plugin.
 * <p>
 * Also provides functionality to schedule tasks for asynchronous completion.
 *
 * @author Andreas Janning andreas.janning@qaware.de
 */
public abstract class AbstractGoOfflineMojo extends AbstractMojo {

    /**
     * Remote repositories used to download dependencies.
     */
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
    private List<ArtifactRepository> remoteRepositories;

    /**
     * Remote repositories used to download plugins.
     */
    @Parameter(defaultValue = "${project.pluginArtifactRepositories}", readonly = true, required = true)
    private List<ArtifactRepository> remotePluginRepositories;

    /**
     * Contains the full list of projects in the reactor.
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true)
    private List<MavenProject> reactorProjects;

    /**
     * The Maven session.
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;


    private ProjectBuildingRequest buildingRequest;


    /**
     * @return a building request initialized with the data of the current maven session
     */
    protected ProjectBuildingRequest getBuildingRequest() {
        if (buildingRequest == null) {
            buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setRemoteRepositories(remoteRepositories);
            buildingRequest.setPluginArtifactRepositories(remotePluginRepositories);
            buildingRequest.setRepositoryMerging(ProjectBuildingRequest.RepositoryMerging.REQUEST_DOMINANT);
            buildingRequest.setResolveDependencies(true);
        }
        return buildingRequest;
    }

    /**
     * @return the full list of projects in the current build-reactor
     */
    protected List<MavenProject> getReactorProjects() {
        return Collections.unmodifiableList(reactorProjects);
    }
}
