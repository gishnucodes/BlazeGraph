package blazegraph.core.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class PropertyValueTest {
    @Test
    public void testListAndNull() {
        PropertyValue nullVal = PropertyValue.NULL;
        assertEquals(PropertyValue.Type.NULL, nullVal.getType());

        PropertyValue listVal = PropertyValue.of(List.of(PropertyValue.of("a"), PropertyValue.of("b")));
        assertEquals(PropertyValue.Type.LIST, listVal.getType());
        assertEquals(2, listVal.asList().size());
        assertEquals("a", listVal.asList().get(0).asString());
    }
}
