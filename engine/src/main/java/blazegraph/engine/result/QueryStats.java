package blazegraph.engine.result;

public record QueryStats(
    long executionTimeMs,
    long nodesCreated,
    long nodesDeleted,
    long edgesCreated,
    long edgesDeleted,
    long propertiesSet,
    long labelsAdded,
    long rowCount
) {}
