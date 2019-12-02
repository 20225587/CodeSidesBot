package logic;

import model.Unit;
import model.Vec2Double;

import static java.lang.Math.*;

public class Utils {
    public static double dist(Point a, Unit b) {
        return dist(a.x, a.y, b.getPosition().getX(), b.getPosition().getY());
    }

    public static double dist(Unit me, Unit enemy) {
        return dist(me.getPosition(), enemy.getPosition());
    }

    public static double dist(Vec2Double a, Vec2Double b) {
        return dist(a.getX(), a.getY(), b.getX(), b.getY());
    }

    public static double dist(double x1, double y1, double x2, double y2) {
        return sqrt((sqr(x1 - x2) + sqr(y1 - y2)));
    }

    public static double dist(Vec2Double a, Unit b) {
        return dist(a.getX(), a.getY(), b.getPosition().getX(), b.getPosition().getY());
    }

    public static double dist(Unit a, Point b) {
        return dist(a.getPosition(), b);
    }

    public static double dist(Vec2Double a, Point b) {
        return dist(a.getX(), a.getY(), b.x, b.y);
    }

    public static double sqr(double x) {
        return x * x;
    }

    public static double dist(Point a, Point b) {
        return dist(a.x, a.y, b.x, b.y);
    }
}
