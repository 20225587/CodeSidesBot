import model.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.*;

public class MyStrategy {

    Unit me;
    Game game;
    MyDebug debug;

    public UnitAction getAction(Unit me, Game game, Debug debug0) {
        this.me = me;
        this.game = game;
        this.debug = new MyDebug(debug0);

        //-------

        Unit enemy = chooseEnemy();
        LootBox targetBonus = chooseTargetBonus();
        Vec2Double targetPos = chooseTarget(enemy, targetBonus);
        Vec2Double aimDir = aim(enemy);
        boolean jump = shouldJump(targetPos);
        double velocity = getVelocity(targetPos);

        Point to = new Point(me).add(Point.dir(PI / 4).mult(100));

        debug.drawLine(me, to);

        return new UnitAction(velocity, jump, !jump, aimDir, true, false, false);
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
                .getTiles()[(int) (me.getPosition().getX() + 1)][(int) (me.getPosition().getY())] == Tile.WALL) {
            jump = true;
        }
        if (targetPos.getX() < me.getPosition().getX() && game.getLevel()
                .getTiles()[(int) (me.getPosition().getX() - 1)][(int) (me.getPosition().getY())] == Tile.WALL) {
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

        public Point mult(int v) {
            return new Point(x * v, y * v);
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
    }
}