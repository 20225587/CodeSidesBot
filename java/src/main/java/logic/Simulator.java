package logic;

import model.Bullet;
import model.Tile;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;
import static logic.Utils.*;
import static model.Tile.*;

public class Simulator {
    public static final double SPEED = 1.0 / 6;
    public static final double WIDTH = 0.9;
    public static final double HEIGHT = 1.8;

    private static final int TICKS_PER_SECOND = 60;
    private static final int MICROTICKS_PER_TICK = 100;
    private static final double MICROTICK_DURATION = 1.0 / TICKS_PER_SECOND / MICROTICKS_PER_TICK;
    private static final double JUMP_DURATION = 0.55;

    private final Tile[][] map;

    public Simulator(Tile[][] map) {
        this.map = map;
    }

    public List<UnitState> simulate(UnitState state, Plan plan) {
        UnitState curState = state;
        List<UnitState> r = new ArrayList<>();
        int tick = 0;
        for (MoveAction move : plan.moves) {
            for (int microtick = 0; microtick < MICROTICKS_PER_TICK; microtick++) {
                double newX = curState.position.x;
                double newY = curState.position.y;
                double remainingJumpTime = curState.remainingJumpTime;

                newX += move.speed / MICROTICKS_PER_TICK;
                if (unitCollidesWithWall(map, newX, newY)) {
                    if (move.speed > 0) {
                        newX = (int) (newX + WIDTH / 2) - WIDTH / 2;
                    } else {
                        newX = (int) (newX - WIDTH / 2) + 1 + WIDTH / 2;
                    }
                }

                boolean canMoveDown = !unitIsStandingOnWall(newX, newY);
                boolean willBeStanding = canJump(new Point(newX, newY));
                boolean onLadder = onLadder(newX, newY);

                if (move.jumpDown && canMoveDown) {
                    newY -= SPEED / MICROTICKS_PER_TICK;
                    remainingJumpTime = 0;
                } else {
                    if (willBeStanding && remainingJumpTime == 0 || onLadder) {
                        if (move.jump) {
                            newY += SPEED / MICROTICKS_PER_TICK;
                            remainingJumpTime = JUMP_DURATION - MICROTICK_DURATION;
                            if (onLadder(newX, newY)) {
                                remainingJumpTime = 0;
                            }
                        }
                    } else {
                        if (move.jump && remainingJumpTime > 0) {
                            remainingJumpTime -= MICROTICK_DURATION;
                            newY += SPEED / MICROTICKS_PER_TICK;
                        } else {
                            newY -= SPEED / MICROTICKS_PER_TICK;
                            remainingJumpTime = 0;
                        }
                    }
                }
                if (unitCollidesWithWall(map, newX, newY)) {
                    if (newY < curState.position.y) {
                        newY = (int) newY + 1;
                    } else {
                        newY = (int) (newY + HEIGHT) - HEIGHT;
                    }
                }
                curState = new UnitState(new Point(newX, newY), remainingJumpTime);
            }
            r.add(curState);
            tick++;
        }
        return r;
    }

    private boolean onLadder(double x, double y) {
        return tileAtPoint(map, x, y) == LADDER || tileAtPoint(map, x, y + HEIGHT / 2) == LADDER;
    }

    private boolean canJump(Point p) {
        return pointIsStanding(p.x - WIDTH / 2, p.y, false)
                || pointIsStanding(p.x + WIDTH / 2, p.y, false)
                || pointIsStanding(p.x, p.y, true);
    }

    private boolean unitIsStandingOnWall(double px, double py) {
        return pointIsStandingOnWall(px - WIDTH / 2, py) && pointIsStandingOnWall(px + WIDTH / 2, py);
    }

    private boolean pointIsStandingOnWall(double px, double py) {
        int x = (int) px;
        int y = (int) py;
        Tile below = map[x][y - 1];
        return (below == WALL) && abs(py - (int) py) < 1e-9;
    }

    private boolean pointIsStanding(double px, double py, boolean allowLadder) {
        int x = (int) px;
        int y = (int) py;
        Tile below = map[x][y - 1];
        return (below == PLATFORM || below == WALL || allowLadder && below == LADDER) && abs(py - (int) py) < 1e-8;
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
