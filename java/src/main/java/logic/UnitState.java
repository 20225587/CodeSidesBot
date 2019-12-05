package logic;

import model.Unit;

public class UnitState {
    public final Point position;
    final double remainingJumpTime;

    public UnitState(Point position, double remainingJumpTime) {
        this.position = position;
        this.remainingJumpTime = remainingJumpTime;
    }

    public UnitState(Unit me) {
        position = new Point(me);
        remainingJumpTime = me.getJumpState().getMaxTime();
    }

    @Override
    public String toString() {
        return String.format("new UnitState(new Point(%s, %s), %s)", position.x,position.y, remainingJumpTime);
    }
}
