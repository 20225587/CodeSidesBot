package logic;

import model.Bullet;
import model.Tile;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;
import static logic.Utils.*;
import static model.Tile.*;

public class Simulator {
    public final static double SPEED = 10;
    public final static double WIDTH = 0.9;
    public final static double HEIGHT = 1.8;
    private final static double EPS = 1e-9;

    private static final double JUMP_DURATION = 0.55;
    private static final double JUMP_PAD_DURATION = 0.525;

    private final int ticksPerSecond;
    public final int microticksPerTick;
    private final double microtickDuration;
    private final double microtickSpeed;
    public final double tickSpeed;

    private final Tile[][] map;

    public Simulator(Tile[][] map, int ticksPerSecond, int microticksPerTick) {
        this.map = map;
        this.ticksPerSecond = ticksPerSecond;
        this.microticksPerTick = microticksPerTick;
        microtickDuration = 1.0 / ticksPerSecond / microticksPerTick;
        microtickSpeed = SPEED / ticksPerSecond / microticksPerTick;
        tickSpeed = microtickSpeed * microticksPerTick;
    }

    public static Simulator real(Tile[][] map) {
        return new Simulator(map, 60, 100);
    }

    public static Simulator forTesting(Tile[][] map) {
        return new Simulator(map, 6000, 1);
    }

    public List<UnitState> simulate(UnitState startState, Plan plan) {
        UnitState curState = startState;
        List<UnitState> r = new ArrayList<>();
        int tick = 0;
        for (MoveAction move : plan.moves) {
            for (int microtick = 0; microtick < microticksPerTick; microtick++) {
                double newX = curState.position.x;
                double newY = curState.position.y;
                double remainingJumpTime = curState.remainingJumpTime;
                boolean canJump = curState.canJump;
                boolean canCancel = curState.canCancel;

                newX += move.speed * microtickDuration;
                if (unitCollidesWithWall(map, newX, newY)) {
                    if (move.speed > 0) {
                        newX = max(curState.position.x, (int) (newX + WIDTH / 2) - WIDTH / 2 - EPS);
                    } else {
                        newX = min(curState.position.x, (int) (newX - WIDTH / 2) + 1 + WIDTH / 2 + EPS);
                    }
                }


                boolean canMoveDown = !unitIsStandingOnWall(newX, newY);
                boolean wasOnLadder = onLadder(curState.position.x, curState.position.y);
                boolean wasOnGround = isStanding(newX, newY) && canJump && !wasOnLadder;

                if (canJump && !canCancel) {
                    newY += microtickSpeed * 2;
                    remainingJumpTime -= microtickDuration;
                } else if (canMoveDown && move.jumpDown) {
                    newY -= microtickSpeed;
                } else if (canJump && move.jump) {
                    newY += microtickSpeed;
                    remainingJumpTime -= microtickDuration;
                } else if (!wasOnGround && !wasOnLadder) {
                    newY -= microtickSpeed;
                    remainingJumpTime = 0;
                    canJump = false;
                    canCancel = false;
                } else {
                    remainingJumpTime = 0;
                    canJump = false;
                    canCancel = false;
                }

                boolean onGround = wasOnGround && isStanding(newX, newY);

                if (unitCollidesWithWall(map, newX, newY) || platformOrLadderCollision(map, newX, newY, curState, move)) {
                    if (newY > curState.position.y) {
                        newY = max(curState.position.y, (int) (newY + HEIGHT) - HEIGHT - EPS);
                        canJump = false;
                        canCancel = false;
                        remainingJumpTime = 0;
                    } else {
                        newY = min(curState.position.y, (int) newY + 1 + EPS);
                        onGround = true;
                    }
                }

                if (onLadder(newX, newY)) {
                    canJump = true;
                    canCancel = true;
                    remainingJumpTime = JUMP_DURATION;
                } else if (unitCollidesWith(map, newX, newY, JUMP_PAD)) {
                    remainingJumpTime = JUMP_PAD_DURATION;
                    canJump = true;
                    canCancel = false;
                } else if (onGround) {
                    canJump = true;
                    canCancel = true;
                    remainingJumpTime = JUMP_DURATION;
                } else if (remainingJumpTime <= 0 || canCancel && move.jumpDown) {
                    canJump = false;
                    canCancel = false;
                    remainingJumpTime = 0;
                }

                curState = new UnitState(new Point(newX, newY), remainingJumpTime, canJump, canCancel);
            }
            r.add(curState);
            tick++;
        }
        return r;
    }

    private boolean unitOnPlatform(double px, double py) {
        return pointOnPlatform(px - WIDTH / 2, py)
                || pointOnPlatform(px + WIDTH / 2, py);
    }

    private boolean pointOnPlatform(double px, double py) {
        int x = (int) px;
        int y = (int) py;
        Tile below = map[x][y - 1];
        return below == PLATFORM && abs(py - (int) py) < 1e-8;
    }

    private boolean platformOrLadderCollision(Tile[][] map, double newX, double newY, UnitState curState, MoveAction move) {
        if (move.jumpDown) {
            return false;
        }
        if ((int) newY >= (int) curState.position.y) {
            return false;
        }
        return tileAtPoint(map, newX - WIDTH / 2, newY) == PLATFORM
                || tileAtPoint(map, newX + WIDTH / 2, newY) == PLATFORM
                || tileAtPoint(map, newX, newY) == LADDER;
    }

    private boolean onLadder(double x, double y) {
        return tileAtPoint(map, x, y) == LADDER || tileAtPoint(map, x, y + HEIGHT / 2) == LADDER;
    }

    public double clampSpeed(double speed) {
        if (speed > SPEED) {
            return SPEED;
        }
        if (speed < -SPEED) {
            return -SPEED;
        }
        return speed;
    }

    private boolean isStanding(double px, double py) {
        return pointIsStanding(px - WIDTH / 2, py, false)
                || pointIsStanding(px + WIDTH / 2, py, false)
                || pointIsStanding(px, py, true);
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
        if (y == 0) {
            return true;
        }
        Tile below = map[x][y - 1];
        return (below == PLATFORM || below == WALL || allowLadder && below == LADDER) && abs(py - (int) py) < 1e-8;
    }

    public double toTickSpeed(double speed) {
        return speed / ticksPerSecond;
    }

    public double fromTickSpeed(double speed) {
        return speed * ticksPerSecond;
    }

    public List<Point> simulateBullet(Bullet bullet, int ticks) {
        List<Point> r = new ArrayList<>();
        Point curPos = new Point(bullet.getPosition());
        Point speed = new Point(
                toTickSpeed(bullet.getVelocity().getX()),
                toTickSpeed(bullet.getVelocity().getY())
        );
        for (int i = 0; i < ticks; i++) {
            curPos = curPos.add(speed);
            r.add(curPos);
        }
        return r;
    }
}
