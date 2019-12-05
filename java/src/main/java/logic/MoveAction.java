package logic;

public class MoveAction {
    public final double speed;
    public final boolean jump;
    public final boolean jumpDown;

    public MoveAction(double speed, boolean jump, boolean jumpDown) {
        this.speed = speed;
        this.jump = jump;
        this.jumpDown = jumpDown;
    }
}
