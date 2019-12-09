package logic;

import model.JumpState;
import model.Unit;

import java.util.Objects;

public class UnitState {
    public final Point position;
    public final double remainingJumpTime;
    public final boolean canJump;
    public final boolean canCancel;

    public UnitState(Point position, double remainingJumpTime) {
        this.position = position;
        this.remainingJumpTime = remainingJumpTime;
        this.canJump = false;
        this.canCancel = false;
    }

    public UnitState(Point position, double remainingJumpTime, boolean canJump, boolean canCancel) {
        this.position = position;
        this.remainingJumpTime = remainingJumpTime;
        this.canJump = canJump;
        this.canCancel = canCancel;
    }

    public UnitState(Unit me) {
        position = new Point(me);
        JumpState jumpState = me.getJumpState();
        this.remainingJumpTime = jumpState.getMaxTime();
        this.canJump = jumpState.isCanJump();
        this.canCancel = jumpState.isCanCancel();
    }

    @Override
    public String toString() {
        return String.format(
                "new UnitState(new Point(%s, %s), %s, %s, %s)",
                position.x, position.y, remainingJumpTime, canJump, canCancel
        );
    }

    @Override
    public boolean equals(Object o) {
        UnitState unitState = (UnitState) o;
        return Double.compare(unitState.remainingJumpTime, remainingJumpTime) == 0 &&
                Objects.equals(position, unitState.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, remainingJumpTime);
    }
}
