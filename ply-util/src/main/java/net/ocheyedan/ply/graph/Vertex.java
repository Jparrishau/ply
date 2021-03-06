package net.ocheyedan.ply.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: blangel
 * Date: 11/4/11
 * Time: 9:53 AM
 *
 * A vertex within a {@link Graph<T>}
 * Code influenced by {@see org.codehaus.plexus.util.dag.Vertex}
 */
public final class Vertex<T> {

    private final List<Vertex<T>> parents;

    private final List<Vertex<T>> children;

    private final T value;

    Vertex(T value) {
        this.parents = new ArrayList<Vertex<T>>();
        this.children = new ArrayList<Vertex<T>>();
        this.value = value;
    }

    void addEdgeTo(Vertex<T> vertex) {
        if (!children.contains(vertex)) {
            children.add(vertex);
        }
    }

    void addEdgeFrom(Vertex<T> vertex) {
        if (!parents.contains(vertex)) {
            parents.add(vertex);
        }
    }

    void removeEdgeTo(Vertex<T> vertex) {
        children.remove(vertex);
    }

    void removeEdgeFrom(Vertex<T> vertex) {
        parents.remove(vertex);
    }

    public boolean hasEdgeTo(Vertex<T> to) {
        return children.contains(to);
    }

    public boolean hasEdgeFrom(Vertex<T> from) {
        return parents.contains(from);
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public boolean isRoot() {
        return parents.isEmpty();
    }

    public Vertex<T> getAnyParent() {
        return (parents.isEmpty() ? null : parents.get(0));
    }

    public T getValue() {
        return value;
    }

    public boolean isConnected() {
        return (isLeaf() || isRoot());
    }

    public List<Vertex<T>> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override public String toString() {
        return (value == null ? "" : value.toString());
    }

    public String toExtendedString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(toString());
        for (Vertex<T> child : children) {
            if (buffer.length() > toString().length()) {
                buffer.append("\n\t");
            }
            buffer.append(" -> ");
            buffer.append(child.toExtendedString());
        }
        return buffer.toString();
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Vertex vertex = (Vertex) o;
        return (value == null ? vertex.value == null : value.equals(vertex.value));
    }

    @Override public int hashCode() {
        return value == null ? 0 : value.hashCode();
    }
}
