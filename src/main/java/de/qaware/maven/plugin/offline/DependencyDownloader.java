package de.qaware.maven.plugin.offline;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Downloads artifacts for project dependencies and plugins. This class maintains two separate sessions with separate caches
 * for project and plugin dependencies so all artifacts are resolved for all remote repository contexts.
 * <p>
 * The downloader must be initialized by calling {@link #init(ProjectBuildingRequest, List, Log)} before any of its other methods my be used.
 * <p>
 * This class only works for maven versions &gt;=3.1
 *
 * @author Andreas Janning andreas.janning@qaware.de
 */
@Component(role = DependencyDownloader.class, hint = "default")
public class DependencyDownloader {

    /**
     * Artifact type for maven plugins.
     */
    private static final String MAVEN_PLUGIN_ARTIFACT_TYPE = "maven-plugin";

    /**
     * Aether repository system
     */
    @Requirement
    private RepositorySystem repositorySystem;

    /**
     * Maven artifact handler manager
     */
    @Requirement
    private ArtifactHandlerManager artifactHandlerManager;

    private DefaultRepositorySystemSession remoteSession;
    private DefaultRepositorySystemSession pluginSession;
    private List<RemoteRepository> remoteRepositories;
    private List<RemoteRepository> pluginRepositories;
    private ArtifactTypeRegistry typeRegistry;
    private Log log;
    private List<Exception> errors;

    private boolean downloadSources = false;
    private boolean downloadJavadoc = false;
    private Set<Artifact> reactorArtifacts;

    /**
     * Initialize the DependencyDownloader
     *
     * @param buildingRequest a buildingRequest containing the maven session and Repositories to be used to download artifacts
     * @param reactorProjects the reactorProjects of the current build used to exclude reactor artifacts from the dependency download.
     * @param logger          used to log infos and warnings.
     */
    public void init(ProjectBuildingRequest buildingRequest, List<MavenProject> reactorProjects, Log logger) {
        this.log = logger;
        typeRegistry = RepositoryUtils.newArtifactTypeRegistry(artifactHandlerManager);
        remoteRepositories = RepositoryUtils.toRepos(buildingRequest.getRemoteRepositories());
        pluginRepositories = RepositoryUtils.toRepos(buildingRequest.getPluginArtifactRepositories());
        remoteSession = new DefaultRepositorySystemSession(buildingRequest.getRepositorySession());

        DependencySelector wagonExcluder = null;
        try {
            Class<?> wagonExcluderClass = Class.forName("org.apache.maven.plugin.internal.WagonExcluder");
            Constructor<?> wagonExcluderConstructor = wagonExcluderClass.getDeclaredConstructor();
            wagonExcluderConstructor.setAccessible(true);
            wagonExcluder = (DependencySelector) wagonExcluderConstructor.newInstance();
        } catch (ReflectiveOperationException e) {
            log.warn("Could not initialize wagonExcluder, might not be able to download plugin dependencies correctly", e);
        }

        reactorArtifacts = computeReactorArtifacts(reactorProjects);
        DependencySelector selector = new AndDependencySelector(new ScopeDependencySelector("system", "test", "provided"), new OptionalDependencySelector());
        remoteSession.setDependencySelector(selector);
        remoteSession.setIgnoreArtifactDescriptorRepositories(true);

        pluginSession = new DefaultRepositorySystemSession(remoteSession);
        remoteSession.setCache(new DefaultRepositoryCache());
        pluginSession.setCache(new DefaultRepositoryCache());
        if (wagonExcluder != null) {
            pluginSession.setDependencySelector(new AndDependencySelector(new ScopeDependencySelector("system", "test", "provided"), new OptionalDependencySelector(), wagonExcluder));
        }
        this.errors = new ArrayList<>();
    }

    public void enableDownloadSources() {
        this.downloadSources = true;
    }


    public void enableDownloadJavadoc() {
        this.downloadJavadoc = true;
    }


    public void downloadArtifacts(Collection<ArtifactWithRepoType> artifacts) {
        List<ArtifactRequest> mainRequests = new ArrayList<>(artifacts.size());
        List<ArtifactRequest> pluginRequests = new ArrayList<>(artifacts.size());
        for (ArtifactWithRepoType artifactWithRepoType : artifacts) {
            Artifact artifact = artifactWithRepoType.getArtifact();
            RepositoryType context = artifactWithRepoType.getRepositoryType();

            ArtifactRequest artifactRequest = new ArtifactRequest();
            artifactRequest.setArtifact(artifact);
            artifactRequest.setRepositories(context == RepositoryType.MAIN ? remoteRepositories : pluginRepositories);
            if (context == RepositoryType.MAIN) {
                artifactRequest.setRequestContext(context.getRequestContext());
                mainRequests.add(artifactRequest);

            } else {
                artifactRequest.setRequestContext(context.getRequestContext());
                pluginRequests.add(artifactRequest);
            }
            if (context == RepositoryType.MAIN && "jar".equals(artifact.getExtension())) {
                if (downloadSources) {
                    Artifact sourceArtifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "sources", artifact.getExtension(), artifact.getVersion());
                    mainRequests.add(new ArtifactRequest(sourceArtifact, remoteRepositories, context.getRequestContext()));
                }
                if (downloadJavadoc) {
                    Artifact javadocArtifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "javadoc", artifact.getExtension(), artifact.getVersion());
                    mainRequests.add(new ArtifactRequest(javadocArtifact, remoteRepositories, context.getRequestContext()));
                }
            }
        }
        try {
            repositorySystem.resolveArtifacts(remoteSession, mainRequests);
            repositorySystem.resolveArtifacts(pluginSession, pluginRequests);
        } catch (ArtifactResolutionException | RuntimeException e) {
            log.error("Error downloading dependencies for project");
            handleRepositoryException(e);
        }
    }


    /**
     * Download all dependencies of a maven project including transitive dependencies.
     * Dependencies that refer to an artifact in the current reactor build are ignored.
     * Transitive dependencies that are marked as optional are ignored
     * Transitive dependencies with the scopes "test", "system" and "provided" are ignored.
     *
     * @param project the project to download the dependencies for.
     */
    public Set<ArtifactWithRepoType> resolveDependencies(MavenProject project) {
        Artifact projectArtifact = RepositoryUtils.toArtifact(project.getArtifact());
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRepositories(remoteRepositories);
        collectRequest.setRootArtifact(projectArtifact);
        collectRequest.setRequestContext(RepositoryType.MAIN.getRequestContext());


        List<Dependency> aetherDependencies = new ArrayList<>();
        for (org.apache.maven.model.Dependency d : project.getDependencies()) {
            Dependency dependency = RepositoryUtils.toDependency(d, typeRegistry);
            aetherDependencies.add(dependency);
        }

        collectRequest.setDependencies(aetherDependencies);

        List<Dependency> aetherDepManagement = new ArrayList<>();
        DependencyManagement dependencyManagement = project.getDependencyManagement();
        if (dependencyManagement != null) {
            for (org.apache.maven.model.Dependency d : dependencyManagement.getDependencies()) {
                Dependency dependency = RepositoryUtils.toDependency(d, typeRegistry);
                aetherDepManagement.add(dependency);
            }
        }
        collectRequest.setManagedDependencies(aetherDepManagement);

        try {
            CollectResult collectResult = repositorySystem.collectDependencies(remoteSession, collectRequest);
            return getArtifactsFromCollectResult(collectResult, RepositoryType.MAIN);
        } catch (RepositoryException | RuntimeException e) {
            log.error("Error resolving dependencies for project " + project.getGroupId() + ":" + project.getArtifactId());
            handleRepositoryException(e);
        }
        return Collections.emptySet();
    }

    private Set<ArtifactWithRepoType> getArtifactsFromCollectResult(CollectResult collectResult, RepositoryType context) {
        CollectAllDependenciesVisitor visitor = new CollectAllDependenciesVisitor();
        collectResult.getRoot().accept(visitor);
        Set<Artifact> visitorArtifacts = visitor.getArtifacts();
        Set<ArtifactWithRepoType> artifacts = new HashSet<>();
        for (Artifact visitorArtifact : visitorArtifacts) {
            if (!isReactorArtifact(visitorArtifact)) {
                artifacts.add(new ArtifactWithRepoType(visitorArtifact, context));
            }
        }
        Artifact rootArtifact = collectResult.getRoot().getArtifact();
        if (!isReactorArtifact(rootArtifact)) {
            artifacts.add(new ArtifactWithRepoType(rootArtifact, context));
        }
        return artifacts;
    }

    /**
     * Download a plugin, all of its transitive dependencies and dependencies declared on the plugin declaration.
     * <p>
     * Dependencies and plugin artifacts that refer to an artifact in the current reactor build are ignored.
     * Transitive dependencies that are marked as optional are ignored
     * Transitive dependencies with the scopes "test", "system" and "provided" are ignored.
     *
     * @param plugin the plugin to download
     */
    public Set<ArtifactWithRepoType> resolvePlugin(Plugin plugin) {
        Artifact pluginArtifact = toArtifact(plugin);
        Dependency pluginDependency = new Dependency(pluginArtifact, null);
        CollectRequest collectRequest = new CollectRequest(pluginDependency, pluginRepositories);
        collectRequest.setRequestContext(RepositoryType.PLUGIN.getRequestContext());

        List<Dependency> pluginDependencies = new ArrayList<>();
        for (org.apache.maven.model.Dependency d : plugin.getDependencies()) {
            Dependency dependency = RepositoryUtils.toDependency(d, typeRegistry);
            pluginDependencies.add(dependency);
        }
        collectRequest.setDependencies(pluginDependencies);

        try {
            CollectResult collectResult = repositorySystem.collectDependencies(pluginSession, collectRequest);
            return getArtifactsFromCollectResult(collectResult, RepositoryType.PLUGIN);
        } catch (DependencyCollectionException | RuntimeException e) {
            log.error("Error resolving plugin " + plugin.getGroupId() + ":" + plugin.getArtifactId());
            handleRepositoryException(e);
        }
        return Collections.emptySet();
    }

    /**
     * Download a single dependency and all of its transitive dependencies that is needed by the build without appearing in any dependency tree
     * <p>
     * Dependencies and plugin artifacts that refer to an artifact in the current reactor build are ignored.
     * Transitive dependencies that are marked as optional are ignored
     * Transitive dependencies with the scopes "test", "system" and "provided" are ignored.
     *
     * @param dynamicDependency the dependency to download
     */
    public Set<ArtifactWithRepoType> resolveDynamicDependency(DynamicDependency dynamicDependency) {
        DefaultArtifact artifact = new DefaultArtifact(dynamicDependency.getGroupId(), dynamicDependency.getArtifactId(), dynamicDependency.getClassifier(), "jar", dynamicDependency.getVersion());

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(artifact, null));
        RepositoryType repositoryType = dynamicDependency.getRepositoryType();
        RepositorySystemSession session;
        switch (repositoryType) {
            case MAIN:
                session = remoteSession;
                collectRequest.setRepositories(remoteRepositories);
                collectRequest.setRequestContext(repositoryType.getRequestContext());
                break;
            case PLUGIN:
                session = pluginSession;
                collectRequest.setRepositories(pluginRepositories);
                collectRequest.setRequestContext(repositoryType.getRequestContext());
                break;
            default:
                throw new IllegalStateException("Unknown enum val " + repositoryType);

        }
        try {
            CollectResult collectResult = repositorySystem.collectDependencies(session, collectRequest);
            return getArtifactsFromCollectResult(collectResult, repositoryType);
        } catch (DependencyCollectionException | RuntimeException e) {
            log.error("Error resolving dynamic dependency" + dynamicDependency.getGroupId() + ":" + dynamicDependency.getArtifactId());
            handleRepositoryException(e);
        }
        return Collections.emptySet();
    }

    /**
     * @return a List of errors encountered during the downloading of artifacts since this class has been initialized.
     */
    public List<Exception> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    private Set<Artifact> computeReactorArtifacts(List<MavenProject> reactorProjects) {
        Set<Artifact> artifacts = new HashSet<>(reactorProjects.size());
        for (MavenProject p : reactorProjects) {
            artifacts.add(toArtifact(p.getArtifact()));
        }
        return artifacts;
    }

    private boolean isReactorArtifact(Artifact artifact) {
        return reactorArtifacts.contains(artifact);
    }

    private void handleRepositoryException(Exception e) {
        log.error(e.getMessage());
        log.debug(e);
        addToErrorList(e);
    }

    private synchronized void addToErrorList(Exception e) {
        errors.add(e);
    }

    private Artifact toArtifact(org.apache.maven.artifact.Artifact mavenArtifact) {
        ArtifactType artifactType = typeRegistry.get(mavenArtifact.getType());
        return new DefaultArtifact(mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(), artifactType.getClassifier(), artifactType.getExtension(), mavenArtifact.getVersion(),
                artifactType);
    }

    private Artifact toArtifact(Plugin plugin) {
        ArtifactType artifactType = typeRegistry.get(MAVEN_PLUGIN_ARTIFACT_TYPE);
        return new DefaultArtifact(plugin.getGroupId(), plugin.getArtifactId(), artifactType.getClassifier(), artifactType.getExtension(), plugin.getVersion(),
                artifactType);
    }

    private static class CollectAllDependenciesVisitor implements DependencyVisitor {

        private boolean root = true;
        private Set<Artifact> artifacts = new HashSet<>();

        @Override
        public boolean visitEnter(DependencyNode node) {
            if (root) {
                root = false;
                return true;
            }
            return artifacts.add(node.getArtifact());
        }

        @Override
        public boolean visitLeave(DependencyNode node) {
            return true;
        }

        public Set<Artifact> getArtifacts() {
            return artifacts;
        }
    }
}
