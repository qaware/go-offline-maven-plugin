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
    MAIN("project"),

    /**
     * RemoteRepository used for downloading plugins and their dependencies.
     */
    PLUGIN("plugin");

    private final String requestContext;

    /**
     * Constructor for RepositoryType
     * 
     * @param requestContext the context for the request
     */
    RepositoryType(String requestContext) {
        this.requestContext = requestContext;
    }

    /**
     * Returns the remote repository request context.
     * 
     * @return the remote repository request context.
     */
    public String getRequestContext() {
        return requestContext;
    }
}
