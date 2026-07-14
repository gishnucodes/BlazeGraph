package blazegraph.core.index;

import blazegraph.core.model.PropertyValue;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PropertyIndex {
    private final ConcurrentHashMap<String, ConcurrentHashMap<PropertyValue, Set<Long>>> index = new ConcurrentHashMap<>();

    public void add(String propertyKey, PropertyValue value, long id) {
        index.computeIfAbsent(propertyKey, k -> new ConcurrentHashMap<>())
             .computeIfAbsent(value, k -> ConcurrentHashMap.newKeySet())
             .add(id);
    }

    public void remove(String propertyKey, PropertyValue value, long id) {
        ConcurrentHashMap<PropertyValue, Set<Long>> valueMap = index.get(propertyKey);
        if (valueMap != null) {
            Set<Long> ids = valueMap.get(value);
            if (ids != null) {
                ids.remove(id);
                if (ids.isEmpty()) {
                    valueMap.remove(value, Collections.emptySet());
                }
            }
            if (valueMap.isEmpty()) {
                index.remove(propertyKey, Collections.emptyMap());
            }
        }
    }

    public Set<Long> get(String propertyKey, PropertyValue value) {
        ConcurrentHashMap<PropertyValue, Set<Long>> valueMap = index.get(propertyKey);
        if (valueMap == null) {
            return Collections.emptySet();
        }
        return valueMap.getOrDefault(value, Collections.emptySet());
    }

    public void clear() {
        index.clear();
    }
}
