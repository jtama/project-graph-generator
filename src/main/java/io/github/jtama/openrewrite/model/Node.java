package io.github.jtama.openrewrite.model;

import org.openrewrite.Column;

/**
 * Represents a node in the project graph, corresponding to a Java class.
 */
public class Node {

    @Column(displayName = "Group Identifier", description = "The project's group identifier the class belongs to.")
    private String artifactId;
    @Column(displayName = "Class name", description = "The simple name of the class.")
    private final String className;
    @Column(displayName = "Package name", description = "The class package name.")
    private final String packageName;
    @Column(displayName = "Incoming connections", description = "The number of other classes pointing to this class.")
    private int incomingConnections;
    @Column(displayName = "Outgoing connections", description = "The number of other classes this class points to .")
    private int outgoingConnections;

    /**
     * Constructs a new Node.
     *
     * @param className The fully qualified name of the class.
     * @param packageName The package name of the class.
     */
    public Node(String className, String packageName) {
        this.className = className;
        this.packageName = packageName;
        this.incomingConnections = 0; // Start with a base size
        this.outgoingConnections = 0; // Start with a base size
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public int getIncomingConnections() {
        return incomingConnections;
    }

    public int getOutgoingConnections() {
        return outgoingConnections;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String groupId) {
        this.artifactId = groupId;
    }

    /**
     * Increments the size of the node, typically representing an additional incoming connection.
     */
    public void incrementIncoming() {
        this.incomingConnections++;
    }

    /**
     * Increments the size of the node, typically representing an additional incoming connection.
     */
    public void incrementOutgoing() {
        this.outgoingConnections++;
    }
}