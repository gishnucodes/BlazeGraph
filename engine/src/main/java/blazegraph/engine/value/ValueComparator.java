package blazegraph.engine.value;

import blazegraph.core.model.Edge;
import blazegraph.core.model.Node;
import blazegraph.core.model.PropertyValue;
import blazegraph.core.traversal.Path;

import java.util.Comparator;
import java.util.List;

public class ValueComparator implements Comparator<Object> {
    public static final ValueComparator INSTANCE = new ValueComparator();

    @Override
    public int compare(Object o1, Object o2) {
        if (o1 == null && o2 == null) return 0;
        if (o1 == null) return 1; // NULLs last ascending
        if (o2 == null) return -1;
        
        int t1 = typeOrder(o1);
        int t2 = typeOrder(o2);
        if (t1 != t2) return Integer.compare(t1, t2);

        if (o1 instanceof PropertyValue p1 && o2 instanceof PropertyValue p2) {
            return compareProperties(p1, p2);
        } else if (o1 instanceof Node n1 && o2 instanceof Node n2) {
            return Long.compare(n1.getId(), n2.getId());
        } else if (o1 instanceof Edge e1 && o2 instanceof Edge e2) {
            return Long.compare(e1.getId(), e2.getId());
        } else if (o1 instanceof Path p1 && o2 instanceof Path p2) {
            return Integer.compare(p1.length(), p2.length());
        } else if (o1 instanceof List<?> l1 && o2 instanceof List<?> l2) {
            int len = Math.min(l1.size(), l2.size());
            for (int i = 0; i < len; i++) {
                int c = compare(l1.get(i), l2.get(i));
                if (c != 0) return c;
            }
            return Integer.compare(l1.size(), l2.size());
        }
        return 0;
    }

    private int typeOrder(Object o) {
        if (o instanceof PropertyValue p) {
            return switch (p.getType()) {
                case BOOLEAN -> 1;
                case INTEGER, DOUBLE -> 2;
                case STRING -> 3;
                case LIST -> 4;
                case NULL -> 0; // Handled before
            };
        } else if (o instanceof List) return 4;
        else if (o instanceof Node) return 5;
        else if (o instanceof Edge) return 6;
        else if (o instanceof Path) return 7;
        return 8;
    }

    private int compareProperties(PropertyValue p1, PropertyValue p2) {
        if (p1.getType() == PropertyValue.Type.BOOLEAN) {
            return Boolean.compare((Boolean) p1.getValue(), (Boolean) p2.getValue());
        } else if (p1.getType() == PropertyValue.Type.STRING) {
            return ((String) p1.getValue()).compareTo((String) p2.getValue());
        } else if (isNumber(p1) && isNumber(p2)) {
            return Double.compare(asDouble(p1), asDouble(p2));
        } else if (p1.getType() == PropertyValue.Type.LIST && p2.getType() == PropertyValue.Type.LIST) {
            List<?> l1 = (List<?>) p1.getValue();
            List<?> l2 = (List<?>) p2.getValue();
            int len = Math.min(l1.size(), l2.size());
            for (int i = 0; i < len; i++) {
                int c = compare(l1.get(i), l2.get(i));
                if (c != 0) return c;
            }
            return Integer.compare(l1.size(), l2.size());
        }
        return 0;
    }

    public static boolean equalsWithCoercion(Object o1, Object o2) {
        Integer cmp = compareForPredicate(o1, o2);
        return cmp != null && cmp == 0;
    }

    public static Integer compareForPredicate(Object o1, Object o2) {
        if (o1 == null || o2 == null) return null;
        if (o1 instanceof PropertyValue p1 && o2 instanceof PropertyValue p2) {
            if (isNumber(p1) && isNumber(p2)) {
                return Double.compare(asDouble(p1), asDouble(p2));
            }
            if (p1.getType() != p2.getType()) return null; // Incompatible
            return INSTANCE.compareProperties(p1, p2);
        }
        if (o1.getClass() != o2.getClass()) return null;
        return INSTANCE.compare(o1, o2);
    }

    private static boolean isNumber(PropertyValue p) {
        return p.getType() == PropertyValue.Type.INTEGER || p.getType() == PropertyValue.Type.DOUBLE;
    }

    private static double asDouble(PropertyValue p) {
        if (p.getType() == PropertyValue.Type.INTEGER) return ((Long) p.getValue()).doubleValue();
        return (Double) p.getValue();
    }
}
