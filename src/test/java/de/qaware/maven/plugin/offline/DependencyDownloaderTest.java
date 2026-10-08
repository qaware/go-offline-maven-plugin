package de.qaware.maven.plugin.offline;

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static de.qaware.maven.plugin.offline.TestSupport.dependency;
import static de.qaware.maven.plugin.offline.TestSupport.project;
import static de.qaware.maven.plugin.offline.TestSupport.repository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyDownloaderTest {

    private static final String MAIN_REPO = "central";
    private static final String PLUGIN_REPO = "plugins";

    @Mock
    private RepositorySystem repositorySystem;

    @Mock
    private Log log;

    private DependencyDownloader downloader;
    private MavenProject reactorProject;
    private MavenProject reactorSibling;

    @BeforeEach
    void setUp() {
        downloader = new DependencyDownloader();
        TestSupport.setField(downloader, "repositorySystem", repositorySystem);
        TestSupport.setField(downloader, "artifactHandlerManager", new TestSupport.StubArtifactHandlerManager());

        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest();
        buildingRequest.setRepositorySession(new DefaultRepositorySystemSession());
        buildingRequest.setRemoteRepositories(Collections.singletonList(repository(MAIN_REPO)));
        buildingRequest.setPluginArtifactRepositories(Collections.singletonList(repository(PLUGIN_REPO)));

        reactorProject = project("org.example", "module-a", "1.0-SNAPSHOT");
        reactorSibling = project("org.example", "module-b", "1.0-SNAPSHOT");
        downloader.init(buildingRequest, Arrays.asList(reactorProject, reactorSibling), log);
    }

    // --- resolveDependencies ---

    @Test
    void resolveDependenciesReturnsTransitiveDependenciesWithoutReactorArtifacts() throws Exception {
        reactorProject.getDependencies().add(dependency("org.lib", "lib-a", "1.0"));
        reactorProject.getDependencies().add(dependency("org.example", "module-b", "1.0-SNAPSHOT"));
        DependencyManagement dependencyManagement = new DependencyManagement();
        dependencyManagement.addDependency(dependency("org.lib", "lib-c", "3.0"));
        reactorProject.getModel().setDependencyManagement(dependencyManagement);

        DefaultDependencyNode libA = node("org.lib:lib-a:1.0", node("org.lib:lib-c:3.0"));
        DefaultDependencyNode moduleB = node("org.example:module-b:1.0-SNAPSHOT", node("org.lib:lib-b:2.0"));
        DefaultDependencyNode root = node("org.example:module-a:1.0-SNAPSHOT", libA, moduleB);
        when(repositorySystem.collectDependencies(any(), any())).thenReturn(collectResult(root));

        Set<ArtifactWithRepoType> result = downloader.resolveDependencies(reactorProject);

        assertEquals(coordinates("org.lib:lib-a:jar:1.0", "org.lib:lib-b:jar:2.0", "org.lib:lib-c:jar:3.0"), coordinatesOf(result));
        assertAllOfType(result, RepositoryType.MAIN);

        CollectRequest request = captureCollectRequest();
        assertEquals(Collections.singletonList(MAIN_REPO), repositoryIds(request.getRepositories()));
        assertEquals(RepositoryType.MAIN.getRequestContext(), request.getRequestContext());
        assertEquals("org.example:module-a:jar:1.0-SNAPSHOT", request.getRootArtifact().toString());
        assertEquals(Arrays.asList("org.lib:lib-a:jar:1.0", "org.example:module-b:jar:1.0-SNAPSHOT"), artifactsOf(request.getDependencies()));
        assertEquals(Collections.singletonList("org.lib:lib-c:jar:3.0"), artifactsOf(request.getManagedDependencies()));
    }

    @Test
    void resolveDependenciesIncludesRootIfNotInReactor() throws Exception {
        MavenProject external = project("org.other", "standalone", "1.0");
        when(repositorySystem.collectDependencies(any(), any())).thenReturn(collectResult(node("org.other:standalone:1.0")));

        Set<ArtifactWithRepoType> result = downloader.resolveDependencies(external);

        assertEquals(coordinates("org.other:standalone:jar:1.0"), coordinatesOf(result));
    }

    @Test
    void resolveDependenciesRecordsCollectionErrors() throws Exception {
        DependencyCollectionException failure = new DependencyCollectionException(new CollectResult(new CollectRequest()), "collection failed");
        when(repositorySystem.collectDependencies(any(), any())).thenThrow(failure);

        Set<ArtifactWithRepoType> result = downloader.resolveDependencies(reactorProject);

        assertTrue(result.isEmpty());
        assertEquals(Collections.singletonList(failure), downloader.getErrors());
        verify(log).error("Error resolving dependencies for project org.example:module-a");
        verify(log).error("collection failed");
    }

    @Test
    void resolveDependenciesRecordsRuntimeExceptions() throws Exception {
        IllegalStateException failure = new IllegalStateException("unexpected");
        when(repositorySystem.collectDependencies(any(), any())).thenThrow(failure);

        assertTrue(downloader.resolveDependencies(reactorProject).isEmpty());
        assertEquals(Collections.singletonList(failure), downloader.getErrors());
    }

    // --- resolvePlugin ---

    @Test
    void resolvePluginReturnsPluginAndItsDependencies() throws Exception {
        Plugin plugin = plugin("org.apache.maven.plugins", "maven-surefire-plugin", "3.0.0");
        plugin.addDependency(dependency("org.extra", "extra", "1.0"));

        DefaultDependencyNode root = node("org.apache.maven.plugins:maven-surefire-plugin:3.0.0",
                node("org.apache.maven.surefire:surefire-api:3.0.0"), node("org.extra:extra:1.0"));
        when(repositorySystem.collectDependencies(any(), any())).thenReturn(collectResult(root));

        Set<ArtifactWithRepoType> result = downloader.resolvePlugin(plugin);

        assertEquals(coordinates("org.apache.maven.plugins:maven-surefire-plugin:jar:3.0.0",
                "org.apache.maven.surefire:surefire-api:jar:3.0.0", "org.extra:extra:jar:1.0"), coordinatesOf(result));
        assertAllOfType(result, RepositoryType.PLUGIN);

        CollectRequest request = captureCollectRequest();
        assertEquals(Collections.singletonList(PLUGIN_REPO), repositoryIds(request.getRepositories()));
        assertEquals(RepositoryType.PLUGIN.getRequestContext(), request.getRequestContext());
        Artifact rootArtifact = request.getRoot().getArtifact();
        assertEquals("org.apache.maven.plugins:maven-surefire-plugin:jar:3.0.0", rootArtifact.toString());
        assertEquals("maven-plugin", rootArtifact.getProperty("type", null));
        assertEquals(Collections.singletonList("org.extra:extra:jar:1.0"), artifactsOf(request.getDependencies()));
    }

    @Test
    void resolvePluginExcludesReactorPlugins() throws Exception {
        Plugin plugin = plugin("org.example", "module-b", "1.0-SNAPSHOT");
        when(repositorySystem.collectDependencies(any(), any()))
                .thenReturn(collectResult(node("org.example:module-b:1.0-SNAPSHOT", node("org.lib:lib-a:1.0"))));

        Set<ArtifactWithRepoType> result = downloader.resolvePlugin(plugin);

        assertEquals(coordinates("org.lib:lib-a:jar:1.0"), coordinatesOf(result));
    }

    @Test
    void resolvePluginRecordsCollectionErrors() throws Exception {
        DependencyCollectionException failure = new DependencyCollectionException(new CollectResult(new CollectRequest()), "plugin failed");
        when(repositorySystem.collectDependencies(any(), any())).thenThrow(failure);

        assertTrue(downloader.resolvePlugin(plugin("org.example", "some-plugin", "1.0")).isEmpty());
        assertEquals(Collections.singletonList(failure), downloader.getErrors());
        verify(log).error("Error resolving plugin org.example:some-plugin");
    }

    @Test
    void projectsAndPluginsAreResolvedInSeparateSessions() throws Exception {
        when(repositorySystem.collectDependencies(any(), any()))
                .thenReturn(collectResult(node("org.example:module-a:1.0-SNAPSHOT")))
                .thenReturn(collectResult(node("org.example:some-plugin:1.0")));

        downloader.resolveDependencies(reactorProject);
        downloader.resolvePlugin(plugin("org.example", "some-plugin", "1.0"));

        ArgumentCaptor<RepositorySystemSession> sessions = ArgumentCaptor.forClass(RepositorySystemSession.class);
        verify(repositorySystem, times(2)).collectDependencies(sessions.capture(), any());
        RepositorySystemSession projectSession = sessions.getAllValues().get(0);
        RepositorySystemSession pluginSession = sessions.getAllValues().get(1);
        assertNotSame(projectSession, pluginSession);
        assertNotSame(projectSession.getCache(), pluginSession.getCache());
    }

    // --- resolveDynamicDependency ---

    @Test
    void resolveDynamicDependencyFromMainRepository() throws Exception {
        DynamicDependency dynamicDependency = dynamicDependency("org.flywaydb", "flyway-commandline", "4.0.3", RepositoryType.MAIN);
        dynamicDependency.setType("zip");
        dynamicDependency.setClassifier("linux");
        when(repositorySystem.collectDependencies(any(), any()))
                .thenReturn(collectResult(node("org.flywaydb:flyway-commandline:zip:linux:4.0.3", node("org.lib:lib-a:1.0"))));

        Set<ArtifactWithRepoType> result = downloader.resolveDynamicDependency(dynamicDependency);

        assertEquals(coordinates("org.flywaydb:flyway-commandline:zip:linux:4.0.3", "org.lib:lib-a:jar:1.0"), coordinatesOf(result));
        assertAllOfType(result, RepositoryType.MAIN);

        CollectRequest request = captureCollectRequest();
        assertEquals("org.flywaydb:flyway-commandline:zip:linux:4.0.3", request.getRoot().getArtifact().toString());
        assertEquals(Collections.singletonList(MAIN_REPO), repositoryIds(request.getRepositories()));
        assertEquals(RepositoryType.MAIN.getRequestContext(), request.getRequestContext());
    }

    @Test
    void resolveDynamicDependencyFromPluginRepositoryUsesTypeHandler() throws Exception {
        DynamicDependency dynamicDependency = dynamicDependency("org.apache.maven.surefire", "surefire-junit4", "3.0.0", RepositoryType.PLUGIN);
        dynamicDependency.setType("test-jar");
        when(repositorySystem.collectDependencies(any(), any()))
                .thenReturn(collectResult(node("org.apache.maven.surefire:surefire-junit4:jar:tests:3.0.0")));

        Set<ArtifactWithRepoType> result = downloader.resolveDynamicDependency(dynamicDependency);

        assertAllOfType(result, RepositoryType.PLUGIN);
        CollectRequest request = captureCollectRequest();
        Artifact root = request.getRoot().getArtifact();
        // the extension and classifier are derived from the artifact handler of the type
        assertEquals("jar", root.getExtension());
        assertEquals("tests", root.getClassifier());
        assertEquals(Collections.singletonList(PLUGIN_REPO), repositoryIds(request.getRepositories()));
        assertEquals(RepositoryType.PLUGIN.getRequestContext(), request.getRequestContext());
    }

    @Test
    void resolveDynamicDependencyDefaultsToJar() throws Exception {
        when(repositorySystem.collectDependencies(any(), any()))
                .thenReturn(collectResult(node("org.lib:lib-a:1.0")));

        downloader.resolveDynamicDependency(dynamicDependency("org.lib", "lib-a", "1.0", RepositoryType.MAIN));

        assertEquals("org.lib:lib-a:jar:1.0", captureCollectRequest().getRoot().getArtifact().toString());
    }

    @Test
    void resolveDynamicDependencyRecordsCollectionErrors() throws Exception {
        DependencyCollectionException failure = new DependencyCollectionException(new CollectResult(new CollectRequest()), "dynamic failed");
        when(repositorySystem.collectDependencies(any(), any())).thenThrow(failure);

        assertTrue(downloader.resolveDynamicDependency(dynamicDependency("org.lib", "lib-a", "1.0", RepositoryType.MAIN)).isEmpty());
        assertEquals(Collections.singletonList(failure), downloader.getErrors());
    }

    // --- downloadArtifacts ---

    @Test
    void downloadArtifactsResolvesMainAndPluginArtifactsWithTheirRepositories() throws Exception {
        downloader.downloadArtifacts(Arrays.asList(
                new ArtifactWithRepoType(new DefaultArtifact("org.lib:lib-a:1.0"), RepositoryType.MAIN),
                new ArtifactWithRepoType(new DefaultArtifact("org.example:some-plugin:1.0"), RepositoryType.PLUGIN)));

        List<ArtifactRequest> mainRequests = captureResolveRequests(0);
        List<ArtifactRequest> pluginRequests = captureResolveRequests(1);

        assertEquals(1, mainRequests.size());
        assertEquals("org.lib:lib-a:jar:1.0", mainRequests.get(0).getArtifact().toString());
        assertEquals(Collections.singletonList(MAIN_REPO), repositoryIds(mainRequests.get(0).getRepositories()));
        assertEquals(RepositoryType.MAIN.getRequestContext(), mainRequests.get(0).getRequestContext());

        assertEquals(1, pluginRequests.size());
        assertEquals("org.example:some-plugin:jar:1.0", pluginRequests.get(0).getArtifact().toString());
        assertEquals(Collections.singletonList(PLUGIN_REPO), repositoryIds(pluginRequests.get(0).getRepositories()));
        assertEquals(RepositoryType.PLUGIN.getRequestContext(), pluginRequests.get(0).getRequestContext());
    }

    @Test
    void downloadArtifactsDoesNotRequestSourcesOrJavadocByDefault() throws Exception {
        downloader.downloadArtifacts(Collections.singletonList(
                new ArtifactWithRepoType(new DefaultArtifact("org.lib:lib-a:1.0"), RepositoryType.MAIN)));

        assertEquals(Collections.singletonList("org.lib:lib-a:jar:1.0"), requestedArtifacts(captureResolveRequests(0)));
    }

    @Test
    void downloadArtifactsRequestsSourcesAndJavadocForMainJarsOnly() throws Exception {
        downloader.enableDownloadSources();
        downloader.enableDownloadJavadoc();

        downloader.downloadArtifacts(Arrays.asList(
                new ArtifactWithRepoType(new DefaultArtifact("org.lib:lib-a:1.0"), RepositoryType.MAIN),
                new ArtifactWithRepoType(new DefaultArtifact("org.lib:lib-parent:pom:1.0"), RepositoryType.MAIN),
                new ArtifactWithRepoType(new DefaultArtifact("org.example:some-plugin:1.0"), RepositoryType.PLUGIN)));

        List<ArtifactRequest> mainRequests = captureResolveRequests(0);
        assertEquals(coordinates("org.lib:lib-a:jar:1.0", "org.lib:lib-a:jar:sources:1.0", "org.lib:lib-a:jar:javadoc:1.0", "org.lib:lib-parent:pom:1.0"),
                new HashSet<>(requestedArtifacts(mainRequests)));
        for (ArtifactRequest request : mainRequests) {
            assertEquals(Collections.singletonList(MAIN_REPO), repositoryIds(request.getRepositories()));
            assertEquals(RepositoryType.MAIN.getRequestContext(), request.getRequestContext());
        }
        assertEquals(Collections.singletonList("org.example:some-plugin:jar:1.0"), requestedArtifacts(captureResolveRequests(1)));
    }

    @Test
    void downloadArtifactsRequestsOnlySourcesWhenJavadocIsDisabled() throws Exception {
        downloader.enableDownloadSources();

        downloader.downloadArtifacts(Collections.singletonList(
                new ArtifactWithRepoType(new DefaultArtifact("org.lib:lib-a:1.0"), RepositoryType.MAIN)));

        assertEquals(coordinates("org.lib:lib-a:jar:1.0", "org.lib:lib-a:jar:sources:1.0"), new HashSet<>(requestedArtifacts(captureResolveRequests(0))));
    }

    @Test
    void downloadArtifactsContinuesWithPluginsWhenMainDownloadFails() throws Exception {
        ArtifactResolutionException mainFailure = new ArtifactResolutionException(Collections.emptyList(), "main failed", null);
        ArtifactResolutionException pluginFailure = new ArtifactResolutionException(Collections.emptyList(), "plugin failed", null);
        when(repositorySystem.resolveArtifacts(any(), any())).thenThrow(mainFailure).thenThrow(pluginFailure);

        downloader.downloadArtifacts(Arrays.asList(
                new ArtifactWithRepoType(new DefaultArtifact("org.lib:lib-a:1.0"), RepositoryType.MAIN),
                new ArtifactWithRepoType(new DefaultArtifact("org.example:some-plugin:1.0"), RepositoryType.PLUGIN)));

        verify(repositorySystem, times(2)).resolveArtifacts(any(), any());
        assertEquals(Arrays.asList(mainFailure, pluginFailure), downloader.getErrors());
        verify(log).error("Error downloading dependencies for project");
        verify(log).error("Error downloading plugin dependencies for project");
    }

    @Test
    void errorsAreAccumulatedAndUnmodifiable() throws Exception {
        when(repositorySystem.collectDependencies(any(), any())).thenThrow(new IllegalStateException("one"), new IllegalStateException("two"));

        downloader.resolveDependencies(reactorProject);
        downloader.resolvePlugin(plugin("org.example", "some-plugin", "1.0"));

        List<Exception> errors = downloader.getErrors();
        assertEquals(Arrays.asList("one", "two"), errors.stream().map(Exception::getMessage).collect(Collectors.toList()));
        assertThrows(UnsupportedOperationException.class, () -> errors.add(new Exception()));
        verify(log, times(2)).debug(any(Throwable.class));
    }

    @Test
    void reInitClearsErrors() throws Exception {
        when(repositorySystem.collectDependencies(any(), any())).thenThrow(new IllegalStateException("boom"));
        downloader.resolveDependencies(reactorProject);
        assertEquals(1, downloader.getErrors().size());

        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest();
        buildingRequest.setRepositorySession(new DefaultRepositorySystemSession());
        downloader.init(buildingRequest, Collections.<MavenProject>emptyList(), log);

        assertTrue(downloader.getErrors().isEmpty());
    }

    @Test
    void initFindsMavenWagonExcluder() {
        // the plugin session relies on maven-core's internal WagonExcluder, which is loaded reflectively
        verify(log, times(0)).warn(anyString(), any(Throwable.class));
    }

    // --- helpers ---

    private CollectRequest captureCollectRequest() throws Exception {
        ArgumentCaptor<CollectRequest> captor = ArgumentCaptor.forClass(CollectRequest.class);
        verify(repositorySystem).collectDependencies(any(), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<ArtifactRequest> captureResolveRequests(int invocation) throws Exception {
        ArgumentCaptor<Collection<ArtifactRequest>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repositorySystem, times(2)).resolveArtifacts(any(), captor.capture());
        return new ArrayList<>(captor.getAllValues().get(invocation));
    }

    private static DefaultDependencyNode node(String coordinates, DependencyNode... children) {
        DefaultDependencyNode node = new DefaultDependencyNode(new Dependency(new DefaultArtifact(coordinates), "compile"));
        node.setChildren(new ArrayList<>(Arrays.asList(children)));
        return node;
    }

    private static CollectResult collectResult(DependencyNode root) {
        CollectResult result = new CollectResult(new CollectRequest());
        result.setRoot(root);
        return result;
    }

    private static Plugin plugin(String groupId, String artifactId, String version) {
        Plugin plugin = new Plugin();
        plugin.setGroupId(groupId);
        plugin.setArtifactId(artifactId);
        plugin.setVersion(version);
        return plugin;
    }

    private static DynamicDependency dynamicDependency(String groupId, String artifactId, String version, RepositoryType repositoryType) {
        DynamicDependency dynamicDependency = new DynamicDependency();
        dynamicDependency.setGroupId(groupId);
        dynamicDependency.setArtifactId(artifactId);
        dynamicDependency.setVersion(version);
        dynamicDependency.setRepositoryType(repositoryType);
        return dynamicDependency;
    }

    private static Set<String> coordinates(String... coordinates) {
        return new HashSet<>(Arrays.asList(coordinates));
    }

    private static Set<String> coordinatesOf(Set<ArtifactWithRepoType> artifacts) {
        return artifacts.stream().map(a -> a.getArtifact().toString()).collect(Collectors.toSet());
    }

    private static void assertAllOfType(Set<ArtifactWithRepoType> artifacts, RepositoryType type) {
        for (ArtifactWithRepoType artifact : artifacts) {
            assertSame(type, artifact.getRepositoryType(), artifact.toString());
        }
    }

    private static List<String> repositoryIds(List<RemoteRepository> repositories) {
        return repositories.stream().map(RemoteRepository::getId).collect(Collectors.toList());
    }

    private static List<String> artifactsOf(List<Dependency> dependencies) {
        return dependencies.stream().map(d -> d.getArtifact().toString()).collect(Collectors.toList());
    }

    private static List<String> requestedArtifacts(List<ArtifactRequest> requests) {
        return requests.stream().map(r -> r.getArtifact().toString()).collect(Collectors.toList());
    }
}
