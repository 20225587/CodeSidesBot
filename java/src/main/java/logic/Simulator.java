package logic;

import model.Bullet;
import model.Tile;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;
import static logic.Utils.*;
import static model.Tile.*;

public class Simulator {
    public static final double SPEED = 10;
    public static final double WIDTH = 0.9;
    public static final double HEIGHT = 1.8;

    private static final double JUMP_DURATION = 0.55;

    private final int ticksPerSecond;
    private final int microticksPerTick;
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
                        newX = (int) (newX + WIDTH / 2) - WIDTH / 2;
                    } else {
                        newX = (int) (newX - WIDTH / 2) + 1 + WIDTH / 2;
                    }
                }

                if (canJump && move.jump) {
                    newY += microtickSpeed;
                    remainingJumpTime -= microtickDuration;
                }

                boolean willBeStanding = canJump(new Point(newX, newY));

                if (willBeStanding) {
                    canJump = true;
                    canCancel = true;
                    remainingJumpTime = JUMP_DURATION;
                }

                if (unitCollidesWithWall(map, newX, newY)) {
                    if (newY < curState.position.y) {
                        newY = (int) newY + 1;
                    } else {
                        newY = (int) (newY + HEIGHT) - HEIGHT;
                    }
                }
                curState = new UnitState(new Point(newX, newY), remainingJumpTime, canJump, canCancel);
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
