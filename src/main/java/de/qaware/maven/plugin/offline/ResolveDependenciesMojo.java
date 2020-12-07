package de.qaware.maven.plugin.offline;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mojo used to download all dependencies of a project or reactor to the local repository.
 * <p>
 * This includes:
 * <ul>
 * <li>Direct and transitive dependencies declared in POMs</li>
 * <li>All plugins used for the build and their transitive dependencies</li>
 * <li>Dependencies of plugins declared in POMs</li>
 * <li>DynamicDependencies configured in the go-offline-maven-plugin configuration</li>
 * </ul>
 *
 * @author Andreas Janning andreas.janning@qaware.de
 */
@Mojo(name = "resolve-dependencies", threadSafe = true, requiresOnline = true, aggregator = true)
public class ResolveDependenciesMojo extends AbstractGoOfflineMojo {

    @Component
    private DependencyDownloader dependencyDownloader;

    @Parameter
    private List<DynamicDependency> dynamicDependencies;

    @Parameter(defaultValue = "false", property = "downloadSources")
    private boolean downloadSources;

    @Parameter(defaultValue = "false", property = "downloadJavadoc")
    private boolean downloadJavadoc;

    @Parameter(defaultValue = "false", property = "failOnErrors")
    private boolean failOnErrors;

    public void execute() throws MojoExecutionException {
        validateConfiguration();
        dependencyDownloader.init(getBuildingRequest(), getReactorProjects(), getLog());
        if (downloadSources) {
            dependencyDownloader.enableDownloadSources();
        }
        if (downloadJavadoc) {
            dependencyDownloader.enableDownloadJavadoc();
        }

        List<Plugin> allPlugins = new ArrayList<>();
        for (MavenProject mavenProject : getReactorProjects()) {
            List<Plugin> buildPlugins = mavenProject.getBuildPlugins();
            allPlugins.addAll(buildPlugins);
        }

        Set<ArtifactWithRepoType> artifactsToDownload = new HashSet<>();


        for (Plugin plugin : allPlugins) {
            artifactsToDownload.addAll(dependencyDownloader.resolvePlugin(plugin));
        }
        for (MavenProject project : getReactorProjects()) {
            artifactsToDownload.addAll(dependencyDownloader.resolveDependencies(project));
        }
        if (dynamicDependencies != null) {
            for (DynamicDependency dep : dynamicDependencies) {
                artifactsToDownload.addAll(dependencyDownloader.resolveDynamicDependency(dep));
            }
        }

        dependencyDownloader.downloadArtifacts(artifactsToDownload);


        List<Exception> errors = dependencyDownloader.getErrors();
        for (Exception error : errors) {
            getLog().warn(error.getMessage());
        }

        if (failOnErrors && !errors.isEmpty()) {
            throw new MojoExecutionException("Unable to download dependencies, consult the errors and warnings printed above.");
        }
    }

    private void validateConfiguration() throws MojoExecutionException {
        if (dynamicDependencies != null) {
            for (DynamicDependency dynamicDependency : dynamicDependencies) {
                dynamicDependency.validate();
            }
        }
    }

}
