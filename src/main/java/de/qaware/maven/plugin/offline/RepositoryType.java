package de.qaware.maven.plugin.offline;

/**
 * Enum for the types of remote repositories used in maven.
 *
 * @author Andreas Janning andreas.janning@qaware.de
 */
public enum RepositoryType {
    /**
     * RemoteRepository used for downloading project dependencies
     */
    MAIN,
    /**
     * RemoteRepository used for downloading plugins and their dependencies.
     */
    PLUGIN
}
