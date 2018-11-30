package de.qaware.maven.plugin.offline;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositoryException;

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


    public void execute() {
        dependencyDownloader.init(getBuildingRequest(), getReactorProjects(), getLog());
        if (downloadSources) {
            dependencyDownloader.enableDownloadSources();
        }
        if (downloadJavadoc) {
            dependencyDownloader.enableDownloadJavadoc();
        }

        Set<Plugin> allPlugins = new HashSet<>();
        for (MavenProject mavenProject : getReactorProjects()) {
            List<Plugin> buildPlugins = mavenProject.getBuildPlugins();
            allPlugins.addAll(buildPlugins);
        }

        for (Plugin plugin : allPlugins) {
            scheduleTask(new ResolvePluginJob(plugin));
        }
        for (MavenProject project : getReactorProjects()) {
            scheduleTask(new ResolveProjectDependenciesJob(project));
        }
        if (dynamicDependencies != null) {
            for (DynamicDependency dep : dynamicDependencies) {
                scheduleTask(new ResolveDynamicDependenciesJob(dep));
            }
        }
        waitForTasksToComplete();

        for (RepositoryException error : dependencyDownloader.getErrors()) {
            getLog().warn(error.getMessage());
        }
    }

    private class ResolvePluginJob implements Runnable {
        private final Plugin plugin;

        public ResolvePluginJob(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void run() {
            dependencyDownloader.resolvePlugin(plugin);
        }
    }

    private class ResolveProjectDependenciesJob implements Runnable {
        private final MavenProject project;

        public ResolveProjectDependenciesJob(MavenProject project) {
            this.project = project;
        }

        @Override
        public void run() {
            dependencyDownloader.resolveDependencies(project);
        }
    }

    private class ResolveDynamicDependenciesJob implements Runnable {
        private final DynamicDependency dep;

        public ResolveDynamicDependenciesJob(DynamicDependency dep) {
            this.dep = dep;
        }

        @Override
        public void run() {
            dependencyDownloader.resolveDynamicDependency(dep);
        }
    }
}
