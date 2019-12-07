package logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Plan {
    public final List<MoveAction> moves = new ArrayList<>();

    public static Plan plan(int n, MoveAction move) {
        return new Plan().add(n, move);
    }

    public Plan add(int n, MoveAction move) {
        moves.addAll(Collections.nCopies(n, move));
        return this;
    }

    public MoveAction get(int index) {
        return moves.get(index);
    }
}
