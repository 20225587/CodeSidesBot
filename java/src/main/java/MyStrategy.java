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
    public static final double STABLE_POINT_DELTA = 0.25;

    final boolean fake;
    final boolean local;
    final boolean bazookaOnly = false;

    Game game;
    MyDebug debug;
    Tile[][] map;

    Simulator simulator;
    List<Point> stablePoints;
    List<Unit> myTeam;
    List<Unit> enemies;
    List<BulletTrajectory> bulletTrajectories;
    int previousTick = -1;

    // things different for different units
    Map<Integer, Plan> lastMovementPlan = new HashMap<>();
    Map<Integer, UnitAction> plannedMoves = new HashMap<>();
    Map<Integer, Double> initialX = new HashMap<>();

    public MyStrategy() {
        fake = false;
        local = false;
    }

    public MyStrategy(boolean fake, boolean local) {
        this.fake = fake;
        this.local = local;
    }

    public UnitAction getAction(Unit me, Game game, Debug debug0) {
        /*if (fake) {
            return noop();
        }/**/
        this.game = game;
        this.debug = (local && !fake) ? new MyDebugImpl(debug0) : new MyDebugStub();

        initCommon(me);
        if (simulator == null) {
            initZeroTick();
            //evaluateStablePoints();
        }
        if (previousTick != game.getCurrentTick()) {
            think();
        }
        //showBulletTrajectories();
        previousTick = game.getCurrentTick();
        return plannedMoves.get(me.getId());
    }

    private void evaluateStablePoints() {
        double mi = Double.POSITIVE_INFINITY;
        double ma = Double.NEGATIVE_INFINITY;

        for (Point p : stablePoints) {
            double e = evaluateStablePoint(p);
            mi = min(mi, e);
            ma = max(ma, e);
        }

        for (Point p : stablePoints) {
            double e = evaluateStablePoint(p);
            double normE = normalize(e, mi, ma);
            double size = STABLE_POINT_DELTA;
            debug.drawSquare(
                    p.add(new Point(-size / 2, -size / 2)),
                    size,
                    color(1 - normE, normE, 0, 1)
            );
        }
    }

    private double normalize(double e, double mi, double ma) {
        return (e - mi) / (ma - mi);
    }

    private double evaluateStablePoint(Point p) {
        double delta = 0.2;
        int steps = 20;
        double r = 0;
        for (int dx = -steps; dx <= steps; dx++) {
            for (int dy = -steps; dy <= steps; dy++) {
                double x = p.x + dx * delta;
                double y = p.y + dy * delta;
                Tile tile = inside((int) x, (int) y) ? tileAtPoint(x, y) : WALL;
                r += evalTile(tile);
            }
        }
        return r;
    }

    private double evalTile(Tile tile) {
        switch (tile) {
            case LADDER:
                return 1;
            case PLATFORM:
                return 0.75;
            case EMPTY:
                return 0.5;
            case JUMP_PAD:
                return 0.25;
            case WALL:
                return 0;
        }
        throw new RuntimeException();
    }

    private void think() {
        Unit primary = getPrimary();
        Intention primaryIntention = getIntention(primary, null);
        plannedMoves.put(primary.getId(), primaryIntention.unitAction);

        if (myTeam.size() > 1) {
            Unit secondary = getSecondary(primary);
            Intention secondaryIntention = getIntention(secondary, primaryIntention);
            plannedMoves.put(secondary.getId(), secondaryIntention.unitAction);
        }
    }

    private Intention getIntention(Unit me, Intention primaryIntention) {
        Unit enemy = chooseEnemy(me);
        LootBox targetBonus = chooseTargetBonus(me, enemy, primaryIntention);
        Point targetPos = chooseTargetPosition(me, enemy, targetBonus, primaryIntention);
        PlanAndStates ps = move(me, targetPos, primaryIntention);
        Vec2Double aimDir = aim(me, enemy);
        boolean shoot = shouldShoot(me, enemy);

        boolean swap = targetBonus != null && targetBonus.getItem() instanceof Item.Weapon;
        boolean plantMine = false;

        MoveAction moveAction = ps.plan.get(0);

        UnitAction unitAction = new UnitAction(
                moveAction.speed,
                moveAction.jump,
                moveAction.jumpDown,
                aimDir,
                shoot,
                false,
                swap,
                plantMine
        );
        return new Intention(ps.plan, ps.states, unitAction, targetBonus, targetPos);
    }

    static class Intention {
        final Plan plan;
        final List<UnitState> states;
        final UnitAction unitAction;
        final LootBox targetBonus;
        final Point targetPoint;

        Intention(Plan plan, List<UnitState> states, UnitAction unitAction, LootBox targetBonus, Point targetPoint) {
            this.plan = plan;
            this.states = states;
            this.unitAction = unitAction;
            this.targetBonus = targetBonus;
            this.targetPoint = targetPoint;
        }
    }

    static class PlanAndStates {
        final Plan plan;
        final List<UnitState> states;

        PlanAndStates(Plan plan, List<UnitState> states) {
            this.plan = plan;
            this.states = states;
        }
    }

    private Unit getPrimary() {
        double centerX = map.length / 2.0;
        return myTeam.stream()
                .min(Comparator.comparing((Unit u) -> u.getWeapon() != null)
                        .thenComparing(Unit::getHealth)
                        .thenComparing(u -> abs(initialX.get(u.getId()) - centerX)))
                .get();
    }

    private Unit getSecondary(Unit primary) {
        return myTeam.stream().filter(u -> u.getId() != primary.getId()).findFirst().get();
    }

    private void initZeroTick() {
        map = game.getLevel().getTiles();
        fixBorders(map);
        simulator = new Simulator(
                map,
                (int) game.getProperties().getTicksPerSecond(),
                game.getProperties().getUpdatesPerTick()
        );
        stablePoints = findStablePoints();
        for (Unit u : myTeam) {
            initialX.put(u.getId(), u.getPosition().getX());
        }
    }

    private void initCommon(Unit me) {
        myTeam = Stream.of(game.getUnits())
                .filter(u -> u.getPlayerId() == me.getPlayerId())
                .collect(Collectors.toList());
        enemies = Stream.of(game.getUnits())
                .filter(u -> u.getPlayerId() != me.getPlayerId())
                .collect(Collectors.toList());
        bulletTrajectories = Stream.of(game.getBullets())
                .map(b -> simulator.simulateBullet(b, 100500))
                .collect(Collectors.toList());
    }

    private UnitAction noop() {
        return new UnitAction(0, false, false, new Vec2Double(0, 0), false, false, false, false);
    }

    Plan testPlan;

    List<UnitState> actualStates = new ArrayList<>();
    List<UnitState> simulation;

    private UnitAction testSimulation(Unit me) {
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

    private PlanAndStates move(Unit me, Point targetPos, Intention primaryIntention) {
        UnitState start = new UnitState(me);
        debug.drawLine(new Point(me), targetPos, WHITE);
        Set<Plan> plans = genMovementPlans(me, targetPos, getPlanLength());
        double minEval = Double.POSITIVE_INFINITY;
        Plan bestPlan = null;
        List<UnitState> bestStates = null;
        int[][] dfsDist = dfs(targetPos);
        for (Plan plan : plans) {
            List<UnitState> states = simulator.simulate(start, plan);
            double eval = evaluate(me, targetPos, primaryIntention, dfsDist, states);
            if (eval < minEval) {
                minEval = eval;
                bestStates = states;
                bestPlan = plan;
            }
        }
        showStates(bestStates, GREEN);
        lastMovementPlan.put(me.getId(), bestPlan);
        return new PlanAndStates(bestPlan, bestStates);
    }

    private void showBulletTrajectories(List<BulletTrajectory> trajectories) {
        for (BulletTrajectory trajectory : trajectories) {
            for (Point p : trajectory.positions) {
                debug.drawSquare(p, trajectory.bulletSize, RED);
            }
            if (trajectory.collisionPos != null) {
                debug.drawSquare(trajectory.collisionPos, EXPLOSION_SIZE, color(1, 0, 0, 0.1));
            }
        }
    }

    private double evaluate(Unit me, Point targetPos, Intention primaryIntention, int[][] dfsDist, List<UnitState> states) {
        double eval = 0;
        eval += 0.01 * evalDist(states, dfsDist, targetPos);
        eval += dangerFactor(me, states);
        if (collidesWithPrimaryOrEnemies(states, primaryIntention)) {
            eval += 100;
        }
        return eval;
    }

    private void showStates(List<UnitState> states, ColorFloat color) {
        for (UnitState state : states) {
            debug.drawSquare(state.position, 0.1, color);
        }
    }

    private double evalDist(List<UnitState> states, int[][] dfsDist, Point target) {
        double r = Double.POSITIVE_INFINITY;
        for (int i = 0; i < states.size(); i++) {
            UnitState state = states.get(i);
            double dist = evalDist(dfsDist, target, state) + i * simulator.tickSpeed * 0.1;
            r = min(r, dist);
        }
        return r;
    }

    private double evalDist(int[][] dfsDist, Point target, UnitState state) {
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

    private boolean collidesWithPrimaryOrEnemies(List<UnitState> states, Intention primaryIntention) {
        if (primaryIntention != null) {
            for (int i = 0; i < states.size(); i++) {
                if (unitsIntersect(states.get(i).position, primaryIntention.states.get(i).position)) {
                    return true;
                }
            }
        }
        for (Unit enemy : enemies) {
            Point p = new Point(enemy);
            for (UnitState state : states) {
                if (unitsIntersect(state.position, p)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean unitsIntersect(Point a, Point b) { // todo get rid of excessive object creation
        return intersects(new Segment(a.x - WIDTH / 2, a.x + WIDTH / 2), new Segment(b.x - WIDTH / 2, b.x + WIDTH / 2)) &&
                intersects(new Segment(a.y, a.y + HEIGHT), new Segment(b.y, b.y + HEIGHT));
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

    private Set<Plan> genMovementPlans(Unit me, Point targetPos, int steps) {
        Set<Plan> plans = new LinkedHashSet<>();
        if (myTeam.contains(me)) {
            addFollowUpPlans(plans, lastMovementPlan.get(me.getId()), steps);
        }

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
        verifyLength(plans, steps);
        return plans;
    }

    private int getPlanLength() {
        if (myTeam.size() == 2) {
            return 32;
        } else {
            return 64;
        }
    }

    private void verifyLength(Set<Plan> plans, int steps) {
        if (!plans.stream().allMatch(p -> p.moves.size() == steps)) {
            throw new RuntimeException("wrong plan length");
        }
    }

    Random rnd = new Random(12);
    Point target;

    private Point chooseTargetPosition(Unit me, Unit enemy, LootBox targetBonus, Intention primaryIntention) {
        /*if (true) {
            if (!iAmFirst(me)) {
                return new Point(map.length - 2, 1);
            } else {
                return new Point(1.5, 1);
            }
        }/**/
        Point targetPos;
        if (shouldGoToHealthPack(me, targetBonus)) {
            targetPos = healthPackTargetPoint(me, targetBonus, enemy);
        } else if (targetBonus != null && targetBonus.getItem() instanceof Item.Weapon) {
            targetPos = new Point(targetBonus.getPosition());
        } else {
            targetPos = findShootingPosition(me, enemy, primaryIntention);
        }
        return targetPos;
    }

    private boolean iAmFirst(Unit me) {
        return myTeam.get(0).getId() == me.getId();
    }

    private Point randomGoodTarget(Unit me) {
        while (!goodTarget(me, target)) {
            target = new Point(
                    rnd.nextInt(map.length - 2) + 1.5,
                    rnd.nextInt(map[0].length - 2) + 1.5
            );
        }
        return target;
    }

    private boolean goodTarget(Unit me, Point target) {
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

    private boolean shouldGoToHealthPack(Unit me, LootBox targetBonus) {
        if (targetBonus == null || !(targetBonus.getItem() instanceof Item.HealthPack)) {
            return false;
        }
        if (me.getHealth() < HEALTHPACK_THRESHOLD) {
            return true;
        }
        return myTeam.size() == 1 && iAmWinning(me);
    }

    private boolean iAmWinning(Unit me) {
        return getMyPlayer(me).getScore() > getEnemyPlayer(me).getScore();
    }

    Player getMyPlayer(Unit me) {
        return Stream.of(game.getPlayers())
                .filter(p -> p.getId() == me.getPlayerId())
                .findAny().get();
    }

    Player getEnemyPlayer(Unit me) {
        return Stream.of(game.getPlayers())
                .filter(p -> p.getId() != me.getPlayerId())
                .findAny().get();
    }

    private Point findShootingPosition(Unit me, Unit enemy, Intention primaryIntention) {
        Point enemyPos = new Point(enemy);
        if (game.getCurrentTick() > game.getProperties().getMaxTickCount() * 0.75 && !iAmWinning(me)) {
            return enemyPos;
        }
        if (timeToShoot(me) == 0 && me.getWeapon().getTyp() == ROCKET_LAUNCHER) {
            return enemyPos;
        }
        /*if (!fake && timeToShoot(me) < timeToShoot(enemy) - 0.1) {
            debug.drawSquare(new Point(map.length / 2.0, map[0].length / 2.0), 2, RED);
            return enemyPos;
        }/**/
        return getSafeShootingPosition(me, enemy, primaryIntention);
    }

    private Point getSafeShootingPosition(Unit me, Unit enemy, Intention primaryIntention) {
        double maxDist = Double.NEGATIVE_INFINITY;
        Point bestPoint = null;
        double myX = me.getPosition().getX();
        double enemyX = enemy.getPosition().getX();
        for (Point p : stablePoints) {
            if (primaryIntention != null && unitsIntersect(primaryIntention.targetPoint, p)) {
                continue;
            }
            Point muzzlePoint = new Point(p.x, p.y + HEIGHT / 2);
            if (inLineOfSight(muzzlePoint, me.getWeapon(), enemy)) {
                double dist = dist(muzzlePoint, enemy);
                boolean sameSide = abs(myX - enemyX) < 1 || (myX < enemyX) == (muzzlePoint.x < enemyX);
                if (!sameSide) {
                    dist -= 1000;
                }
                if (dist > maxDist) {
                    maxDist = dist;
                    bestPoint = p;
                }
                /*double eval = evaluateStablePoint(p);
                if (eval > maxEval) {
                    maxEval = eval;
                    bestPoint = p;
                }*/
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

    private void addFollowUpPlans(Set<Plan> plans, Plan lastPlan, int steps) {
        if (lastPlan == null) {
            return;
        }
        for (double speed : new double[]{-SPEED, 0, SPEED}) {
            for (boolean jump : new boolean[]{false, true}) {
                for (boolean jumpDown : new boolean[]{false, true}) {
                    if (jump && jumpDown) {
                        continue;
                    }
                    plans.add(lastPlan.followUpPlan(new MoveAction(speed, jump, jumpDown), steps));
                }
            }
        }
    }

    private double dangerFactor(Unit me, List<UnitState> states) {
        double minAllowedDist = 0.5;
        double danger = 0;
        for (int bulletIndex = 0; bulletIndex < game.getBullets().length; bulletIndex++) {
            Bullet bullet = game.getBullets()[bulletIndex];
            if (bullet.getUnitId() == me.getId() && bullet.getWeaponType() != ROCKET_LAUNCHER) {
                continue;
            }
            BulletTrajectory trajectory = bulletTrajectories.get(bulletIndex);
            danger += bulletDangerFactor(states, trajectory, new MyBulletParams(bullet), minAllowedDist);
        }
        danger += minesDangerFactor(states, minAllowedDist);
        return danger;
    }

    static class MyBulletParams {
        final double size;
        final int damage;
        final ExplosionParams explosion;

        MyBulletParams(double size, int damage, ExplosionParams explosion) {
            this.size = size;
            this.damage = damage;
            this.explosion = explosion;
        }

        MyBulletParams(BulletParams bullet, ExplosionParams explosion) {
            this(bullet.getSize(), bullet.getDamage(), explosion);
        }

        MyBulletParams(Bullet bullet) {
            this(bullet.getSize(), bullet.getDamage(), bullet.getExplosionParams());
        }
    }

    private double bulletDangerFactor(List<UnitState> states, BulletTrajectory trajectory, MyBulletParams bullet, double minAllowedDist) {
        double minDist = Double.POSITIVE_INFINITY;
        double danger = 0;

        ExplosionParams explosion = bullet.explosion;
        for (int i = 0; i < min(trajectory.size(), states.size()); i++) {
            Point bulletPos = trajectory.get(i);
            Point myPos = states.get(i).position;
            double dist = distToBullet(myPos, bulletPos, bullet.size);
            minDist = min(minDist, dist);
            if (dist == 0) {
                break;
            }
        }

        if (minDist > 0 &&
                trajectory.collisionPos != null &&
                explosion != null &&
                trajectory.size() < states.size()
        ) {
            Point myPos = states.get(trajectory.size()).position;
            double distToExplosion = distToBullet(myPos, trajectory.collisionPos, explosion.getRadius() * 2);
            danger += getDanger(minAllowedDist, distToExplosion, explosion.getDamage());
        }

        int collisionDamage = bullet.damage;
        if (explosion != null) {
            collisionDamage += explosion.getDamage();
        }
        danger += getDanger(minAllowedDist, minDist, collisionDamage);
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
            return (1 - dist / minAllowedDist) * 2;
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

    private Point healthPackTargetPoint(Unit me, LootBox healthPack, Unit enemy) {
        Point hpPos = new Point(healthPack.getPosition());
        if (me.getHealth() < HEALTHPACK_THRESHOLD) {
            return hpPos;
        }
        if ((int) me.getPosition().getY() != (int) healthPack.getPosition().getY()) {
            return hpPos;
        }
        double delta = healthPack.getSize().getX() / 2 + me.getSize().getX() / 2 + 0.1;
        double enemyX = enemy.getPosition().getX();
        double targetX;
        if (enemyX > hpPos.x + delta) {
            targetX = hpPos.x + delta;
        } else if (enemyX < hpPos.x - delta) {
            targetX = hpPos.x - delta;
        } else {
            targetX = enemyX;
        }
        return new Point(targetX, hpPos.y);
    }

    private boolean shouldShoot(Unit me, Unit enemy) {
        if (timeToShoot(me) != 0) {
            return false;
        }
        Weapon weapon = me.getWeapon();
        if (canShootTeammate(me)) {
            return false;
        }
        if (!fake && weapon.getTyp() == ROCKET_LAUNCHER) {
            return enemyCantDodge(me);
        }
        if (!inLineOfSight(me, enemy)) {
            return false;
        }
        if (canExplodeMyselfWithBazooka(me)) {
            return false;
        }
        return goodSpread(me, enemy, weapon);
    }

    private boolean enemyCantDodge(Unit me) {
        Weapon weapon = me.getWeapon();
        BulletParams bullet = weapon.getParams().getBullet();
        double angle = weapon.getLastAngle() == null ? 0 : weapon.getLastAngle();
        double spread = weapon.getSpread();
        int steps = 25;
        List<BulletTrajectory> trajectories = Arrays.asList(
                getTrajectory(me, angle + spread, steps),
                getTrajectory(me, angle - spread, steps),
                getTrajectory(me, angle, steps)
        );
        //showBulletTrajectories(trajectories);

        for (Unit enemy : enemies) {
            Set<Plan> plans = genMovementPlans(enemy, new Point(enemy), steps);
            List<PlanAndStates> ps = plans.stream()
                    .map(p -> new PlanAndStates(p, simulator.simulate(new UnitState(enemy), p)))
                    .collect(Collectors.toList());
            if (!canDodge(weapon, bullet, trajectories, ps)) {
                return true;
            }
        }
        return false;
    }

    private boolean canDodge(Weapon weapon, BulletParams bullet, List<BulletTrajectory> trajectories, List<PlanAndStates> ps) {
        for (BulletTrajectory trajectory : trajectories) {
            boolean dodge = false;
            for (PlanAndStates planAndStates : ps) {
                List<UnitState> states = planAndStates.states;
                double danger = bulletDangerFactor(states, trajectory, new MyBulletParams(bullet, weapon.getParams().getExplosion()), 0);
                if (danger == 0) {
                    dodge = true;
                    break;
                }
            }
            if (!dodge) {
                return false;
            }
        }
        return true;
    }

    private BulletTrajectory getTrajectory(Unit me, double shootAngle, int steps) {
        BulletParams bullet = me.getWeapon().getParams().getBullet();
        Point speed = Point.dir(shootAngle).mult(bullet.getSpeed());
        return simulator.simulateBullet(muzzlePoint(me), speed, bullet.getSize(), steps);
    }


    private boolean canShootTeammate(Unit me) {
        Unit teammate = getTeammate(me);
        if (teammate == null) {
            return false;
        }
        Weapon weapon = me.getWeapon();
        double angle = weapon.getLastAngle();
        double spread = weapon.getSpread();

        return canShootTeammate(me, teammate, weapon, angle - spread) ||
                canShootTeammate(me, teammate, weapon, angle + spread);
    }

    private boolean canShootTeammate(Unit me, Unit teammate, Weapon weapon, double shootAngle) {
        BulletParams bullet = weapon.getParams().getBullet();
        double speed = bullet.getSpeed();
        Point start = muzzlePoint(me);
        List<Point> bulletPositions = simulator.simulateBullet(
                start,
                Point.dir(shootAngle).mult(speed),
                bullet.getSize(),
                10
        ).positions;
        for (Point bulletPosition : bulletPositions) {
            if (distToBullet(new Point(teammate), bulletPosition, bullet.getSize()) < 0.1) {
                return true;
            }
        }
        return false;
    }

    private Unit getTeammate(Unit me) {
        return myTeam.stream()
                .filter(u -> u.getId() != me.getId())
                .findAny()
                .orElse(null);
    }

    private boolean canExplodeMyselfWithBazooka(Unit me) {
        Weapon weapon = me.getWeapon();
        if (weapon.getTyp() != ROCKET_LAUNCHER) {
            return false;
        }
        debug.showSpread(me);
        double angle = weapon.getLastAngle() == null ? 0 : weapon.getLastAngle();
        double spread = weapon.getSpread();
        return canExplodeMyselfWithBazooka(me, angle + spread) || canExplodeMyselfWithBazooka(me, angle - spread);
    }

    private boolean canExplodeMyselfWithBazooka(Unit me, double angle) {
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

    private boolean inLineOfSight(Unit me, Unit enemy) {
        return inLineOfSight(muzzlePoint(me), me.getWeapon(), enemy);
    }

    private boolean inLineOfSight(Point from, Weapon weapon, Unit enemy) {
        Point to = muzzlePoint(enemy);
        BulletParams bullet = weapon.getParams().getBullet();
        double speed = bullet.getSpeed();
        Point speedV = to.minus(from).norm().mult(speed);
        int ticksToReach = (int) ceil(dist(from, to) / (speed * simulator.tickDuration));
        BulletTrajectory trajectory = simulator.simulateBullet(from, speedV, bullet.getSize(), ticksToReach);
        return trajectory.size() >= ticksToReach;
    }

    private Tile tileAtPoint(Point p) {
        return tileAtPoint(p.x, p.y);
    }

    private Tile tileAtPoint(double x, double y) {
        return Utils.tileAtPoint(map, x, y);
    }

    private boolean goodSpread(Unit me, Unit enemy, Weapon weapon) { // todo rework
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

    private Vec2Double aim(Unit me, Unit enemy) {
        return new Vec2Double(
                enemy.getPosition().getX() - me.getPosition().getX(),
                enemy.getPosition().getY() - me.getPosition().getY()
        );
    }

    private LootBox chooseTargetBonus(Unit me, Unit enemy, Intention primaryIntention) {
        Map<Class<? extends Item>, List<LootBox>> map = Stream.of(game.getLootBoxes())
                .filter(b -> !bonusTaken(b, primaryIntention))
                .collect(Collectors.groupingBy(b -> b.getItem().getClass()));
        List<LootBox> weapons = map.getOrDefault(Item.Weapon.class, Collections.emptyList());
        List<LootBox> healthPacks = map.getOrDefault(Item.HealthPack.class, Collections.emptyList());
        List<LootBox> mines = map.getOrDefault(Item.Mine.class, Collections.emptyList());

        LootBox weapon = chooseWeapon(me, weapons);
        if (weapon != null) {
            return weapon;
        }
        return chooseHealthPack(me, healthPacks, enemy);
    }

    private boolean bonusTaken(LootBox b, Intention primaryIntention) {
        if (primaryIntention == null) {
            return false;
        }
        if (primaryIntention.targetBonus == null) {
            return false;
        }
        return dist(b.getPosition(), primaryIntention.targetBonus.getPosition()) < 1e-9;
    }

    private LootBox chooseHealthPack(Unit me, List<LootBox> healthPacks, Unit enemy) {
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

    private LootBox chooseWeapon(Unit me, List<LootBox> weapons) {
        return weapons.stream()
                .filter(w -> betterWeapon(me, w))
                .min(
                        Comparator.comparing((LootBox w) -> dangerousLootBox(me, w))
                                .thenComparing(w -> dist(w.getPosition(), me.getPosition()))
                )
                .orElse(null);
    }

    private boolean betterWeapon(Unit me, LootBox w) {
        if (me.getWeapon() == null) {
            return true;
        }
        boolean weNeedBazooka = myTeam.stream()
                .noneMatch(u -> u.getWeapon() != null && u.getWeapon().getTyp() == ROCKET_LAUNCHER);
        return weNeedBazooka && getType(w) == ROCKET_LAUNCHER;
    }

    private WeaponType weaponType(LootBox w) {
        return ((Item.Weapon) w.getItem()).getWeaponType();
    }

    private boolean dangerousLootBox(Unit me, LootBox w) {
        if (me.getWeapon() != null) {
            return false;
        }
        double dist = dist(me, w);
        return enemies.stream().anyMatch(e -> dist(e, me) < dist && dist(e, w) < dist);
    }

    private WeaponType getType(LootBox lb) {
        return ((Item.Weapon) lb.getItem()).getWeaponType();
    }

    private Unit chooseEnemy(Unit me) {
        return enemies.stream()
                .min(Comparator.comparing(e -> dist(e, me))).get();
    }

    private List<Point> findStablePoints() {
        List<Point> r = new ArrayList<>();
        double delta = STABLE_POINT_DELTA;
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