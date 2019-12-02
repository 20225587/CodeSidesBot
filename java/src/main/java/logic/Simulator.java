package logic;

import model.Tile;

import java.util.ArrayList;
import java.util.List;

import static model.Tile.*;

public class Simulator {
    static double SPEED = 1.0 / 6;
    public static final double WEIRD_SHIFT = SPEED / 100;
    static int JUMP_TICKS = 32;

    public List<UnitState> simulate(UnitState state, Tile[][] map, List<MoveAction> moves) {
        UnitState curState = state;
        List<UnitState> r = new ArrayList<>();
        int tick = 0;
        for (MoveAction move : moves) {
            tick++;
            double curY = curState.position.y;
            double remainingJumpTime = curState.remainingJumpTime;
            boolean standing = isStanding(curState.position, map);
            if (standing) {
                if (move.jump) {
                    curY += SPEED - WEIRD_SHIFT;
                    remainingJumpTime = (JUMP_TICKS + 0.01) / 60.0;
                }
            } else {
                if (move.jump && remainingJumpTime * 60.0 >= 1) {
                    remainingJumpTime -= 1.0 / 60;
                    curY += SPEED;
                } else if (remainingJumpTime > 0) {
                    remainingJumpTime = 0;
                    curY -= SPEED - 4 * WEIRD_SHIFT;
                } else {
                    remainingJumpTime = 0;
                    curY -= SPEED;
                }
            }
            curState = new UnitState(new Point(curState.position.x, curY), remainingJumpTime);
            r.add(curState);
        }
        return r;
    }

    private boolean isStanding(Point p, Tile[][] map) {
        int x = (int) p.x;
        int y = (int) p.y;
        if (y == 0) { // hack
            return true;
        }
        Tile below = map[x][y - 1];
        return (below == PLATFORM || below == WALL) && Math.abs(p.y - (int) p.y) < 0.01;
    }
}
