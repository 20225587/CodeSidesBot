package logic;

import model.Tile;
import model.Unit;
import model.Vec2Double;

import static java.lang.Math.*;
import static model.Tile.WALL;

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

    public static double toApiSpeed(double speed) {
        return speed * 60;
    }

    public static double fromApiSpeed(double speed) {
        return speed / 60;
    }

    public static Tile tileAtPoint(Tile[][] map, double x, double y) {
        return map[(int) x][(int) y];
    }

    public static Tile tileAtPoint(Tile[][] map, Point p) {
        return tileAtPoint(map, p.x, p.y);
    }

    public static boolean unitCollidesWithWall(Tile[][] map, double x, double y) {
        return tileAtPoint(map, x + Simulator.WIDTH / 2, y) == WALL ||
                tileAtPoint(map, x - Simulator.WIDTH / 2, y) == WALL ||
                tileAtPoint(map, x + Simulator.WIDTH / 2, y + Simulator.HEIGHT) == WALL ||
                tileAtPoint(map, x - Simulator.WIDTH / 2, y + Simulator.HEIGHT) == WALL;
    }

    public static boolean bulletCollidesWithWall(Tile[][] map, Point p, double size) {
        return tileAtPoint(map, p.x - size / 2, p.y - size / 2) == WALL ||
                tileAtPoint(map, p.x - size / 2, p.y + size / 2) == WALL ||
                tileAtPoint(map, p.x + size / 2, p.y - size / 2) == WALL ||
                tileAtPoint(map, p.x + size / 2, p.y + size / 2) == WALL;
    }
}
