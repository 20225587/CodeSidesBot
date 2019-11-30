import model.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static model.Tile.*;

public class MyStrategy {

    Unit me;
    Game game;
    MyDebug debug;
    Tile[][] map;
    public static final ColorFloat BLACK = new ColorFloat(0, 0, 0, 1);
    public static final ColorFloat RED = new ColorFloat(1, 0, 0, 1);
    public static final ColorFloat WHITE = new ColorFloat(1, 1, 1, 1);

    public UnitAction getAction(Unit me, Game game, Debug debug0) {
        this.me = me;
        this.game = game;
        this.debug = new MyDebug(debug0);
        this.map = game.getLevel().getTiles();

        //-------

        Unit enemy = chooseEnemy();
        LootBox targetBonus = chooseTargetBonus();
        Vec2Double targetPos = chooseTarget(enemy, targetBonus);
        Vec2Double aimDir = aim(enemy);
        boolean jump = shouldJump(targetPos);
        double velocity = getVelocity(targetPos);
        boolean shoot = shouldShoot(enemy);

        debug.showSpread(me);
        if (shoot) {
            debug0.draw(new CustomData.Log("shoot"));
        }

        return new UnitAction(velocity, jump, !jump, aimDir, shoot, false, false);
    }

    private boolean shouldShoot(Unit enemy) {
        Weapon weapon = me.getWeapon();
        if (weapon == null) {
            return false;
        }
        return inLineOfSight(enemy) && goodSpread(enemy, weapon);
    }

    private boolean inLineOfSight(Unit enemy) {
        Point a = muzzlePoint(me);
        Point b = muzzlePoint(enemy);
        boolean blocked = false;
        int n = 1000;
        Point delta = b.minus(a).mult(1.0 / n);
        for (int i = 0; i < n; i++) {
            Point t = a.add(delta.mult(i));
            if (inWall(t)) {
                blocked = true;
                debug.drawSquare(t, 0.1, RED);
            } else {
                debug.drawSquare(t, 0.1, WHITE);
            }
        }
        return !blocked;
    }

    private boolean inWall(Point t) {
        return map[(int) t.x][(int) t.y] == WALL;
    }

    private boolean goodSpread(Unit enemy, Weapon weapon) { // todo rework
        if (true) {
            return true;
        }
        double spread = weapon.getSpread();
        if (abs(spread - weapon.getParams().getMinSpread()) < 1e-5) {
            return true;
        }
        double d = dist(me, enemy);
        double r = max(enemy.getSize().getX(), enemy.getSize().getY()) / 2;
        double angle = atan(r / d);
        return spread <= angle;
    }

    private double dist(Point a, Unit b) {
        return dist(a.x, a.y, b.getPosition().getX(), b.getPosition().getY());
    }

    private double dist(Unit me, Unit enemy) {
        return dist(me.getPosition(), enemy.getPosition());
    }

    private double dist(Vec2Double a, Vec2Double b) {
        return dist(a.getX(), a.getY(), b.getX(), b.getY());
    }

    private double dist(double x1, double y1, double x2, double y2) {
        return sqrt((sqr(x1 - x2) + sqr(y1 - y2)));
    }

    private double sqr(double x) {
        return x * x;
    }

    private static Point muzzlePoint(Unit unit) {
        return new Point(unit).add(new Point(0, unit.getSize().getY() / 2));
    }


    private double getVelocity(Vec2Double targetPos) {
        double myX = me.getPosition().getX();
        if (abs(myX - targetPos.getX()) <= me.getSize().getX() / 8) {
            return 0;
        }
        return (targetPos.getX() > myX) ?
                game.getProperties().getUnitMaxHorizontalSpeed() :
                -game.getProperties().getUnitMaxHorizontalSpeed();
    }

    private boolean shouldJump(Vec2Double targetPos) {
        boolean jump = targetPos.getY() > me.getPosition().getY();
        if (targetPos.getX() > me.getPosition().getX() && game.getLevel()
                .getTiles()[(int) (me.getPosition().getX() + 1)][(int) (me.getPosition().getY())] == WALL) {
            jump = true;
        }
        if (targetPos.getX() < me.getPosition().getX() && game.getLevel()
                .getTiles()[(int) (me.getPosition().getX() - 1)][(int) (me.getPosition().getY())] == WALL) {
            jump = true;
        }
        return jump;
    }

    private Vec2Double aim(Unit enemy) {
        return new Vec2Double(
                enemy.getPosition().getX() - me.getPosition().getX(),
                enemy.getPosition().getY() - me.getPosition().getY()
        );
    }

    private Vec2Double chooseTarget(Unit nearestEnemy, LootBox targetBonus) {
        Vec2Double targetPos = me.getPosition();
        if (targetBonus != null && targetBonus.getItem() instanceof Item.HealthPack) {
            targetPos = targetBonus.getPosition();
        } else if (targetBonus != null && me.getWeapon() == null) {
            targetPos = targetBonus.getPosition();
        } else if (nearestEnemy != null) {
            targetPos = nearestEnemy.getPosition();
        }
        return targetPos;
    }

    private LootBox chooseTargetBonus() {
        Map<Class<? extends Item>, List<LootBox>> map = Stream.of(game.getLootBoxes())
                .collect(Collectors.groupingBy(b -> b.getItem().getClass()));
        List<LootBox> weapons = map.getOrDefault(Item.Weapon.class, Collections.emptyList());
        List<LootBox> healthPacks = map.getOrDefault(Item.HealthPack.class, Collections.emptyList());
        List<LootBox> mines = map.getOrDefault(Item.Mine.class, Collections.emptyList());

        List<LootBox> required = me.getWeapon() == null ? weapons : healthPacks;
        return required.stream()
                .min(Comparator.comparing(lb -> sqrDist(lb.getPosition(), me.getPosition())))
                .orElse(null);
    }

    private Unit chooseEnemy() {
        Unit nearestEnemy = null;
        for (Unit other : game.getUnits()) {
            if (other.getPlayerId() != me.getPlayerId()) {
                if (nearestEnemy == null || sqrDist(me.getPosition(),
                        other.getPosition()) < sqrDist(me.getPosition(), nearestEnemy.getPosition())) {
                    nearestEnemy = other;
                }
            }
        }
        return nearestEnemy;
    }

    static double sqrDist(Vec2Double a, Vec2Double b) {
        return (a.getX() - b.getX()) * (a.getX() - b.getX()) + (a.getY() - b.getY()) * (a.getY() - b.getY());
    }

    static class Point {
        public final double x, y;

        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public Point(Unit unit) {
            this(unit.getPosition());
        }

        public Point(Vec2Double p) {
            this(p.getX(), p.getY());
        }

        public static Point dir(double a) {
            return new Point(cos(a), sin(a));
        }

        Point add(Point p) {
            return new Point(x + p.x, y + p.y);
        }

        Vec2Float toV2F() {
            return new Vec2Float((float) x, (float) y);
        }

        public Point mult(double v) {
            return new Point(x * v, y * v);
        }

        public Point minus(Point a) {
            return new Point(x - a.x, y - a.y);
        }
    }

    static class MyDebug {
        final Debug debug;

        MyDebug(Debug debug) {
            this.debug = debug;
        }

        private void drawLine(Unit a, Point b) {
            drawLine(new Point(a), b);
        }

        private void drawLine(Point a, Point b) {
            debug.draw(new CustomData.Line(a.toV2F(), b.toV2F(), 0.1f, new ColorFloat(0f, 0f, 0f, 0.9f)));
        }

        private void showSpread(Unit me) {
            Weapon weapon = me.getWeapon();
            if (weapon != null) {
                double curDir = weapon.getLastAngle();
                Point muzzle = muzzlePoint(me);

                showSpread(curDir, muzzle, weapon.getSpread());
                showSpread(curDir, muzzle, weapon.getParams().getMinSpread());
            }
        }

        private void showSpread(double curDir, Point muzzle, double spread) {
            Point to1 = muzzle.add(Point.dir(curDir + spread).mult(100));
            Point to2 = muzzle.add(Point.dir(curDir - spread).mult(100));

            drawLine(muzzle, to1);
            drawLine(muzzle, to2);
        }

        public void drawSquare(Point p, double size, ColorFloat color) {
            Vec2Float pos = p.add(new Point(-size / 2, -size / 2)).toV2F();
            Vec2Float sizeV = new Vec2Float((float) size, (float) size);
            debug.draw(new CustomData.Rect(pos, sizeV, color));
        }
    }
}