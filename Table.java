import java.io.File;
import java.io.PrintWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

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
        if (wrapper.getName().equals("java.lang.Integer")) {
            return int.class;
        } else if (wrapper.getName().equals("java.lang.Float")) {
            return float.class;
        } else if (wrapper.getName().equals("java.lang.Double")) {
            return double.class;
        } else if (wrapper.getName().equals("java.lang.Boolean")) {
            return boolean.class;
        } else if (wrapper.getName().equals("java.lang.Character")) {
            return char.class;
        } else if (wrapper.getName().equals("java.lang.Byte")) {
            return byte.class;
        } else if (wrapper.getName().equals("java.lang.Short")) {
            return short.class;
        } else if (wrapper.getName().equals("java.lang.Long")) {
            return long.class;
        } else if (wrapper.getName().equals("java.lang.String")) {
            return String.class;
        } else {
            return null;
        }
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

    private HashMap<String, Field> columns = new HashMap<>();

    private ArrayList<String> columnNames = new ArrayList<>();
    private ArrayList<T> rows = new ArrayList<>();

    private Class<T> rowclass;

    private final int numColumns;
    private int numRows = 0;

    private String filename;

    public Table(Class<T> rowclass) {
        this.rowclass = rowclass;
        this.numColumns = this.loadColumnFields();
    }

    public Table(Class<T> rowclass, String filename) {
        this(rowclass);
        this.filename = filename;
    }

    public void loadCSV(String filename) {
        if (this.filename == null) {
            this.filename = filename;
        }

        int lineNumber = 1;
        try (Scanner scanner = new Scanner(new File(filename))) {
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
                                            filename
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
                        Field field = this.columns.get(this.columnNames.get(i));
                        Class<?> type = field.getType();
                        Class<?> parsedType = ParseTypeFromString(parts[i]);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadCSV() {
        this.loadCSV(this.filename);
    }

    public void saveCSV(String filename) {
        try (PrintWriter writer = new PrintWriter(filename)) {
            writer.write(this.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveCSV() {
        this.saveCSV(this.filename);
    }

    // Gets all the values in a column
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
                    field.setAccessible(true);
                    Object value = field.get(row);
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

    public boolean addRow(Object...values) {
        T row = this.createRow(values);
        if (row != null) {
            this.rows.add(row);
            this.numRows++;
            return true;
        } else {
            return false;
        }
    }

    public boolean addRow(T row) {
        if (row != null) {
            this.rows.add(row);
            this.numRows++;
            return true;
        } else {
            return false;
        }
    }

    public T createRow(Object[] values) {
        try {
            Class<?>[] types = new Class<?>[values.length];
            Object[] args = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                types[i] = WrapperToPrimitive(ParseTypeFromString(values[i].toString()));
                args[i] = values[i];
            }
            return this.rowclass.getDeclaredConstructor(types).newInstance(args);
        } catch (Exception e1) {
            try {
                T row = this.rowclass.getDeclaredConstructor().newInstance();
                for (int i = 0; i < values.length; i++) {
                    Field field = this.columns.get(this.columnNames.get(i));
                    field.set(row, ParseValueFromString(values[i].toString()));
                }
                return row;
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            e1.printStackTrace();
        }
        return null;
    }

    private int loadColumnFields() {
        Field[] fields = this.rowclass.getDeclaredFields();
        int columnCount = 0;
        for (Field field : fields) {
            if (field.isAnnotationPresent(Column.class)) {
                String fname = field.getName().toLowerCase();
                columns.put(fname, field);
                this.columnNames.add(fname);
                columnCount++;
            }
        }
        return columnCount;
    }

}
