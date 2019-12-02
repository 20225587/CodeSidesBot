package logic;

import model.Unit;
import model.Vec2Double;
import model.Vec2Float;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

public class Point {
    public final double x, y;

    public Point(double x, double y) {
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

    public Point add(Point p) {
        return new Point(x + p.x, y + p.y);
    }

    public Vec2Float toV2F() {
        return new Vec2Float((float) x, (float) y);
    }

    public Point mult(double v) {
        return new Point(x * v, y * v);
    }

    public Point minus(Point a) {
        return new Point(x - a.x, y - a.y);
    }
}
