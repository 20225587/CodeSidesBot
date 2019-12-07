package logic;

import model.Bullet;
import model.Tile;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;
import static logic.Utils.*;
import static model.Tile.*;

public class Simulator {
    public static double SPEED = 1.0 / 6;
    public static double WIDTH = 0.9;
    public static double HEIGHT = 1.8;
    public static final double WEIRD_SHIFT = SPEED / 100;
    static int JUMP_TICKS = 32;

    private final Tile[][] map;

    public Simulator(Tile[][] map) {
        this.map = map;
    }

    public List<UnitState> simulate(UnitState state, Plan plan) {
        UnitState curState = state;
        List<UnitState> r = new ArrayList<>();
        int tick = 0;
        for (MoveAction move : plan.moves) {
            tick++;
            double curY = curState.position.y;
            double curX = curState.position.x;
            double remainingJumpTime = curState.remainingJumpTime;
            boolean wasStanding = unitIsStanding(curState.position);

            curX += move.speed;
            if (unitCollidesWithWall(map, curX, curY)) {
                if (move.speed > 0) {
                    curX = (int) (curX + WIDTH / 2) - WIDTH / 2;
                } else {
                    curX = (int) (curX - WIDTH / 2) + 1 + WIDTH / 2;
                }
            }

            if (wasStanding) {
                if (move.jump) {
                    curY += SPEED - WEIRD_SHIFT;
                    remainingJumpTime = (JUMP_TICKS + 0.01) / 60.0;
                } else {
                    if (!unitIsStanding(new Point(curX, curY))) {
                        double delta;
                        if (tileAtPoint(map, curState.position.x, curState.position.y - 1) == LADDER) {
                            if (move.speed > 0) {
                                delta = curX % 1;
                            } else {
                                delta = 1 - curX % 1;
                            }
                        } else {
                            if (move.speed > 0) {
                                delta = (curX - WIDTH / 2) % 1;
                            } else {
                                double rightBorder = curX + WIDTH / 2;
                                delta = (1 - rightBorder % 1);
                            }
                        }
                        double fallingTime = delta / abs(move.speed);
                        curY -= fallingTime * SPEED;
                    }
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
            if (unitCollidesWithWall(map, curX, curY)) { // todo rework
                curY = curState.position.y;
            }
            curState = new UnitState(new Point(curX, curY), remainingJumpTime);
            r.add(curState);
        }
        return r;
    }

    private boolean unitIsStanding(Point p) {
        return pointIsStanding(p.x - WIDTH / 2, p.y, false)
                || pointIsStanding(p.x + WIDTH / 2, p.y, false)
                || pointIsStanding(p.x, p.y, true);
    }

    private boolean pointIsStanding(double px, double py, boolean allowLadder) {
        int x = (int) px;
        int y = (int) py;
        if (y == 0) { // hack
            return true;
        }
        if (y >= map[0].length) { // todo уже давно вылез за потолок
            return false;
        }
        Tile below = map[x][y - 1];
        return (below == PLATFORM || below == WALL || allowLadder && below == LADDER) && abs(py - (int) py) < 0.01;
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
