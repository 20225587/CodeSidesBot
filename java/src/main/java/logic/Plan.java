package logic;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Plan {
    public final List<MoveAction> moves = new ArrayList<>();
    public final List<HistoryRecord> history = new ArrayList<>();

    public static Plan plan(int n, MoveAction move) {
        return new Plan().add(n, move);
    }

    public Plan add(int n, MoveAction move) {
        moves.addAll(Collections.nCopies(n, move));
        history.add(new HistoryRecord(n, move));
        return this;
    }

    public MoveAction get(int index) {
        return moves.get(index);
    }

    @Override
    public String toString() {
        return "new Plan()" + history.stream()
                .map(r -> String.format("\n.add(%s,%s)", r.n, r.move))
                .collect(Collectors.joining());
    }

    static class HistoryRecord {
        public final int n;
        public final MoveAction move;

        HistoryRecord(int n, MoveAction move) {
            this.n = n;
            this.move = move;
        }
    }
}
