# csv-java
```java
class User extends Table.Row {

    // Must declare a default constructor
    public User() {}

    // Optional constructor for personal use
    public User(String name, float height, int age) {
        this.name = name;
        this.height = height;
        this.age = age;
    }

    @Table.Column
    public String name;

    @Table.Column
    public float height;

    @Table.Column
    public int age;

    // Overriding toString is optional and only for your use. It is not called to populate the string that is written to the output file.
    public String toString() {
        return String.format("%s,%.2f,%d", name, height, age);
    }

}

public class Example {

    public static void print(Object...o) {
        for (int i = 0; i < o.length; i++) {
            System.out.print(o[i]);
            if (i < o.length - 1) {
                System.out.print(", ");
            }
        }
    }

    public static void println(Object...o) {
        print(o);
        System.out.println();
    }

    public static void main(String[] args) {
        Table<User> table = new Table<>(User.class);
        try {
            // Filename could also be declared as the second argument of the Table constructor
            table.loadCSV("users.csv");

            // Add row using var args. Will automatically load those values into an instance of the provided row class (in this case User)
            table.addRow("Monkey D. Luffy", 2.0f, 19);
            // Add row using a prebuilt row
            table.addRow(new User("Roronoa Zoro", 1.8f, 20));

            println("Table Size:");
            println(table.getNumRows(), table.getNumColumns());
            println();

            println("Names: ");
            // Get all the values in a column
            println(table.getColumn("name"));
            println();

            // Find the first user with name 'Monkey D. Luffy'. Returns null if not found
            User user = table.find((User u) -> u.name.equals("Monkey D. Luffy"));
            // Inherited from Table.Row
            user.forEachColumn(Example::println);
            println();

            println("Tall People: ");
            // Find all users with a height > 2.0
            println(table.findAll((User u) -> u.height > 2.0f));

            // Sort the table in descending order according to height
            table.sort(Sort.Descending("height"));

            // Save this table to a new file
            table.saveCSV("new_users.csv");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```
