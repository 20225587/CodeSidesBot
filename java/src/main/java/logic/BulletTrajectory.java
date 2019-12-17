package logic;

import java.util.List;

public class BulletTrajectory {
    public final List<Point> positions;
    public final Point collisionPos;

    public BulletTrajectory(List<Point> positions, Point collisionPos) {
        this.positions = positions;
        this.collisionPos = collisionPos;
    }

    public int size() {
        return positions.size();
    }

    public Point get(int i) {
        return positions.get(i);
    }
}
