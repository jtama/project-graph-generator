package io.github.jtama.openrewrite.model;

import org.openrewrite.Column;

/**
 * Represents an interaction between two Java classes.
 */
public class Link {

    @Column(displayName = "Source", description = "The fully qualified name of the source class.")
    private final String source;
    @Column(displayName = "Target", description = "The fully qualified name of the target class.")
    private final String target;
    @Column(displayName = "The link weight", description = "The number of times these to classes relate to each other")
    private int weight;

    /**
     * Constructs a new Link.
     *
     * @param source The fully qualified name of the source class.
     * @param target The fully qualified name of the target class.
     */
    public Link(String source, String target) {
        this.source = source;
        this.target = target;
        this.weight = 1; // Start with a base weight
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public int getWeight() {
        return weight;
    }

    /**
     * Increments the weight of the link, representing an additional interaction between the two classes.
     */
    public void incrementWeight() {
        this.weight++;
    }
}