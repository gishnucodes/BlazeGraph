package blazegraph.core.index;

import blazegraph.core.model.PropertyValue;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

public class IndexTest {
    @Test
    public void testLabelIndex() {
        LabelIndex idx = new LabelIndex();
        idx.add("Person", 1L);
        idx.add("Person", 2L);
        Set<Long> res = idx.get("Person");
        assertTrue(res.contains(1L));
        assertTrue(res.contains(2L));
        idx.remove("Person", 1L);
        assertFalse(idx.get("Person").contains(1L));
    }
    
    @Test
    public void testTypeIndex() {
        TypeIndex idx = new TypeIndex();
        idx.add("KNOWS", 100L);
        assertTrue(idx.get("KNOWS").contains(100L));
        idx.remove("KNOWS", 100L);
        assertTrue(idx.get("KNOWS").isEmpty());
    }
    
    @Test
    public void testPropertyIndex() {
        PropertyIndex idx = new PropertyIndex();
        PropertyValue val = PropertyValue.of("Alice");
        idx.add("name", val, 1L);
        assertTrue(idx.get("name", val).contains(1L));
        idx.remove("name", val, 1L);
        assertTrue(idx.get("name", val).isEmpty());
    }
}
