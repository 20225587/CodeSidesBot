package logic;

import model.Game;
import model.Tile;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TestCasePrinter {
    public static void print(Tile[][] map, Plan plan, List<UnitState> states, Game game) {
        System.out.println(String.format("@Test\n" +
                "    void test() {\n" +
                "        UnitState start = %s;", states.get(0)));
        System.out.println(String.format("Tile[][] map = new Tile[][]%s;", mapToString(map)));
        System.out.println(
                String.format("Simulator simulator = new Simulator(map,(int)%s,%s);",
                        game.getProperties().getTicksPerSecond(),
                        game.getProperties().getUpdatesPerTick()
                ));
        System.out.println("List<UnitState> expected = Arrays.asList(");
        System.out.println(
                states.subList(1, states.size()).stream()
                        .map(UnitState::toString)
                        .collect(Collectors.joining(",\n"))
        );
        System.out.println(");");
        System.out.println(String.format("Plan plan = %s;", plan));
        System.out.println("        List<UnitState> actual = simulator.simulate(start, plan);\n" +
                "        assertEquals(actual, expected);\n" +
                "    }");
    }

    private static String mapToString(Tile[][] map) {
        return Arrays.deepToString(map).replaceAll("\\[", "{").replaceAll("]", "}");
    }
}
