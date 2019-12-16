import java.io.IOException;

public class MyRunner {
    public static void main(String[] args) throws IOException {
        new Runner("127.0.0.1", 31001, "0000000000000000", new MyStrategy(false, true)).run();
    }
}
