package logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Plan {
    public final List<MoveAction> moves = new ArrayList<>();
    public final List<HistoryRecord> history = new ArrayList<>();

    public static Plan plan(int n, MoveAction move) {
        return new Plan().add(n, move);
    }

    public static Plan plan(int n, double speed, boolean jump, boolean jumpDown) {
        return new Plan().add(n, new MoveAction(speed, jump, jumpDown));
    }

    public Plan add(int n, MoveAction move) {
        moves.addAll(Collections.nCopies(n, move));
        if (n != 0) {
            history.add(new HistoryRecord(n, move));
        }
        return this;
    }

    public Plan add(int n, double speed, boolean jump, boolean jumpDown) {
        return add(n, new MoveAction(speed, jump, jumpDown));
    }

    public MoveAction get(int index) {
        return moves.get(index);
    }

    public Plan followUpPlan(MoveAction move) {
        Plan r = new Plan();
        r.moves.addAll(moves);
        r.moves.remove(0);

        r.history.addAll(history);
        HistoryRecord firstHr = history.get(0);
        if (firstHr.n == 1) {
            r.history.remove(0);
        } else {
            r.history.set(0, new HistoryRecord(firstHr.n - 1, firstHr.move));
        }
        r.add(1, move);
        return r;
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

    @Override
    public boolean equals(Object o) {
        Plan plan = (Plan) o;
        return moves.equals(plan.moves);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moves);
    }
}
