import java.lang.reflect.Field;

public class Sort {

    @FunctionalInterface
    public interface Sorter<T> {
        int sort(T a, T b);
    }

    public static <T extends Table.Row> Sorter<T> Ascending(String column) {
        return (a, b) -> {
            try {
                Field field = a.getClass().getDeclaredField(column);
                field.setAccessible(true);
                Object aVal = field.get(a);
                Object bVal = field.get(b);
                if (aVal instanceof Comparable) {
                    return ((Comparable) aVal).compareTo(bVal);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        };
    }

    public static <T extends Table.Row> Sorter<T> Descending(String column) {
        return (a, b) -> {
            try {
                Field field = a.getClass().getDeclaredField(column);
                field.setAccessible(true);
                Object aVal = field.get(a);
                Object bVal = field.get(b);
                if (aVal instanceof Comparable) {
                    return ((Comparable) bVal).compareTo(aVal);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        };
    }
}
