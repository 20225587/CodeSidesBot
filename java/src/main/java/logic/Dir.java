package logic;

public enum Dir {
    RIGHT(1,0), UP(0, 1), LEFT(-1, 0), DOWN(0, -1);
    public final int dx, dy;

    public static Dir[] dirs = values();

    Dir(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }
}
