package blazegraph.core.index;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TypeIndex {
    private final ConcurrentHashMap<String, Set<Long>> index = new ConcurrentHashMap<>();

    public void add(String type, long id) {
        index.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet()).add(id);
    }

    public void remove(String type, long id) {
        Set<Long> ids = index.get(type);
        if (ids != null) {
            ids.remove(id);
            if (ids.isEmpty()) {
                index.remove(type, Collections.emptySet());
            }
        }
    }

    public Set<Long> get(String type) {
        return index.getOrDefault(type, Collections.emptySet());
    }

    public void clear() {
        index.clear();
    }
}
