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
            table.loadCSV("users.csv");

            table.addRow("Monkey D. Luffy", 2.0f, 19);
            table.addRow(new User("Roronoa Zoro", 1.8f, 20));

            println("Table Size:");
            println(table.getNumRows(), table.getNumColumns());
            println();

            println("Names: ");
            println(table.getColumn("name"));
            println();

            User user = table.find((User u) -> u.name.equals("Monkey D. Luffy"));
            // Inherited from Table.Row
            user.forEachColumn(Example::println);
            println();

            println("Tall People: ");
            println(table.findAll((User u) -> u.height > 2.0f));

            table.sort(Sort.Descending("height"));

            table.saveCSV("new_users.csv");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```
