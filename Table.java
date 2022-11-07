import java.io.File;
import java.io.PrintWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Table<T extends Table.Row> {

    private static boolean PrimitiveIsWrapper(Class<?> primitive, Class<?> wrapper) {
        return (primitive.getName().equals("int") && wrapper.getName().equals("java.lang.Integer")) ||
                (primitive.getName().equals("float") && wrapper.getName().equals("java.lang.Float")) ||
                (primitive.getName().equals("double") && wrapper.getName().equals("java.lang.Double")) ||
                (primitive.getName().equals("boolean") && wrapper.getName().equals("java.lang.Boolean")) ||
                (primitive.getName().equals("char") && wrapper.getName().equals("java.lang.Character")) ||
                (primitive.getName().equals("byte") && wrapper.getName().equals("java.lang.Byte")) ||
                (primitive.getName().equals("short") && wrapper.getName().equals("java.lang.Short")) ||
                (primitive.getName().equals("long") && wrapper.getName().equals("java.lang.Long")) ||
                (primitive.getName().equals("java.lang.String") && wrapper.getName().equals("java.lang.String"));
    }

    private static Class<?> WrapperToPrimitive(Class<?> wrapper) {
        return switch (wrapper.getName()) {
            case "java.lang.Integer" -> int.class;
            case "java.lang.Float" -> float.class;
            case "java.lang.Double" -> double.class;
            case "java.lang.Boolean" -> boolean.class;
            case "java.lang.Character" -> char.class;
            case "java.lang.Byte" -> byte.class;
            case "java.lang.Short" -> short.class;
            case "java.lang.Long" -> long.class;
            case "java.lang.String" -> String.class;
            default -> null;
        };
    }

    private static Class<?> ParseTypeFromString(String str) {
        try {
            Integer.parseInt(str);
            return Integer.class;
        } catch (Exception e) {
            try {
                Float.parseFloat(str);
                return Float.class;
            } catch (Exception e2) {
                if (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false")) {
                    return Boolean.class;
                } else {
                    return String.class;
                }
            }
        }
    }

    private static Object ParseValueFromString(String str) {
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            try {
                return Float.parseFloat(str);
            } catch (Exception e2) {
                if (str.equalsIgnoreCase("true")) {
                    return true;
                } else if (str.equalsIgnoreCase("false")) {
                    return false;
                } else {
                    return str;
                }
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Column {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Enum {
        Class<?> enumClass();
    }

    private interface Mapper<T> {
        T map(Object t);
    }

    public static class Row {

        public Row() {}

        public <T> ArrayList<T> mapColumns(Mapper<T> mapper) {
            ArrayList<T> list = new ArrayList<>();
            for (Field field : this.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Column.class)) {
                    try {
                        list.add(mapper.map(field.get(this)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return list;
        }

        public void forEachColumn(Consumer<Object> consumer) {
            for (Field field : this.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Column.class)) {
                    try {
                        consumer.accept(field.get(this));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    private final HashMap<String, Field> columns = new HashMap<>();
    private final HashMap<String, Class<?>> enumResolvers = new HashMap<>();

    private final ArrayList<String> columnNames = new ArrayList<>();
    private final ArrayList<T> rows = new ArrayList<>();

    private final Class<T> rowclass;

    private final int numColumns;
    private int numRows = 0;

    private String filePath;

    private boolean allowDuplicates;

    public Table(Class<T> rowclass) {
        this.rowclass = rowclass;
        this.numColumns = this.doReflectionTasks();
    }

    public Table(Class<T> rowclass, String filePath) {
        this(rowclass);
        this.filePath = filePath;
    }

    public Table(Class<T> rowclass, String filePath, boolean allowDuplicates) {
        this(rowclass, filePath);
        this.allowDuplicates = allowDuplicates;
    }

    public void loadCSV(String filePath) {
        if (this.filePath == null) {
            this.filePath = filePath;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        int lineNumber = 1;
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String[] parts = scanner.nextLine().split(",");
                // Get rid of any leading or trailing whitespace and make everything lowercase
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].trim();
                }

                if (lineNumber == 1) {
                    // Inside here we validate that the first line contains the correct column names,
                    // and they match the Row class we were given
                    for (String str : parts) {
                        if (!this.columnNames.contains(str)) {
                            throw new Exceptions.InvalidColumn(
                                    String.format("Column %s does not exist on %s",
                                            str,
                                            this.rowclass.getName()
                                    )
                            );
                        }
                    }

                    // If there was a column declared on our Row class that wasn't in the CSV file,
                    // we throw an exception
                    for (String str : this.columnNames) {
                        if (!Arrays.asList(parts).contains(str)) {
                            throw new Exceptions.InvalidColumn(
                                    String.format("Column %s exists on '%s' but was not found in %s",
                                            this.columns.get(str).getName(),
                                            this.rowclass.getName(),
                                            filePath
                                    )
                            );
                        }
                    }
                } else {
                    if (parts.length != this.columnNames.size()) {
                        throw new Exceptions.Parse(
                                String.format("Line %d has %d columns, expected %d",
                                        lineNumber,
                                        parts.length,
                                        this.columnNames.size()
                                )
                        );
                    }

                    for (int i = 0; i < this.columnNames.size(); i++) {
                        String columnName = this.columnNames.get(i);
                        Field field = this.columns.get(columnName);
                        Class<?> type = field.getType();
                        Class<?> parsedType = ParseTypeFromString(parts[i]);
                        if (this.enumResolvers.containsKey(columnName)) {
                            if (parsedType != Integer.class) {
                                throw new Exceptions.Parse(
                                        String.format("Line %d, column %s: Expected enum ordinal, got %s",
                                                lineNumber,
                                                columnName,
                                                parsedType.getName()
                                        )
                                );
                            }
                            Class<?> enumClass = this.enumResolvers.get(columnName);
                            ArrayList<java.lang.Enum<?>> enumConstants = new ArrayList<>(Arrays.asList((java.lang.Enum<?>[]) enumClass.getEnumConstants()));
                            List<Integer> ordinals = enumConstants.stream().map(java.lang.Enum::ordinal).collect(Collectors.toList());
                            if (!ordinals.contains(Integer.parseInt(parts[i]))) {
                                throw new Exceptions.Parse(
                                        String.format("Line %d, column %s: %s is not a valid enum value for %s",
                                                lineNumber,
                                                columnName,
                                                parts[i],
                                                enumClass.getName()
                                        )
                                );
                            }
                        } else
                        // Make sure that the type of the value in the CSV file matches the correct column type declared in our row class
                        if (!PrimitiveIsWrapper(type, parsedType)) {
                            String[] wrapperParts = parsedType.getName().split("\\.");
                            // Converts wrapper class name like java.lang.Float to float
                            // Matches more similarly to the results of field.getType().getName()
                            String wrapperName = wrapperParts[wrapperParts.length - 1].toLowerCase();
                            throw new Exceptions.Parse(
                                    String.format("Line %d column '%s' has type %s, expected %s",
                                            lineNumber,
                                            this.columnNames.get(i),
                                            wrapperName,
                                            type.getName()
                                    )
                            );
                        }
                    }

                    if (!this.addRow(parts)) {
                        throw new Exceptions.Parse(
                                String.format("An instance of Row:%s could not be generated for unknown reasons. Line %d",
                                        this.rowclass.getName(),
                                        lineNumber
                                )
                        );
                    }
                }

                lineNumber++;
            }

            if (!this.allowDuplicates) {
                this.removeDuplicates();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadCSV() {
        this.loadCSV(this.filePath);
    }

    public void saveCSV(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.write(this.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveCSV() {
        this.saveCSV(this.filePath);
    }

    /**
     * Removes all duplicates from the table that are currently present in this.rows
     * @implNote This method requires that the Row class implements the Row.Equals interface or at least overrides the equals method from java.lang.Object
     */
    public void removeDuplicates() {
        for (int i = 0; i < this.rows.size(); i++) {
            for (int j = i + 1; j < this.rows.size(); j++) {
                if (this.rows.get(i).equals(this.rows.get(j))) {
                    this.rows.remove(j);
                    j--;
                }
            }
        }
    }

    @FunctionalInterface
    public interface Caster<T> {
        T cast(Object o);
    }

    // Gets all the values in a column
    // Returns a list with wildcard type so the type can be inferred
    // Sadly, even with reflection, we can't get the type of the column to be accessible as the return type as much as I'd like to
    public ArrayList<?> getColumn(String column) {
        ArrayList<Object> list = new ArrayList<>();
        for (T row : this.rows) {
            try {
                list.add(this.columns.get(column).get(row));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    // Returns a typed listen given a column name and casting function
    public <C> ArrayList<C> getColumn(String column, Caster<C> caster) {
        ArrayList<C> list = new ArrayList<>();
        for (T row : this.rows) {
            try {
                list.add(caster.cast(this.columns.get(column).get(row)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    public ArrayList<T> getRows() {
        return this.rows;
    }

    @FunctionalInterface
    public interface RowFilter<T> {
        boolean filter(T row);
    }

    public T find(RowFilter<T> filter) {
        for (T row : this.rows) {
            if (filter.filter(row)) {
                return row;
            }
        }
        return null;
    }

    public ArrayList<T> findAll(RowFilter<T> filter) {
        ArrayList<T> list = new ArrayList<>();
        for (T row : this.rows) {
            if (filter.filter(row)) {
                list.add(row);
            }
        }
        return list;
    }

    public void sort(Sort.Sorter<T> sorter) {
        this.rows.sort(sorter::sort);
    }

    public boolean contains(T row) {
        for (T t : this.rows) {
            if (t.equals(row)) {
                return true;
            }
        }
        return false;
    }

    public boolean delete(T row) {
        return this.rows.remove(row);
    }

    public boolean findDelete(RowFilter<T> filter) {
        T row = this.find(filter);
        if (row != null) {
            return this.delete(row);
        }
        return false;
    }

    public int getNumColumns() {
        return this.numColumns;
    }

    public int getNumRows() {
        return this.numRows;
    }

    public int[] size() {
        return new int[]{this.numColumns, this.numRows};
    }

    public String toString() {
        StringBuilder out = new StringBuilder();
        for (String str : this.columnNames) {
            out.append(str);
            if (!Objects.equals(str, this.columnNames.get(this.columnNames.size() - 1))) {
                out.append(",");
            }
        }

        for (T row : this.rows) {
            out.append("\n");
            for (String str : this.columnNames) {
                try {
                    Field field = this.columns.get(str);
                    Object value = field.get(row);
                    if (this.enumResolvers.containsKey(str)) {
                        value = ((java.lang.Enum<?>) value).ordinal();
                    }

                    out.append(value.toString());
                    if (!Objects.equals(str, this.columnNames.get(this.columnNames.size() - 1))) {
                        out.append(",");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return out.toString();
    }

    @SafeVarargs
    public final <V> boolean addRow(V... values) {
        T row = this.createRow(values);
        return this.addRow(row);
    }

    public boolean addRow(T row) {
        if (row != null) {
            if (!this.allowDuplicates && this.contains(row)) {
                System.out.printf("Row '%s' already exists in table\n", row);
                return false;
            }
            this.rows.add(row);
            this.numRows++;
            return true;
        } else {
            return false;
        }
    }

    public boolean addAllRows(T...rows) {
        for (T row : rows) {
            if (!this.addRow(row)) {
                return false;
            }
        }
        return true;
    }

    public T createRow(Object[] values) {
        try {
            Class<?>[] types = new Class<?>[values.length];
            Object[] args = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                if (this.enumResolvers.containsKey(this.columnNames.get(i))) {
                    types[i] = this.enumResolvers.get(this.columnNames.get(i));
                    args[i] = this.enumResolvers.get(this.columnNames.get(i)).getEnumConstants()[Integer.parseInt(values[i].toString())];
                } else {
                    types[i] = WrapperToPrimitive(ParseTypeFromString(values[i].toString()));
                    args[i] = values[i];
                }
            }
            return this.rowclass.getDeclaredConstructor(types).newInstance(args);
        } catch (Exception e1) {
            try {
                T row = this.rowclass.getDeclaredConstructor().newInstance();
                for (int i = 0; i < values.length; i++) {
                    Field field = this.columns.get(this.columnNames.get(i));
                    if (this.enumResolvers.containsKey(this.columnNames.get(i))) {
                        field.set(row, this.enumResolvers.get(this.columnNames.get(i)).getEnumConstants()[Integer.parseInt(values[i].toString())]);
                    } else {
                        field.set(row, ParseValueFromString(values[i].toString()));
                    }
                }
                return row;
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            e1.printStackTrace();
        }
        return null;
    }

    private int doReflectionTasks() {
        Field[] fields = this.rowclass.getDeclaredFields();
        int columnCount = 0;
        for (Field field : fields) {
            String fname = field.getName().toLowerCase();
            if (field.isAnnotationPresent(Column.class)) {
                field.setAccessible(true);
                columns.put(fname, field);
                this.columnNames.add(fname);
                columnCount++;
            }
            if (field.isAnnotationPresent(Enum.class)) {
                this.enumResolvers.put(fname, field.getAnnotation(Enum.class).enumClass());
            }
        }
        return columnCount;
    }

}
