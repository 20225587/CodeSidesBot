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
            double newX = curState.position.x;
            double newY = curState.position.y;
            double remainingJumpTime = curState.remainingJumpTime;
            boolean wasStanding = unitIsStanding(curState.position);

            newX += move.speed;
            if (unitCollidesWithWall(map, newX, newY)) {
                if (move.speed > 0) {
                    newX = (int) (newX + WIDTH / 2) - WIDTH / 2;
                } else {
                    newX = (int) (newX - WIDTH / 2) + 1 + WIDTH / 2;
                }
            }

            if (wasStanding) {
                if (move.jump) {
                    newY += SPEED;
                    remainingJumpTime = JUMP_TICKS / 60.0;
                } else {
                    if (!unitIsStanding(new Point(newX, newY))) {
                        double delta;
                        if (tileAtPoint(map, curState.position.x, curState.position.y - 1) == LADDER) {
                            if (move.speed > 0) {
                                delta = newX % 1;
                            } else {
                                delta = 1 - newX % 1;
                            }
                        } else {
                            if (move.speed > 0) {
                                delta = (newX - WIDTH / 2) % 1;
                            } else {
                                double rightBorder = newX + WIDTH / 2;
                                delta = (1 - rightBorder % 1);
                            }
                        }
                        double fallingTime = delta / abs(move.speed);
                        newY -= fallingTime * SPEED;
                    }
                }
            } else {
                if (move.jump && remainingJumpTime * 60.0 > 1) {
                    remainingJumpTime -= 1.0 / 60;
                    newY += SPEED;
                } else if (move.jump && remainingJumpTime > 0) {
                    double up = remainingJumpTime * 60 * SPEED;
                    double down = (1.0 / 60 - remainingJumpTime) * 60 * SPEED;
                    int afkMicroTicks = 2;
                    newY += up - down + afkMicroTicks / 100.0 * SPEED;
                    remainingJumpTime = 0;
                } else {
                    remainingJumpTime = 0;
                    newY -= SPEED;
                }
            }
            if (unitCollidesWithWall(map, newX, newY)) { // todo rework
                newY = curState.position.y;
            }
            curState = new UnitState(new Point(newX, newY), remainingJumpTime);
            r.add(curState);
            tick++;
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
