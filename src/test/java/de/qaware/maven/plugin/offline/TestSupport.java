package de.qaware.maven.plugin.offline;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Helpers for building maven model objects and injecting fields that are normally set by plexus/sisu.
 */
final class TestSupport {

    private TestSupport() {
    }

    static MavenProject project(String groupId, String artifactId, String version) {
        MavenProject project = new MavenProject();
        project.setGroupId(groupId);
        project.setArtifactId(artifactId);
        project.setVersion(version);
        project.setArtifact(ReactorArtifactTest.mavenArtifact(groupId, artifactId, version, "jar", null));
        return project;
    }

    static Dependency dependency(String groupId, String artifactId, String version) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(groupId);
        dependency.setArtifactId(artifactId);
        dependency.setVersion(version);
        return dependency;
    }

    static ArtifactRepository repository(String id) {
        return new MavenArtifactRepository(id, "https://repo.example.org/" + id, new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy(), new ArtifactRepositoryPolicy());
    }

    static void setField(Object target, String name, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("No field " + name + " on " + target.getClass());
    }

    /**
     * Mimics the handlers maven registers for the artifact types used in the tests.
     */
    static class StubArtifactHandlerManager implements ArtifactHandlerManager {

        @Override
        public ArtifactHandler getArtifactHandler(String type) {
            if ("test-jar".equals(type)) {
                DefaultArtifactHandler handler = new DefaultArtifactHandler(type) {
                    @Override
                    public String getClassifier() {
                        return "tests";
                    }
                };
                handler.setExtension("jar");
                return handler;
            }
            DefaultArtifactHandler handler = new DefaultArtifactHandler(type);
            if ("maven-plugin".equals(type)) {
                handler.setExtension("jar");
            }
            return handler;
        }

        @Override
        public void addHandlers(Map<String, ArtifactHandler> handlers) {
            throw new UnsupportedOperationException();
        }
    }
}
