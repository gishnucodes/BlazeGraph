package blazegraph.engine.value;

import blazegraph.core.model.PropertyValue;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ValueTest {
    @Test
    public void test3VL() {
        Values.Truth T = Values.Truth.TRUE;
        Values.Truth F = Values.Truth.FALSE;
        Values.Truth U = Values.Truth.UNKNOWN;

        assertEquals(U, U.not());
        assertEquals(F, T.not());
        assertEquals(T, F.not());

        assertEquals(F, U.and(F));
        assertEquals(U, U.and(T));
        assertEquals(U, U.and(U));

        assertEquals(U, U.or(F));
        assertEquals(T, U.or(T));
        assertEquals(U, U.or(U));
    }

    @Test
    public void testValueComparator() {
        ValueComparator c = ValueComparator.INSTANCE;
        Object p1 = PropertyValue.of(10L);
        Object p2 = PropertyValue.of(10.0);
        Object p3 = PropertyValue.of("abc");
        Object p4 = null;

        assertEquals(0, c.compare(p1, p2));
        assertTrue(c.compare(p1, p3) < 0);
        assertTrue(c.compare(p1, p4) < 0); // null is last
        assertTrue(c.compare(p3, p4) < 0);

        assertTrue(ValueComparator.equalsWithCoercion(p1, p2));
        assertFalse(ValueComparator.equalsWithCoercion(p1, p3));
        
        assertNull(ValueComparator.compareForPredicate(p1, p3));
    }
}
