package de.qaware.maven.plugin.offline;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.qaware.maven.plugin.offline.TestSupport.project;
import static de.qaware.maven.plugin.offline.TestSupport.repository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolveDependenciesMojoTest {

    @Mock
    private DependencyDownloader downloader;

    @Mock
    private MavenSession session;

    @Mock
    private Log log;

    private ResolveDependenciesMojo mojo;
    private MavenProject moduleA;
    private MavenProject moduleB;
    private final List<ArtifactRepository> remoteRepositories = Collections.singletonList(repository("central"));
    private final List<ArtifactRepository> pluginRepositories = Collections.singletonList(repository("plugins"));

    @BeforeEach
    void setUp() {
        moduleA = project("org.example", "module-a", "1.0");
        moduleB = project("org.example", "module-b", "1.0");

        mojo = new ResolveDependenciesMojo();
        mojo.setLog(log);
        TestSupport.setField(mojo, "dependencyDownloader", downloader);
        TestSupport.setField(mojo, "session", session);
        TestSupport.setField(mojo, "reactorProjects", Arrays.asList(moduleA, moduleB));
        TestSupport.setField(mojo, "remoteRepositories", remoteRepositories);
        TestSupport.setField(mojo, "remotePluginRepositories", pluginRepositories);

        lenient().when(session.getProjectBuildingRequest()).thenReturn(new DefaultProjectBuildingRequest());
        lenient().when(downloader.getErrors()).thenReturn(Collections.emptyList());
    }

    @Test
    void downloadsPluginsDependenciesAndDynamicDependenciesOfAllReactorProjects() throws Exception {
        Plugin compilerPlugin = plugin("maven-compiler-plugin");
        Plugin surefirePlugin = plugin("maven-surefire-plugin");
        addBuildPlugins(moduleA, compilerPlugin);
        addBuildPlugins(moduleB, surefirePlugin);
        DynamicDependency dynamicDependency = validDynamicDependency();
        TestSupport.setField(mojo, "dynamicDependencies", Collections.singletonList(dynamicDependency));

        ArtifactWithRepoType compiler = artifact("org.apache.maven.plugins:maven-compiler-plugin:1.0", RepositoryType.PLUGIN);
        ArtifactWithRepoType surefire = artifact("org.apache.maven.plugins:maven-surefire-plugin:1.0", RepositoryType.PLUGIN);
        ArtifactWithRepoType shared = artifact("org.lib:shared:1.0", RepositoryType.MAIN);
        ArtifactWithRepoType libB = artifact("org.lib:lib-b:1.0", RepositoryType.MAIN);
        ArtifactWithRepoType dynamic = artifact("org.apache.maven.surefire:surefire-junit4:1.0", RepositoryType.PLUGIN);
        when(downloader.resolvePlugin(compilerPlugin)).thenReturn(Collections.singleton(compiler));
        when(downloader.resolvePlugin(surefirePlugin)).thenReturn(Collections.singleton(surefire));
        when(downloader.resolveDependencies(moduleA)).thenReturn(Collections.singleton(shared));
        when(downloader.resolveDependencies(moduleB)).thenReturn(new HashSet<>(Arrays.asList(shared, libB)));
        when(downloader.resolveDynamicDependency(dynamicDependency)).thenReturn(Collections.singleton(dynamic));

        mojo.execute();

        assertEquals(new HashSet<>(Arrays.asList(compiler, surefire, shared, libB, dynamic)), captureDownloadedArtifacts());
        InOrder inOrder = inOrder(downloader);
        inOrder.verify(downloader).init(any(), any(), any());
        inOrder.verify(downloader).downloadArtifacts(anyCollection());
        inOrder.verify(downloader).getErrors();
    }

    @Test
    void initializesDownloaderWithProjectRepositoriesAndReactor() throws Exception {
        mojo.execute();

        ArgumentCaptor<ProjectBuildingRequest> request = ArgumentCaptor.forClass(ProjectBuildingRequest.class);
        ArgumentCaptor<List<MavenProject>> reactor = listCaptor();
        verify(downloader).init(request.capture(), reactor.capture(), any(Log.class));
        assertEquals(remoteRepositories, request.getValue().getRemoteRepositories());
        assertEquals(pluginRepositories, request.getValue().getPluginArtifactRepositories());
        assertSame(ProjectBuildingRequest.RepositoryMerging.REQUEST_DOMINANT, request.getValue().getRepositoryMerging());
        assertTrue(request.getValue().isResolveDependencies());
        assertEquals(Arrays.asList(moduleA, moduleB), reactor.getValue());
    }

    @Test
    void sourcesAndJavadocAreDisabledByDefault() throws Exception {
        mojo.execute();

        verify(downloader, never()).enableDownloadSources();
        verify(downloader, never()).enableDownloadJavadoc();
    }

    @Test
    void enablesSourcesAndJavadocWhenConfigured() throws Exception {
        TestSupport.setField(mojo, "downloadSources", true);
        TestSupport.setField(mojo, "downloadJavadoc", true);

        mojo.execute();

        verify(downloader).enableDownloadSources();
        verify(downloader).enableDownloadJavadoc();
    }

    @Test
    void invalidDynamicDependencyFailsBeforeAnythingIsDownloaded() {
        DynamicDependency invalid = validDynamicDependency();
        invalid.setVersion(null);
        TestSupport.setField(mojo, "dynamicDependencies", Arrays.asList(validDynamicDependency(), invalid));

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals(DynamicDependency.CONFIGURATION_ERROR_MESSAGE, e.getMessage());
        verifyNoInteractions(downloader);
    }

    @Test
    void errorsAreLoggedAsWarningsButDoNotFailByDefault() throws Exception {
        when(downloader.getErrors()).thenReturn(Arrays.<Exception>asList(new Exception("first"), new Exception("second")));

        mojo.execute();

        verify(log).warn("first");
        verify(log).warn("second");
    }

    @Test
    void errorsFailTheBuildWhenFailOnErrorsIsSet() {
        TestSupport.setField(mojo, "failOnErrors", true);
        when(downloader.getErrors()).thenReturn(Collections.<Exception>singletonList(new Exception("first")));

        assertThrows(MojoExecutionException.class, mojo::execute);
        verify(log).warn("first");
    }

    @Test
    void failOnErrorsSucceedsWithoutErrors() throws Exception {
        TestSupport.setField(mojo, "failOnErrors", true);

        mojo.execute();

        verify(downloader).downloadArtifacts(anyCollection());
    }

    private Set<ArtifactWithRepoType> captureDownloadedArtifacts() {
        ArgumentCaptor<Collection<ArtifactWithRepoType>> captor = collectionCaptor();
        verify(downloader).downloadArtifacts(captor.capture());
        Set<ArtifactWithRepoType> artifacts = new HashSet<>(captor.getValue());
        assertEquals(captor.getValue().size(), artifacts.size(), "artifacts should be de-duplicated");
        return artifacts;
    }

    private static void addBuildPlugins(MavenProject project, Plugin... plugins) {
        Build build = new Build();
        build.setPlugins(Arrays.asList(plugins));
        project.getModel().setBuild(build);
    }

    private static Plugin plugin(String artifactId) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId(artifactId);
        plugin.setVersion("1.0");
        return plugin;
    }

    private static DynamicDependency validDynamicDependency() {
        DynamicDependency dependency = new DynamicDependency();
        dependency.setGroupId("org.apache.maven.surefire");
        dependency.setArtifactId("surefire-junit4");
        dependency.setVersion("1.0");
        dependency.setRepositoryType(RepositoryType.PLUGIN);
        return dependency;
    }

    private static ArtifactWithRepoType artifact(String coordinates, RepositoryType repositoryType) {
        return new ArtifactWithRepoType(new DefaultArtifact(coordinates), repositoryType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ArgumentCaptor<Collection<T>> collectionCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Collection.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ArgumentCaptor<List<T>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
