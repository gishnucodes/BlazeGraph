package blazegraph.core.traversal;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;

public interface TraversalVisitor {
    /** Called when a node is first discovered. Return false to prune this branch. */
    boolean onNodeVisited(Node node, Path pathToNode);

    /** Called when an edge is about to be followed. Return false to skip it. */
    boolean onEdgeTraversed(Edge edge, Path currentPath);
}
