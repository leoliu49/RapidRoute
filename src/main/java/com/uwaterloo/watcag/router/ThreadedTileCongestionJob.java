package com.uwaterloo.watcag.router;

import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.router.browser.FabricBrowser;
import com.uwaterloo.watcag.router.elements.CustomRoute;
import com.uwaterloo.watcag.router.elements.TilePath;
import com.xilinx.rapidwright.design.Design;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;

public class ThreadedTileCongestionJob extends ThreadedJob {

    private static final int SINK_TILE_TRAVERSAL_MAX_DEPTH = 8;
    private static final int TILE_TRAVERSAL_MAX_DEPTH = FabricBrowser.TILE_TRAVERSAL_MAX_DEPTH;

    private long tBegin;
    private long tEnd;

    private Design coreDesign;

    private Set<Triple<RegisterConnection, CustomRoute, TilePath>> tilePaths;
    private HashMap<CustomRoute, LinkedList<TilePath>> tilePathChoicesMap;
    private Set<Pair<RegisterConnection, CustomRoute>> failedRoutes;

    public ThreadedTileCongestionJob(Design d, Set<Triple<RegisterConnection, CustomRoute, TilePath>> tilePaths) {
        super();

        coreDesign = d;

        this.tilePaths = tilePaths;
        tilePathChoicesMap = new HashMap<>();
        failedRoutes = new HashSet<>();
    }

    private void beginTiming() {
        tBegin = System.currentTimeMillis();
    }

    private void finishTiming() {
        tEnd = System.currentTimeMillis();
    }

    private long getElapsedTime() {
        return tEnd - tBegin;
    }

    @Override
    public void run() {
        beginTiming();

        HashMap<CustomRoute, RegisterConnection> routeConnectionHashMap = new HashMap<>();

        for (Triple<RegisterConnection, CustomRoute, TilePath> triple : tilePaths) {
            TilePath path = triple.getRight();
            LinkedList<TilePath> pathChoices;

            if (path.getExitJunction().isSnk())
                pathChoices = new LinkedList<>(FabricBrowser.findTilePaths(coreDesign, SINK_TILE_TRAVERSAL_MAX_DEPTH,
                        path.getEnterJunction(), path.getExitJunction()));
            else
                pathChoices = new LinkedList<>(FabricBrowser.findTilePaths(coreDesign, path.getEnterJunction(),
                        path.getExitJunction()));

            routeConnectionHashMap.put(triple.getMiddle(), triple.getLeft());
            tilePathChoicesMap.put(triple.getMiddle(), pathChoices);
        }

        boolean congestionSuccess = false;
        while (!congestionSuccess) {

            congestionSuccess = true;

            int liveLockCount = 0;
            HashMap<CustomRoute, Integer> deflectionCountMap = new HashMap<>();

            Queue<CustomRoute> pathQueue = new LinkedList<>(tilePathChoicesMap.keySet());
            HashMap<TilePath, CustomRoute> results = new HashMap<>();

            while (!pathQueue.isEmpty() && liveLockCount < 999) {
                CustomRoute next = pathQueue.remove();
                liveLockCount += 1;

                TilePath candidatePath = tilePathChoicesMap.get(next).removeFirst();
                tilePathChoicesMap.get(next).addLast(candidatePath);

                for (TilePath conflictingPath : RoutingCalculator.locateTilePathCollisions(candidatePath,
                        new HashSet<>(results.keySet()))) {
                    // Always preempt conflicts
                    pathQueue.add(results.get(conflictingPath));
                    results.remove(conflictingPath);

                    if (!deflectionCountMap.containsKey(next))
                        deflectionCountMap.put(next, 1);
                    else
                        deflectionCountMap.put(next, deflectionCountMap.get(next) + 1);
                }

                results.put(candidatePath, next);
            }

            if (liveLockCount >= 999) {

                congestionSuccess = false;

                // Remove conflicting route: most likely the one with the highest deflection count
                CustomRoute failedRoute = null;
                int highestDeflection = 0;
                for (CustomRoute route : deflectionCountMap.keySet()) {
                    if (highestDeflection < deflectionCountMap.get(route)) {
                        failedRoute = route;
                        highestDeflection = deflectionCountMap.get(route);
                    }
                }

                // Strip bad routes for rerouting in the future - fix congestion of rest of tile paths for now
                tilePathChoicesMap.remove(failedRoute);
                failedRoutes.add(new ImmutablePair<>(routeConnectionHashMap.get(failedRoute), failedRoute));

            }
            else {

                for (TilePath path : results.keySet()) {
                    results.get(path).setPath(path);
                }
            }
        }

        DesignRouter.completeTileCongestionJob(failedRoutes);
        ThreadPool.completeJob(threadID, this);

        finishTiming();
    }

}
