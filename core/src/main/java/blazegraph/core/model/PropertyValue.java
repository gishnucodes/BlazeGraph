package blazegraph.core.model;

import java.util.Objects;

public final class PropertyValue implements Comparable<PropertyValue> {
    public enum Type {
        STRING, INTEGER, DOUBLE, BOOLEAN, LIST, NULL
    }

    private final Type type;
    private final Object value;

    private PropertyValue(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static final PropertyValue NULL = new PropertyValue(Type.NULL, null);

    public static PropertyValue of(String value) {
        return new PropertyValue(Type.STRING, Objects.requireNonNull(value));
    }

    public static PropertyValue of(java.util.List<PropertyValue> value) {
        return new PropertyValue(Type.LIST, java.util.Collections.unmodifiableList(new java.util.ArrayList<>(value)));
    }

    public static PropertyValue of(long value) {
        return new PropertyValue(Type.INTEGER, value);
    }

    public static PropertyValue of(double value) {
        return new PropertyValue(Type.DOUBLE, value);
    }

    public static PropertyValue of(boolean value) {
        return new PropertyValue(Type.BOOLEAN, value);
    }

    public Type getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public String asString() {
        return (String) value;
    }

    public long asLong() {
        return (Long) value;
    }

    public double asDouble() {
        return (Double) value;
    }

    public boolean asBoolean() {
        return (Boolean) value;
    }

    @SuppressWarnings("unchecked")
    public java.util.List<PropertyValue> asList() {
        return (java.util.List<PropertyValue>) value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyValue that = (PropertyValue) o;
        return type == that.type && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    /**
     * Warning: This compareTo implementation is type-strict (INTEGER(30) != DOUBLE(30.0))
     * and cross-type compare orders by enum. It is correct for index hash keys but
     * wrong for GQL comparison semantics.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public int compareTo(PropertyValue o) {
        if (this.type != o.type) {
            return this.type.compareTo(o.type);
        }
        if (this.value instanceof Comparable) {
            return ((Comparable) this.value).compareTo(o.value);
        }
        return 0;
    }
}
