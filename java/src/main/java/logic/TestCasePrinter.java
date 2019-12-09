package logic;

import model.Game;
import model.Tile;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static model.Tile.*;

public class TestCasePrinter {
    public static void print(Tile[][] map, Plan plan, List<UnitState> states, Game game) {
        PrintWriter testOut;
        try {
            testOut = new PrintWriter(new FileWriter("test.txt"), true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        testOut.println(String.format("@Test\n" +
                "    void test() {\n" +
                "        UnitState start = %s;", states.get(0)));
        testOut.println(String.format("Tile[][] map = new Tile[][]%s;", mapToString(map)));
        testOut.println(
                String.format("Simulator simulator = new Simulator(map,(int)%s,%s);",
                        game.getProperties().getTicksPerSecond(),
                        game.getProperties().getUpdatesPerTick()
                ));
        testOut.println("List<UnitState> expected = Arrays.asList(");
        testOut.println(
                states.subList(1, states.size()).stream()
                        .map(UnitState::toString)
                        .collect(Collectors.joining(",\n"))
        );
        testOut.println(");");
        testOut.println(String.format("Plan plan = %s;", plan));
        testOut.println("        List<UnitState> actual = simulator.simulate(start, plan);\n" +
                "        assertEquals(actual, expected);\n" +
                "    }");
    }

    private static String mapToString(Tile[][] map) {
        return Arrays.deepToString(map).replaceAll("\\[", "{").replaceAll("]", "}");
    }

    public static void main(String[] args) {
        genStressTestMap();
    }

    private static void genStressTestMap() {
        int n = 40;
        int m = 30;
        Random rnd = new Random();
        Tile[][] map = new Tile[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (i == 0 || j == 0 || i == n - 1 || j == m - 1) {
                    map[i][j] = WALL;
                } else {
                    map[i][j] = Tile.values()[rnd.nextInt(values().length)];
                }
            }
        }
        int startX = 15, startY = 15;
        map[startX][startY + 1] = EMPTY;
        Utils.printMap(map, new Point(startX, startY));
    }
}
