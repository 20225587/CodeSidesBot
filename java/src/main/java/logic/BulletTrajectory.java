package logic;

import java.util.List;

public class BulletTrajectory {
    public final List<Point> positions;
    public final Point collisionPos;
    public final double bulletSize;

    public BulletTrajectory(List<Point> positions, Point collisionPos, double bulletSize) {
        this.positions = positions;
        this.collisionPos = collisionPos;
        this.bulletSize = bulletSize;
    }

    public int size() {
        return positions.size();
    }

    public Point get(int i) {
        return positions.get(i);
    }
}
