package logic;

import model.Unit;

public class MissingFlags {
    public final boolean onGround, onLadder, stand, walkedRight;

    public MissingFlags(Unit me) {
        onGround = me.isOnGround();
        onLadder = me.isOnLadder();
        stand = me.isStand();
        walkedRight = me.isWalkedRight();
    }

    @Override
    public String toString() {
        return "MissingFlags{" +
                "onGround=" + onGround +
                ", onLadder=" + onLadder +
                ", stand=" + stand +
                ", walkedRight=" + walkedRight +
                '}';
    }
}
