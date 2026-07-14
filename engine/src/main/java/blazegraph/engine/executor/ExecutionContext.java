package blazegraph.engine.executor;

import blazegraph.core.storage.PropertyGraphStore;

public class ExecutionContext {
    private final PropertyGraphStore store;
    
    private long executionTimeMs = 0;
    private long nodesCreated = 0;
    private long nodesDeleted = 0;
    private long edgesCreated = 0;
    private long edgesDeleted = 0;
    private long propertiesSet = 0;
    private long labelsAdded = 0;
    private boolean cancelled = false;

    public ExecutionContext(PropertyGraphStore store) {
        this.store = store;
    }

    public PropertyGraphStore getStore() {
        return store;
    }
    
    public void incrementNodesCreated() { nodesCreated++; }
    public void incrementNodesDeleted() { nodesDeleted++; }
    public void incrementEdgesCreated() { edgesCreated++; }
    public void incrementEdgesDeleted() { edgesDeleted++; }
    public void incrementPropertiesSet() { propertiesSet++; }
    public void incrementLabelsAdded() { labelsAdded++; }

    public long getNodesCreated() { return nodesCreated; }
    public long getNodesDeleted() { return nodesDeleted; }
    public long getEdgesCreated() { return edgesCreated; }
    public long getEdgesDeleted() { return edgesDeleted; }
    public long getPropertiesSet() { return propertiesSet; }
    public long getLabelsAdded() { return labelsAdded; }
    
    public void setExecutionTimeMs(long ms) { this.executionTimeMs = ms; }
    public long getExecutionTimeMs() { return executionTimeMs; }

    public void checkCancelled() {
        if (cancelled) throw new blazegraph.engine.eval.ExecutionException("Query cancelled");
    }
    
    public void cancel() {
        cancelled = true;
    }
}
