public class Exceptions {

    public static class Parse extends Exception {
        public Parse(String message) {
            super(message);
        }
    }

    public static class InvalidColumn extends Parse {
        public InvalidColumn(String message) {
            super(message);
        }
    }

}
