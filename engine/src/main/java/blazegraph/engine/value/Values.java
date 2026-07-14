package blazegraph.engine.value;

public class Values {
    public enum Truth {
        TRUE, FALSE, UNKNOWN;

        public static Truth from(boolean b) {
            return b ? TRUE : FALSE;
        }

        public Truth and(Truth other) {
            if (this == FALSE || other == FALSE) return FALSE;
            if (this == TRUE && other == TRUE) return TRUE;
            return UNKNOWN;
        }

        public Truth or(Truth other) {
            if (this == TRUE || other == TRUE) return TRUE;
            if (this == FALSE && other == FALSE) return FALSE;
            return UNKNOWN;
        }

        public Truth not() {
            if (this == TRUE) return FALSE;
            if (this == FALSE) return TRUE;
            return UNKNOWN;
        }

        public Truth xor(Truth other) {
            if (this == UNKNOWN || other == UNKNOWN) return UNKNOWN;
            return (this == TRUE) ^ (other == TRUE) ? TRUE : FALSE;
        }
    }
}
