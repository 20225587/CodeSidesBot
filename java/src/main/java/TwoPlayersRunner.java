import java.io.IOException;

public class TwoPlayersRunner {
    public static void main(String[] args) throws IOException {
        new Thread(() -> {
            try {
                MyStrategy testStrategy = new MyStrategy(true);
                new Runner("127.0.0.1", 31002, "00000000000000001", testStrategy).run();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
        new Runner("127.0.0.1", 31001, "0000000000000000").run();
    }
}
