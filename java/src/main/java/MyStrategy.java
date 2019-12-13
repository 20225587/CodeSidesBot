import logic.*;
import model.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static logic.Dir.*;
import static logic.Plan.plan;
import static logic.Simulator.*;
import static logic.Utils.*;
import static model.Tile.*;
import static model.WeaponType.ROCKET_LAUNCHER;

public class MyStrategy {

    public static final ColorFloat BLACK = new ColorFloat(0, 0, 0, 1);
    public static final ColorFloat RED = new ColorFloat(1, 0, 0, 1);
    public static final ColorFloat GREEN = new ColorFloat(0, 1, 0, 1);
    public static final ColorFloat BLUE = new ColorFloat(0, 0, 1, 1);
    public static final ColorFloat WHITE = new ColorFloat(1, 1, 1, 1);
    public static final double EXPLOSION_SIZE = 6;
    public static final int HEALTHPACK_THRESHOLD = 75;

    final boolean fake;
    final boolean bazookaOnly = false;

    Unit me;
    Game game;
    MyDebug debug;
    Tile[][] map;
    Simulator simulator;
    Plan lastDodgePlan = null;
    Plan lastMovementPlan = null;
    List<Point> stablePoints;

    public MyStrategy() {
        fake = false;
    }

    public MyStrategy(boolean fake) {
        this.fake = fake;
    }

    public UnitAction getAction(Unit me, Game game, Debug debug0) {
        this.me = me;
        this.game = game;
        this.debug = fake ? new MyDebugStub() : new MyDebugImpl(debug0);
        this.map = game.getLevel().getTiles();
        fixBorders(map);
        if (game.getCurrentTick() == 0) {
            simulator = new Simulator(
                    map,
                    (int) game.getProperties().getTicksPerSecond(),
                    game.getProperties().getUpdatesPerTick()
            );
            stablePoints = findStablePoints();
        }

        if (game.getUnits().length != 2) {
            return noop();
        }

//        for (Point p : stablePoints) {
//            debug.drawSquare(p, 0.05, GREEN);
//        }

        //-------

        /*if (true) {
            return testSimulation();
        }/**/
        /*if (fake) {
            return noop();
        }/**/

        Unit enemy = chooseEnemy();
        LootBox targetBonus = chooseTargetBonus(enemy);
        MoveAction moveAction = move(enemy, targetBonus);
        Vec2Double aimDir = aim(enemy);
        boolean shoot = shouldShoot(enemy);

        boolean swap = me.getWeapon() != null && me.getWeapon().getTyp() == ROCKET_LAUNCHER;
        boolean plantMine = false;

        return new UnitAction(
                moveAction.speed,
                moveAction.jump,
                moveAction.jumpDown,
                aimDir,
                shoot,
                false,
                swap,
                plantMine
        );
    }

    private UnitAction noop() {
        return new UnitAction(0, false, false, new Vec2Double(0, 0), false, false, false, false);
    }

    Plan testPlan;

    List<UnitState> actualStates = new ArrayList<>();
    List<UnitState> simulation;

    private UnitAction testSimulation() {
        if (fake) {
            return noop();
        }
        UnitState state = new UnitState(me);
        if (testPlan == null) {
            testPlan = genStressTestPlan();
            simulation = simulator.simulate(state, testPlan);
        } else {
            if (!state.equals(simulation.get(game.getCurrentTick() - 1))) {
                int x = 0;
                x++;
            }
        }

        actualStates.add(state);
        if (game.getCurrentTick() == testPlan.moves.size()) {
            TestCasePrinter.print(map, testPlan, actualStates, game);
        }
        MoveAction curAction = testPlan.get(game.getCurrentTick());
        return new UnitAction(
                curAction.speed,
                curAction.jump,
                curAction.jumpDown,
                new Vec2Double(0, 0),
                false,
                false,
                false,
                false
        );
    }

    private Plan genStressTestPlan() {
        Random rnd = new Random(34343434);
        Plan plan = new Plan();
        for (int i = 0; i < 50; i++) {
            int n = 20 + rnd.nextInt(20);
            double speed = rnd.nextDouble() * 20 - 10;
            boolean jump, jumpDown;
            int jumpType = rnd.nextInt(3);
            if (jumpType == 0) {
                jump = jumpDown = false;
            } else if (jumpType == 1) {
                jump = true;
                jumpDown = false;
            } else {
                jump = false;
                jumpDown = true;
            }
            plan.add(n, new MoveAction(speed, jump, jumpDown));
        }
        return plan;
    }

    private MoveAction move(Unit enemy, LootBox targetBonus) {
        MoveAction move = move0(enemy, targetBonus);
        /*if (!fake) {
            move = new MoveAction(0, false, false);
        } else {
            move = new MoveAction(move.speed, true, false);
        }/**/
        MoveAction dodge = tryDodgeBullets(move);
        if (dodge != null) {
            return dodge;
        }
        return move;
    }

    private MoveAction move0(Unit enemy, LootBox targetBonus) {
        Point targetPos = chooseTargetPosition(enemy, targetBonus);
        if (targetPos == null) {
            return new MoveAction(0, false, false);
        } else {
            debug.drawLine(new Point(me), targetPos, WHITE);
            Set<Plan> plans = genMovementPlans(targetPos);
            double minDist = Double.POSITIVE_INFINITY;
            UnitState start = new UnitState(me);
            Plan bestPlan = null;
            List<UnitState> bestStates = null;
            int[][] dfsDist = dfs(targetPos);
            //print(dfsDist);
            for (Plan plan : plans) {
                List<UnitState> states = simulator.simulate(start, plan);
                //debug.drawSquare(states.get(states.size() - 1).position, 0.1, BLUE);
                double dist = evalDist(states, dfsDist, targetPos);
                if (dist < minDist) {
                    minDist = dist;
                    bestStates = states;
                    bestPlan = plan;
                }
            }
            showStates(bestStates, GREEN);
            lastMovementPlan = bestPlan;
            return bestPlan.get(0);
        }
    }

    private void showStates(List<UnitState> states, ColorFloat color) {
        for (UnitState state : states) {
            debug.drawSquare(state.position, 0.1, color);
        }
    }

    private double evalDist(List<UnitState> states, int[][] dfsDist, Point target) {
        double r = Double.POSITIVE_INFINITY;
        boolean collidesWithEnemy = false;
        for (int i = 0; i < states.size(); i++) {
            UnitState state = states.get(i);
            double dist = evaluate(dfsDist, target, state) + i * simulator.tickSpeed * 0.1;
            if (collidesWithEnemy(state)) {
                collidesWithEnemy = true;
            }
            r = min(r, dist);
        }
        if (collidesWithEnemy) {
            r += 100;
        }
        return r;
    }

    private boolean collidesWithEnemy(UnitState state) {
        Point a = state.position;
        for (Unit enemy : getEnemies()) {
            Point b = new Point(enemy);
            if (intersects(new Segment(a.x - WIDTH / 2, a.x + WIDTH / 2), new Segment(b.x - WIDTH / 2, b.x + WIDTH / 2)) &&
                    intersects(new Segment(a.y, a.y + HEIGHT), new Segment(b.y, b.y + HEIGHT))) {
                return true;
            }
        }
        return false;
    }

    private List<Unit> getEnemies() {
        return Stream.of(game.getUnits())
                .filter(u -> u.getPlayerId() != me.getPlayerId())
                .collect(Collectors.toList());
    }

    private double evaluate(int[][] dfsDist, Point target, UnitState state) {
        double minDist = Double.POSITIVE_INFINITY;
        double x = state.position.x;
        double y = state.position.y;
        int cx = (int) x;
        int cy = (int) y;

        if (dfsDist[cx][cy] == 0) {
            minDist = min(minDist, max(abs(x - target.x), abs(y - target.y)));
        } else {
            for (Dir dir : dirs) {
                int toX = cx + dir.dx;
                int toY = cy + dir.dy;
                double distToNeighbour;
                if (dir == RIGHT) {
                    distToNeighbour = toX - x;
                } else if (dir == LEFT) {
                    distToNeighbour = x - cx;
                } else if (dir == UP) {
                    distToNeighbour = toY - y;
                } else if (dir == DOWN) {
                    distToNeighbour = y - cy;
                } else {
                    throw new RuntimeException();
                }
                double dist = dfsDist[toX][toY] + distToNeighbour + 1;
                minDist = min(minDist, dist);
            }
        }
        return minDist;
    }

    private void print(int[][] dfsDist) {
        for (int y = map[0].length - 1; y >= 0; y--) {
            for (int x = 0; x < map.length; x++) {
                String s;
                if (map[x][y] == WALL) {
                    s = "#";
                } else {
                    s = String.valueOf(dfsDist[x][y]);
                }
                System.out.print(s + "\t");
            }
            System.out.println();
        }
    }

    int[][] dfs(Point start) {
        int[][] dist = new int[map.length][map[0].length];
        final int inf = (int) 1e9;
        for (int i = 0; i < map.length; i++) {
            for (int j = 0; j < map[i].length; j++) {
                dist[i][j] = inf;
            }
        }
        Queue<Cell> q = new ArrayDeque<>();
        Cell startCell = new Cell(start);
        q.add(startCell);
        dist[startCell.x][startCell.y] = 0;
        while (!q.isEmpty()) {
            Cell cur = q.remove();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (abs(dx) + abs(dy) != 1) {
                        continue;
                    }
                    int toX = cur.x + dx;
                    int toY = cur.y + dy;
                    if (!inside(toX, toY)) {
                        continue;
                    }
                    if (map[toX][toY] == WALL) {
                        continue;
                    }
                    if (map[toX][toY + 1] == WALL && map[toX][toY - 1] == WALL) {
                        continue;
                    }
                    if (dist[toX][toY] != inf) {
                        continue;
                    }
                    dist[toX][toY] = dist[cur.x][cur.y] + 1;
                    q.add(new Cell(toX, toY));
                }
            }
        }
        return dist;
    }

    private boolean inside(int x, int y) {
        return x >= 0 && x < map.length && y >= 0 && y < map[0].length;
    }

    private Set<Plan> genMovementPlans(Point targetPos) {
        int steps = 64;
        Set<Plan> plans = new LinkedHashSet<>();
        addFollowUpPlans(plans, lastMovementPlan);

        double speedToTarget = simulator.clampSpeed(simulator.fromTickSpeed(targetPos.x - me.getPosition().getX()));
        plans.add(plan(1, speedToTarget, false, false).add(steps - 1, 0, false, false));

        plans.add(plan(1, 0, false, false).add(steps - 1, 0, true, false));

        for (int cnt = 0; cnt <= steps; cnt += 6) {
            for (double speed : new double[]{-SPEED, 0, SPEED}) {
                for (boolean jump : new boolean[]{false, true}) {
                    for (boolean jumpDown : new boolean[]{false, true}) {
                        if (jump && jumpDown) {
                            continue;
                        }
                        plans.add(
                                plan(cnt, new MoveAction(speed, jump, jumpDown))
                                        .add(steps - cnt, new MoveAction(0, jump, jumpDown))
                        );
                        plans.add(
                                plan(cnt, speed, false, false)
                                        .add(steps - cnt, speed, jump, jumpDown)
                        );

                        plans.add(
                                plan(cnt, speed, false, false)
                                        .add(steps - cnt, -speed, jump, jumpDown)
                        );
                        plans.add(
                                plan(cnt, 0, false, false)
                                        .add(steps - cnt, speed, jump, jumpDown)
                        );
                        plans.add(
                                plan(cnt, 0, true, false)
                                        .add(steps - cnt, speed, jump, jumpDown)
                        );
                    }
                }
            }
        }
        verifyLength(plans, steps);
        return plans;
    }

    private void verifyLength(Set<Plan> plans, int steps) {
        if (!plans.stream().allMatch(p -> p.moves.size() == steps)) {
            throw new RuntimeException("wrong plan length");
        }
    }

    Random rnd = new Random(12);
    Point target;

    private Point chooseTargetPosition(Unit enemy, LootBox targetBonus) {
        /*if (true) {
            while (!goodTarget(target)) {
                target = new Point(
                        rnd.nextInt(map.length - 2) + 1.5,
                        rnd.nextInt(map[0].length - 2) + 1.5
                );
            }
            return target;
        }/**/
        Point targetPos;
        if (shouldGoToHealthPack(targetBonus)) {
            targetPos = heathPackTargetPoint(targetBonus);
        } else if (targetBonus != null && targetBonus.getItem() instanceof Item.Weapon) {
            targetPos = new Point(targetBonus.getPosition());
        } else {
            targetPos = findShootingPosition(enemy);
        }
        return targetPos;
    }

    private boolean goodTarget(Point target) {
        if (target == null) return false;
        if ((int) me.getPosition().getX() == (int) target.x
                && ((int) me.getPosition().getY() == (int) target.y)) {
            return false;
        }
        if (tileAtPoint(target.x, target.y + 1) == WALL) {
            return false;
        }
        if (tileAtPoint(target) == WALL) {
            return false;
        }
        return true;
    }

    private boolean shouldGoToHealthPack(LootBox targetBonus) {
        if (targetBonus == null || !(targetBonus.getItem() instanceof Item.HealthPack)) {
            return false;
        }
        if (me.getHealth() < HEALTHPACK_THRESHOLD) {
            return true;
        }
        return iAmWinning();
    }

    private boolean iAmWinning() {
        return getMyPlayer().getScore() > getEnemyPlayer().getScore();
    }

    Player getMyPlayer() {
        return Stream.of(game.getPlayers())
                .filter(p -> p.getId() == me.getPlayerId())
                .findAny().get();
    }

    Player getEnemyPlayer() {
        return Stream.of(game.getPlayers())
                .filter(p -> p.getId() != me.getPlayerId())
                .findAny().get();
    }

    private Point findShootingPosition(Unit enemy) {
        Point enemyPos = new Point(enemy);
        if (game.getCurrentTick() > game.getProperties().getMaxTickCount() * 0.75 && !iAmWinning()) {
            return enemyPos;
        }
        /*if (!fake && timeToShoot(me) < timeToShoot(enemy) - 0.1) {
            debug.drawSquare(new Point(map.length / 2.0, map[0].length / 2.0), 2, RED);
            return enemyPos;
        }/**/
        return getSafeShootingPosition(enemy);
    }

    private Point getSafeShootingPosition(Unit enemy) {
        double maxDist = Double.NEGATIVE_INFINITY;
        Point bestPoint = null;
        double myX = me.getPosition().getX();
        double enemyX = enemy.getPosition().getX();
        for (Point p : stablePoints) {
            Point muzzlePoint = new Point(p.x, p.y + HEIGHT / 2);
            if (inLineOfSight(muzzlePoint, map, me.getWeapon(), enemy)) {
                double dist = dist(muzzlePoint, enemy);
                boolean sameSide = abs(myX - enemyX) < 1 || (myX < enemyX) == (muzzlePoint.x < enemyX);
                if (!sameSide) {
                    dist -= 1000;
                }
                if (dist > maxDist) {
                    maxDist = dist;
                    bestPoint = p;
                }
            }
        }
        return bestPoint;
    }

    static double timeToShoot(Unit unit) {
        Weapon weapon = unit.getWeapon();
        if (weapon == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (weapon.getFireTimer() == null) {
            return 0;
        }
        return weapon.getFireTimer();
    }

    private boolean canBeInTile(int x, int y) {
        if (map[x][y] == WALL) {
            return false;
        }
        if (map[x][y] == LADDER) {
            return true;
        }
        if (map[x][y - 1] == WALL || map[x][y - 1] == PLATFORM || map[x][y - 1] == LADDER) {
            return true;
        }
        return false;
    }

    private MoveAction tryDodgeBullets(MoveAction move) { // returns null if not in danger or can't dodge
        UnitState state = new UnitState(me);
        int steps = 50;
        List<UnitState> states = simulator.simulate(state, plan(steps, move));
        double defaultDanger = dangerFactor(states);
        if (defaultDanger <= 0) {
            return null;
        }
        for (Bullet bullet : game.getBullets()) {
            if (bullet.getPlayerId() == me.getPlayerId()) {
                continue;
            }
            List<Point> bulletPositions = simulator.simulateBullet(bullet, steps);
            for (Point p : bulletPositions) {
                debug.drawSquare(p, bullet.getSize(), RED);
                if (bulletCollidesWithWall(map, p, bullet.getSize())) {
                    if (bullet.getExplosionParams() != null) {
                        debug.drawSquare(p, bullet.getExplosionParams().getRadius() * 2, new ColorFloat(1f, 0f, 0f, 0.5f));
                    }
                    break;
                }
            }
        }
        Set<Plan> plans = genDodgePlans(steps);

        double minDanger = Double.POSITIVE_INFINITY;
        Plan bestPlan = null;
        for (Plan plan : plans) {
            List<UnitState> dodgeStates = simulator.simulate(state, plan);
            double danger = dangerFactor(dodgeStates);
            if (danger < minDanger) {
                minDanger = danger;
                bestPlan = plan;
            }
        }
        if (minDanger >= defaultDanger) {
            return null;
        }
        /*for (UnitState st : simulator.simulate(state, bestPlan)) {
            debug.drawSquare(st.position, 0.1, GREEN);
        }/**/
        lastDodgePlan = bestPlan;
        return bestPlan.get(0);
    }

    private Set<Plan> genDodgePlans(int steps) {
        Set<Plan> plans = new LinkedHashSet<>();
        addFollowUpPlans(plans, lastDodgePlan);
        for (int standCnt = 0; standCnt <= steps; standCnt += 2) {
            plans.add(
                    plan(standCnt, new MoveAction(0, false, false))
                            .add(steps - standCnt, new MoveAction(0, true, false))
            );
        }
        for (int upCnt = 0; upCnt <= steps; upCnt += 2) {
            plans.add(
                    plan(upCnt, new MoveAction(0, true, false))
                            .add(steps - upCnt, new MoveAction(0, false, true))
            );
        }
        for (double speed : new double[]{-SPEED, 0, SPEED}) {
            for (boolean jump : new boolean[]{false, true}) {
                for (boolean jumpDown : new boolean[]{false, true}) {
                    if (jump && jumpDown) {
                        continue;
                    }
                    plans.add(plan(steps, new MoveAction(speed, jump, jumpDown)));
                }
            }
        }
        verifyLength(plans, steps);
        return plans;
    }

    private void addFollowUpPlans(Set<Plan> plans, Plan lastPlan) {
        if (lastPlan == null) {
            return;
        }
        for (double speed : new double[]{-SPEED, 0, SPEED}) {
            for (boolean jump : new boolean[]{false, true}) {
                for (boolean jumpDown : new boolean[]{false, true}) {
                    if (jump && jumpDown) {
                        continue;
                    }
                    plans.add(lastPlan.followUpPlan(new MoveAction(speed, jump, jumpDown)));
                }
            }
        }
    }

    private double dangerFactor(List<UnitState> states) {
        double minAllowedDist = 0.5;
        double danger = 0;
        for (Bullet bullet : game.getBullets()) {
            if (bullet.getPlayerId() == me.getPlayerId() && bullet.getWeaponType() != ROCKET_LAUNCHER) {
                continue;
            }
            List<Point> bulletPositions = simulator.simulateBullet(bullet, states.size());
            double minDist = Double.POSITIVE_INFINITY;

            ExplosionParams explosion = bullet.getExplosionParams();
            for (int i = 0; i < bulletPositions.size(); i++) {
                Point bulletPos = bulletPositions.get(i);
                Point myPos = states.get(i).position;
                double dist = distToBullet(myPos, bulletPos, bullet.getSize());
                minDist = min(minDist, dist);
                if (dist == 0) {
                    break;
                }
                if (bulletCollidesWithWall(map, bulletPos, bullet.getSize())) {
                    if (explosion != null) {
                        double distToExplosion = distToBullet(myPos, bulletPos, explosion.getRadius() * 2);
                        danger += getDanger(minAllowedDist, distToExplosion, explosion.getDamage());
                    }
                    break;
                }
            }

            int damage = bullet.getDamage();
            if (explosion != null) {
                damage += explosion.getDamage();
            }
            danger += getDanger(minAllowedDist, minDist, damage);
        }
        danger += minesDangerFactor(states, minAllowedDist);
        return danger;
    }

    private double minesDangerFactor(List<UnitState> states, double minAllowedDist) {
        double danger = 0;
        for (Mine mine : game.getMines()) {
            if (mine.getState() != MineState.TRIGGERED) {
                continue;
            }
            int damage = mine.getExplosionParams().getDamage();
            double timer = mine.getTimer();
            int explosionTick = (int) (timer / simulator.tickDuration);
            if (explosionTick >= states.size()) {
                continue;
            }
            double mineSize = mine.getSize().getX();
            Point mineCenter = new Point(mine.getPosition()).add(new Point(0, mineSize / 2));
            UnitState state = states.get(explosionTick);
            double explosionSize = mine.getExplosionParams().getRadius() * 2;
            double dist = distToBullet(state.position, mineCenter, explosionSize);
            danger += getDanger(minAllowedDist, dist, damage);
        }
        return danger;
    }

    private double getDanger(double minAllowedDist, double dist, int damage) {
        if (dist == 0) {
            return damage;
        }
        if (dist < minAllowedDist) {
            return minAllowedDist - dist;
        }
        return 0;
    }

    private static double distToBullet(Point myPos, Point bulletPos, double size) {
        double myX = myPos.x;
        double myY = myPos.y;
        return max(segmentDist(
                new Segment(myX - WIDTH / 2, myX + WIDTH / 2),
                new Segment(bulletPos.x - size / 2, bulletPos.x + size / 2)
        ), segmentDist(
                new Segment(myY, myY + HEIGHT),
                new Segment(bulletPos.y - size / 2, bulletPos.y + size / 2)
        ));
    }

    private static double segmentDist(Segment a, Segment b) {
        if (intersects(a, b)) {
            return 0;
        }
        return max(a.left - b.right, b.left - a.right);
    }

    private static boolean intersects(Segment a, Segment b) {
        return !(a.right < b.left || a.left > b.right);
    }

    static class Segment {
        final double left, right;

        Segment(double left, double right) {
            this.left = left;
            this.right = right;
        }
    }

    private Point heathPackTargetPoint(LootBox healthPack) {
        Point hpPos = new Point(healthPack.getPosition());
        if (me.getHealth() < HEALTHPACK_THRESHOLD) {
            return hpPos;
        }
        if ((int) me.getPosition().getY() != (int) healthPack.getPosition().getY()) {
            return hpPos;
        }
        double centerX = map.length / 2.0;
        double delta = healthPack.getSize().getX() / 2 + me.getSize().getX() / 2 + 0.1;
        if (hpPos.x < centerX) {
            return new Point(hpPos.x + delta, hpPos.y);
        } else {
            return new Point(hpPos.x - delta, hpPos.y);
        }
    }

    private boolean enoughTimeToGetTo(int platformFloor) {
        double curY = me.getPosition().getY();
        double remainingHeight = me.getJumpState().getSpeed() * me.getJumpState().getMaxTime();
        return (int) (curY + remainingHeight) >= platformFloor + 1;
    }

    private int findPlatformAboveFloor() {
        int x = (int) me.getPosition().getX();
        int y = (int) me.getPosition().getY();
        for (int j = y; j < map[0].length; j++) {
            if (map[x][j] == PLATFORM) {
                return j;
            }
        }
        return -1;
    }

    private boolean shouldShoot(Unit enemy) {
        Weapon weapon = me.getWeapon();
        if (weapon == null) {
            return false;
        }
        if (!inLineOfSight(enemy)) {
            return false;
        }
        if (canExplodeMyselfWithBazooka()) {
            return false;
        }
        return goodSpread(enemy, weapon);
    }

    private boolean canExplodeMyselfWithBazooka() {
        Weapon weapon = me.getWeapon();
        if (weapon.getTyp() != ROCKET_LAUNCHER) {
            return false;
        }
        debug.showSpread(me);
        double angle = weapon.getLastAngle() == null ? 0 : weapon.getLastAngle();
        double spread = weapon.getSpread();
        return canExplodeMyselfWithBazooka(angle + spread) || canExplodeMyselfWithBazooka(angle - spread);
    }

    private boolean canExplodeMyselfWithBazooka(double angle) {
        Point bulletPos = muzzlePoint(me);
        BulletParams bullet = me.getWeapon().getParams().getBullet();
        double speedPerTick = simulator.toTickSpeed(bullet.getSpeed());
        Point delta = Point.dir(angle).mult(speedPerTick);
        while (true) {
            bulletPos = bulletPos.add(delta);
            if (bulletCollidesWithWall(map, bulletPos, bullet.getSize())) {
                if (distToBullet(new Point(me), bulletPos, EXPLOSION_SIZE) <= 0.1) {
                    return true;
                } else {
                    break;
                }
            }
        }
        return false;
    }

    private boolean inLineOfSight(Unit enemy) {
        return inLineOfSight(muzzlePoint(me), map, me.getWeapon(), enemy);
    }

    private static boolean inLineOfSight(Point a, Tile[][] map, Weapon weapon, Unit enemy) {
        Point b = muzzlePoint(enemy);
        boolean blocked = false;
        int n = 1000;
        Point delta = b.minus(a).mult(1.0 / n);
        for (int i = 0; i < n; i++) {
            Point t = a.add(delta.mult(i));
            if (bulletCollidesWithWall(map, t, weapon.getParams().getBullet().getSize())) {
                blocked = true;
            }
        }
        return !blocked;
    }

    private Tile tileAtPoint(Point p) {
        return tileAtPoint(p.x, p.y);
    }

    private Tile tileAtPoint(double x, double y) {
        return Utils.tileAtPoint(map, x, y);
    }

    private boolean goodSpread(Unit enemy, Weapon weapon) { // todo rework
        if (true) {
            return true;
        }/**/
        double spread = weapon.getSpread();
        if (abs(spread - weapon.getParams().getMinSpread()) < 0.1) {
            return true;
        }
        double d = dist(me, enemy);
        double r = max(enemy.getSize().getX(), enemy.getSize().getY()) / 2;
        double angle = atan(r / d);
        return spread <= angle;
    }

    private static Point muzzlePoint(Unit unit) {
        return new Point(unit).add(new Point(0, unit.getSize().getY() / 2));
    }


    private double getVelocity(Point targetPos) {
        double r = targetPos.x - me.getPosition().getX();
        if (r > simulator.tickSpeed) {
            return SPEED;
        }
        if (r < -simulator.tickSpeed) {
            return -SPEED;
        }
        return simulator.fromTickSpeed(r);
    }

    private Vec2Double aim(Unit enemy) {
        return new Vec2Double(
                enemy.getPosition().getX() - me.getPosition().getX(),
                enemy.getPosition().getY() - me.getPosition().getY()
        );
    }

    private LootBox chooseTargetBonus(Unit enemy) {
        Map<Class<? extends Item>, List<LootBox>> map = Stream.of(game.getLootBoxes())
                .collect(Collectors.groupingBy(b -> b.getItem().getClass()));
        List<LootBox> weapons = map.getOrDefault(Item.Weapon.class, Collections.emptyList());
        List<LootBox> healthPacks = map.getOrDefault(Item.HealthPack.class, Collections.emptyList());
        List<LootBox> mines = map.getOrDefault(Item.Mine.class, Collections.emptyList());

        if (me.getWeapon() == null || me.getWeapon().getTyp() == ROCKET_LAUNCHER) {
            return chooseWeapon(weapons);
        } else {
            return chooseHealthPack(healthPacks, enemy);
        }
    }

    private LootBox chooseHealthPack(List<LootBox> healthPacks, Unit enemy) {
        double centerX = map.length / 2.0;
        return healthPacks.stream()
                .min(
                        Comparator
                                .comparing((LootBox h) -> dist(h.getPosition(), me) > dist(h.getPosition(), enemy))
                                .thenComparing(h -> abs(h.getPosition().getX() - centerX))
                                .thenComparing(h -> dist(h.getPosition(), me.getPosition()))
                )
                .orElse(null);
    }

    private LootBox chooseWeapon(List<LootBox> weapons) {
        return weapons.stream()
                .filter(w -> getType(w) != ROCKET_LAUNCHER)
                .min(Comparator.comparing(w -> dist(w.getPosition(), me.getPosition())))
                .orElse(null);
    }

    private WeaponType getType(LootBox lb) {
        return ((Item.Weapon) lb.getItem()).getWeaponType();
    }

    private Unit chooseEnemy() {
        Unit nearestEnemy = null;
        for (Unit other : game.getUnits()) {
            if (other.getPlayerId() != me.getPlayerId()) {
                if (nearestEnemy == null || sqrDist(me.getPosition(),
                        other.getPosition()) < sqrDist(me.getPosition(), nearestEnemy.getPosition())) {
                    nearestEnemy = other;
                }
            }
        }
        return nearestEnemy;
    }

    private List<Point> findStablePoints() {
        List<Point> r = new ArrayList<>();
        double delta = 0.5;
        for (double x = 1 + WIDTH / 2; x < map.length - 1 - WIDTH / 2; x = roundIfClose(x + delta)) {
            for (double y = 1; y < map[0].length - 1 - HEIGHT; y = roundIfClose(y + delta)) {
                if (isStable(x, y)) {
                    r.add(new Point(x, y));
                }
            }
        }
        return r;
    }

    private double roundIfClose(double v) {
        if (abs(v - round(v)) < 1e-9) {
            return round(v);
        }
        return v;
    }

    private boolean isStable(double x, double y) {
        if (unitCollidesWithWall(map, x, y)) {
            return false;
        }
        UnitState start = new UnitState(new Point(x, y), 0, false, false);
        List<UnitState> states = simulator.simulate(start, plan(1, 0, false, false));
        return states.stream().allMatch(s -> s.position.equals(start.position));
    }

    private static void fixBorders(Tile[][] map) {
        for (int x = 0; x < map.length; x++) {
            for (int y = 0; y < map[x].length; y++) {
                if (x == 0 || y == 0 || x == map.length - 1 || y == map[x].length - 1) {
                    map[x][y] = WALL;
                }
            }
        }
    }

    static double sqrDist(Vec2Double a, Vec2Double b) {
        return (a.getX() - b.getX()) * (a.getX() - b.getX()) + (a.getY() - b.getY()) * (a.getY() - b.getY());
    }

    interface MyDebug {
        void drawLine(Point a, Point b);

        void drawLine(Point a, Point b, ColorFloat color);

        void showSpread(Unit me);

        void drawSquare(Point p, double size, ColorFloat color);
    }

    static class MyDebugImpl implements MyDebug {
        final Debug debug;

        MyDebugImpl(Debug debug) {
            this.debug = debug;
        }

        private void drawLine(Unit a, Point b) {
            drawLine(new Point(a), b);
        }

        @Override
        public void drawLine(Point a, Point b) {
            drawLine(a, b, new ColorFloat(0f, 0f, 0f, 0.9f));
        }

        @Override
        public void drawLine(Point a, Point b, ColorFloat color) {
            debug.draw(new CustomData.Line(a.toV2F(), b.toV2F(), 0.1f, color));
        }

        @Override
        public void showSpread(Unit me) {
            Weapon weapon = me.getWeapon();
            if (weapon != null) {
                double curDir = weapon.getLastAngle();
                Point muzzle = muzzlePoint(me);

                showSpread(curDir, muzzle, weapon.getSpread());
                showSpread(curDir, muzzle, weapon.getParams().getMinSpread());
            }
        }

        private void showSpread(double curDir, Point muzzle, double spread) {
            Point to1 = muzzle.add(Point.dir(curDir + spread).mult(100));
            Point to2 = muzzle.add(Point.dir(curDir - spread).mult(100));

            drawLine(muzzle, to1);
            drawLine(muzzle, to2);
        }

        @Override
        public void drawSquare(Point p, double size, ColorFloat color) {
            Vec2Float pos = p.add(new Point(-size / 2, -size / 2)).toV2F();
            Vec2Float sizeV = new Vec2Float((float) size, (float) size);
            debug.draw(new CustomData.Rect(pos, sizeV, color));
        }
    }

    static class MyDebugStub implements MyDebug {
        @Override
        public void drawLine(Point a, Point b) {
        }

        @Override
        public void drawLine(Point a, Point b, ColorFloat color) {
        }

        @Override
        public void showSpread(Unit me) {
        }

        @Override
        public void drawSquare(Point p, double size, ColorFloat color) {
        }
    }

}