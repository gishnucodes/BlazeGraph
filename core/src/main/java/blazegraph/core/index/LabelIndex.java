package blazegraph.core.index;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LabelIndex {
    private final ConcurrentHashMap<String, Set<Long>> index = new ConcurrentHashMap<>();

    public void add(String label, long id) {
        index.computeIfAbsent(label, k -> ConcurrentHashMap.newKeySet()).add(id);
    }

    public void remove(String label, long id) {
        Set<Long> ids = index.get(label);
        if (ids != null) {
            ids.remove(id);
            if (ids.isEmpty()) {
                index.remove(label, Collections.emptySet());
            }
        }
    }

    public Set<Long> get(String label) {
        return index.getOrDefault(label, Collections.emptySet());
    }

    public void clear() {
        index.clear();
    }
}
