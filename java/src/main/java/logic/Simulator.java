package logic;

import model.Bullet;
import model.Tile;

import java.util.ArrayList;
import java.util.List;

import static logic.Utils.*;
import static model.Tile.*;

public class Simulator {
    public static double SPEED = 1.0 / 6;
    public static double WIDTH = 0.9;
    public static double HEIGHT = 1.8;
    public static final double WEIRD_SHIFT = SPEED / 100;
    static int JUMP_TICKS = 32;

    public List<UnitState> simulate(UnitState state, Tile[][] map, List<MoveAction> moves) {
        UnitState curState = state;
        List<UnitState> r = new ArrayList<>();
        int tick = 0;
        for (MoveAction move : moves) {
            tick++;
            double curY = curState.position.y;
            double curX = curState.position.x;
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
                } else if (move.jump && remainingJumpTime > 0) {
                    remainingJumpTime = 0;
                    curY -= SPEED - 4 * WEIRD_SHIFT;
                } else {
                    remainingJumpTime = 0;
                    curY -= SPEED;
                }
            }
            if (collidesWithWall(map, curX, curY)) { // todo rework
                curY = curState.position.y;
            }
            curX += move.speed;
            if (collidesWithWall(map, curX + move.speed, curY)) {// todo rework
                curX = curState.position.x;
            }
            curState = new UnitState(new Point(curX, curY), remainingJumpTime);
            r.add(curState);
        }
        return r;
    }

    private boolean collidesWithWall(Tile[][] map, double x, double y) {
        return tileAtPoint(map, x + WIDTH / 2, y) == WALL ||
                tileAtPoint(map, x - WIDTH / 2, y) == WALL ||
                tileAtPoint(map, x + WIDTH / 2, y + HEIGHT) == WALL ||
                tileAtPoint(map, x - WIDTH / 2, y + HEIGHT) == WALL;
    }

    private boolean isStanding(Point p, Tile[][] map) {
        int x = (int) p.x;
        int y = (int) p.y;
        if (y == 0) { // hack
            return true;
        }
        if (y >= map[0].length) { // todo уже давно вылез за потолок
            return false;
        }
        Tile below = map[x][y - 1];
        return (below == PLATFORM || below == WALL) && Math.abs(p.y - (int) p.y) < 0.01;
    }

    public List<Point> simulateBullet(Bullet bullet, int ticks) {
        List<Point> r = new ArrayList<>();
        Point curPos = new Point(bullet.getPosition());
        Point speed = new Point(
                fromApiSpeed(bullet.getVelocity().getX()),
                fromApiSpeed(bullet.getVelocity().getY())
        );
        for (int i = 0; i < ticks; i++) {
            curPos = curPos.add(speed);
            r.add(curPos);
        }
        return r;
    }
}
