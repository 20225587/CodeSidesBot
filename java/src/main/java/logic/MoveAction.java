package logic;

public class MoveAction {
    public final double velocity;
    public final boolean jump;
    public final boolean jumpDown;

    public MoveAction(double velocity, boolean jump, boolean jumpDown) {
        this.velocity = velocity;
        this.jump = jump;
        this.jumpDown = jumpDown;
    }
}
