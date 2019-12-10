package logic;

import java.util.Objects;

public class MoveAction {
    public final double speed;
    public final boolean jump;
    public final boolean jumpDown;

    public MoveAction(double speed, boolean jump, boolean jumpDown) {
        if (jump && jumpDown) {
            throw new RuntimeException();
        }
        this.speed = speed;
        this.jump = jump;
        this.jumpDown = jumpDown;
    }

    @Override
    public String toString() {
        return String.format(
                "new MoveAction(%s,%s,%s)",
                speed, jump, jumpDown
        );
    }

    @Override
    public boolean equals(Object o) {
        MoveAction that = (MoveAction) o;
        return Double.compare(that.speed, speed) == 0 &&
                jump == that.jump &&
                jumpDown == that.jumpDown;
    }

    @Override
    public int hashCode() {
        return Objects.hash(speed, jump, jumpDown);
    }
}
