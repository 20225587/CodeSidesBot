package logic;

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
}
