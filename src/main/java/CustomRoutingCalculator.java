import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

public class CustomRoutingCalculator {

    /*
     * Collection of verbose functions for filling in CustomRoute's
     */

    // Used by determineBestLastPaths in conjunction with findBestRouteTemplates
    private static class BestLastPathsTracer {
        public static int bestLastPathsSkipCount = 0;
    }

    // Used by createRouteTemplates to track progress
    private static class JunctionsTracer {
        public ArrayList<EnteringTileJunction> enJuncs;
        // Used to speed up search time in BFS
        public int fastestX;
        public int fastestY;

        public JunctionsTracer(EnteringTileJunction enJunc, int fastestX, int fastestY) {
            this.enJuncs = new ArrayList<EnteringTileJunction>();
            enJuncs.add(enJunc);
            this.fastestX = fastestX;
            this.fastestY = fastestY;
        }

        public JunctionsTracer(ArrayList<EnteringTileJunction> enJuncs, int fastestX, int fastestY) {
            this.enJuncs = new ArrayList<EnteringTileJunction>(enJuncs);
            this.fastestX = fastestX;
            this.fastestY = fastestY;
        }
    }

    /*
     * Use recursion to find appropriate set of routes
     */
    private static boolean findValidRouteTemplatesRecurse(ArrayList<ArrayList<CustomRoute>> allRoutes, int depth,
                                                          int threshold, int[] traceIndex,
                                                          ArrayList<String> footprint) {
        if (depth == allRoutes.size()) {
            return true;
        }

        ArrayList<CustomRoute> routes = allRoutes.get(depth);
        for (int i = 0; i < routes.size() && i <= threshold; i++) {
            traceIndex[depth] = i;
            CustomRoute route = routes.get(i);
            ArrayList<String> fpCopy = new ArrayList<String>(footprint);

            boolean isValid = true;
            for (TileJunction tj : route.getRouteTemplate()) {
                if (fpCopy.contains(tj.getNodeName())) {
                    isValid = false;
                    break;
                }
                fpCopy.add(tj.getNodeName());
            }
            if (!isValid)
                continue;

            // Current route template is good - move on to next
            if (findValidRouteTemplatesRecurse(allRoutes, depth + 1, threshold, traceIndex, fpCopy)) {
                return true;
            }

        }

        return false;
    }

    /*
     * Search through all permutations of potential routing, stopping at the ostensibly best routing configuration
     * The algorithm is constructed to favor the "best slowest route"
     */
    public static ArrayList<CustomRoute> findValidRouteTemplates(ArrayList<ArrayList<CustomRoute>> allRoutes) {
        ArrayList<CustomRoute> routes = new ArrayList<CustomRoute>();

        int[] traceIndex = new int[allRoutes.size()];

        int thresholdLimit = 0;
        for (ArrayList<CustomRoute> rs : allRoutes) {
            if (thresholdLimit < rs.size())
                thresholdLimit = rs.size();
        }

        RouterLog.log("Attempting to find best route with \"best slowest route\" method.", RouterLog.Level.NORMAL);
        RouterLog.indent();

        // Increase threshold little-by-little, so that the "best worst case" is found
        for (int threshold = 0; threshold <= thresholdLimit; threshold++) {
            for (int i = 0; i < allRoutes.size(); i++) {
                ArrayList<CustomRoute> slowest = allRoutes.get(i);
                if (slowest.size() <= threshold)
                    continue;

                ArrayList<ArrayList<CustomRoute>> routeMatrix = new ArrayList<ArrayList<CustomRoute>>(allRoutes);
                ArrayList<CustomRoute> singleRoute = new ArrayList<CustomRoute>();
                singleRoute.add(slowest.get(threshold));

                routeMatrix.set(i, singleRoute);

                boolean isSuccess = findValidRouteTemplatesRecurse(routeMatrix, 0, threshold, traceIndex,
                        new ArrayList<String>());
                if (isSuccess) {
                    for (int j = 0; j < routeMatrix.size(); j++) {
                        routes.add(routeMatrix.get(j).get(traceIndex[j]));
                    }
                    RouterLog.log("Best routing configuration found on rank " + (threshold + 1)
                            + ". Slowest route template on bit " + i, RouterLog.Level.NORMAL);
                    RouterLog.indent(-1);
                    return routes;
                }
            }
        }
        RouterLog.indent(-1);

        RouterLog.log("Failed to find best routing configuration.", RouterLog.Level.ERROR);
        return null;
    }

    /*
     * Looks ahead to see which end paths are most efficient in cost
     */
    public static ArrayList<CustomRoute> findBestRouteTemplates(Design d, ArrayList<ArrayList<CustomRoute>> allRoutes) {

        ArrayList<ArrayList<TileIntPath>> allEndPaths = new ArrayList<ArrayList<TileIntPath>>();

        RouterLog.log("Attempting to find best route with \"look ahead to end paths\" method.", RouterLog.Level.NORMAL);
        RouterLog.indent();


        // Get all possible end paths of all possible routes
        for (ArrayList<CustomRoute> rs : allRoutes) {
            ArrayList<TileIntPath> paths = new ArrayList<TileIntPath>();
            ArrayList<String> enJuncFootprint = new ArrayList<String>();
            for (CustomRoute route : rs) {
                EnteringTileJunction lastPathEnJunc = (EnteringTileJunction)
                        route.getRouteTemplate().get(route.getRouteTemplate().size() - 2);
                ExitingTileJunction lastPathExJunc = route.getSnkJunction();

                String tileName = lastPathExJunc.getTileName();

                if (!enJuncFootprint.contains(lastPathEnJunc.getNodeName())) {
                    paths.addAll(TileBrowser.findIntPaths(d, tileName, lastPathEnJunc, lastPathExJunc));
                    enJuncFootprint.add(lastPathEnJunc.getNodeName());
                }

            }
            allEndPaths.add(paths);
        }

        RouterLog.indent(-1);


        RouterLog.log("Running determineBestLastPaths - and then validating with findValidRouteTemplates.",
                RouterLog.Level.NORMAL);
        RouterLog.indent();

        // Run determineBestLastPaths, then check if there route templates is valid
        // If not, add to skipCount and determine the next best end paths
        int skipCount = 1;
        while (true) {
            ArrayList<TileIntPath> candidateEndPaths = determineBestLastPaths(allEndPaths);
            ArrayList<ArrayList<CustomRoute>> allBestRoutes = new ArrayList<ArrayList<CustomRoute>>();

            // Find list of route templates which can have the candidateEndPaths
            for (int i = 0; i < allRoutes.size(); i++) {
                ArrayList<CustomRoute> rs = allRoutes.get(i);

                TileIntPath bestEndPath = candidateEndPaths.get(i);
                ArrayList<CustomRoute> bestRoutes = new ArrayList<CustomRoute>();
                for (CustomRoute route : rs) {
                    if (bestEndPath.getEnteringJunction().getNodeName().equals(
                            route.getRouteTemplate().get(route.getRouteTemplate().size() - 2).getNodeName())) {
                        bestRoutes.add(route);
                    }
                }

                allBestRoutes.add(bestRoutes);
            }

            // Try to find a conforming set of route templates
            ArrayList<CustomRoute> bestRoutes = findValidRouteTemplates(allBestRoutes);
            if (bestRoutes != null) {
                RouterLog.indent(-1);
                return bestRoutes;
            }
            else {
                BestLastPathsTracer.bestLastPathsSkipCount = skipCount++;
            }
        }
    }

    /*
     * Use recursion to best paths setup of the very last tile
     *
     * TODO: use threshold easing for the recurse search as well
     */
    private static boolean findLastPathRecurse(ArrayList<ArrayList<TileIntPath>> allPaths, int depth, int threshold,
                                               int[] traceIndex, ArrayList<String> footprint) {

        // Return false only during findBestRouteTemplates skip counts
        if (depth == allPaths.size()) {
            if (BestLastPathsTracer.bestLastPathsSkipCount == 0)
                return true;
            else {
                BestLastPathsTracer.bestLastPathsSkipCount -= 1;
                return false;
            }

        }

        ArrayList<TileIntPath> paths = allPaths.get(depth);
        for (int i = 0; i < paths.size(); i++) {
            traceIndex[depth] = i;
            TileIntPath path = paths.get(i);

            if (path.getCost() > threshold)
                continue;

            ArrayList<String> fpCopy = new ArrayList<String>(footprint);

            boolean isValid = true;
            for (String nodeName : path.getNodePath()) {
                if (fpCopy.contains(nodeName)) {
                    isValid = false;
                    break;
                }
                fpCopy.add(nodeName);
            }
            if (!isValid)
                continue;

            // Current path is good - move on to next depth
            if (findLastPathRecurse(allPaths, depth + 1, threshold, traceIndex, fpCopy)) {
                return true;
            }
        }

        return false;
    }

    /*
     * Given a collection of all possible end paths on each bit, find the best configuration which minimizes
     * # of PIPs needed to route to data in
     * skipCount is used to indicate how many successful configurations to ignore
     */
    private static ArrayList<TileIntPath> determineBestLastPaths(ArrayList<ArrayList<TileIntPath>> allPaths) {

        ArrayList<TileIntPath> results = new ArrayList<TileIntPath>();

        int[] traceIndex = new int[allPaths.size()];
        int thresholdLimit = 0;
        for (ArrayList<TileIntPath> tps : allPaths) {
            for (TileIntPath p : tps) {
                if (thresholdLimit < p.getCost())
                    thresholdLimit = p.getCost();
            }
        }

        RouterLog.log("Attempting to find best end path using \"best slowest path\" method.", RouterLog.Level.NORMAL);
        RouterLog.indent();


        // Increase cost threshold little-by-little, so that the "best worst case" is found
        for (int threshold = 0; threshold <= thresholdLimit; threshold++) {
            for (int i = 0; i < allPaths.size(); i++) {
                ArrayList<TileIntPath> slowest = allPaths.get(i);

                ArrayList<ArrayList<TileIntPath>> lastPathMatrix = new ArrayList<ArrayList<TileIntPath>>(allPaths);
                ArrayList<TileIntPath> thresholdPaths = new ArrayList<TileIntPath>();
                for (TileIntPath path : slowest) {
                    if (path.getCost() == threshold)
                        thresholdPaths.add(path);
                }

                lastPathMatrix.set(i, thresholdPaths);

                boolean isSuccess = findLastPathRecurse(lastPathMatrix, 0, threshold, traceIndex,
                        new ArrayList<String>());
                if (isSuccess) {
                    for (int j = 0; j < lastPathMatrix.size(); j++) {
                        results.add(lastPathMatrix.get(j).get(traceIndex[j]));
                    }
                    RouterLog.log("Best routing configuration found on cost " + threshold
                            + ". Slowest path on bit " + i, RouterLog.Level.NORMAL);

                    RouterLog.log("Best paths:", RouterLog.Level.VERBOSE);
                    RouterLog.indent();

                    for (TileIntPath path : results) {
                        RouterLog.log(path.toString(), RouterLog.Level.VERBOSE);
                    }

                    RouterLog.indent(-1);
                    RouterLog.indent(-1);
                    return results;
                }
            }
        }

        RouterLog.indent(-1);
        RouterLog.log("Failed to complete end path routing.", RouterLog.Level.ERROR);

        return null;
    }

    /*
     * Returns the index of conflicted path
     * -1 reserved for not found condition
     */
    private static int locateConflictedPath(TileIntPath candidatePath, ArrayList<CustomRoute> routes) {
        CustomRoute conflictedRoute = routes.get(doesRoutingConflictExist(candidatePath, routes));

        for (TileIntPath path : conflictedRoute.getRoute()) {
            for (String nodeName : path.getNodePath()) {
                if (candidatePath.getNodePath().contains(nodeName))
                    return conflictedRoute.getRoute().indexOf(path);
            }
        }
        return -1;
    }

    /*
     * Returns -1 if no conflicts exist
     * Else returns the index of conflicted route
     */
    private static int doesRoutingConflictExist(TileIntPath candidatePath, ArrayList<CustomRoute> routes) {
        ArrayList<String> footprint = new ArrayList<String>();
        for (CustomRoute route : routes) {
            for (TileIntPath path : route.getRoute()) {
                for (String nodeName : path.getNodePath())
                    footprint.add(nodeName);
            }

            for (String nodeName : candidatePath.getNodePath()) {
                if (footprint.contains(nodeName))
                    return routes.indexOf(route);
            }
        }

        return -1;
    }

    public static void completeRouting(Design d, ArrayList<CustomRoute> routes) {
        for (CustomRoute route : routes) {
            route.derivePathSubsFromTemplate(d);
        }

        routes = new ArrayList<CustomRoute>(routes);

        RouterLog.log("Performing route contention until last INT tile.", RouterLog.Level.NORMAL);
        RouterLog.indent();
        int conflictsCount = 0;

        boolean allRoutesArrivedToTile = false;
        while (!allRoutesArrivedToTile) {
            allRoutesArrivedToTile = true;

            for (int i = 0; i < routes.size(); i++) {
                CustomRoute route = routes.get(i);
                int nextPathIndex = route.getRoute().size();

                // Since the last path is especially tricky, save it for later
                if (nextPathIndex == route.getRouteTemplateSize() - 1) {
                    allRoutesArrivedToTile = true;
                    continue;
                }

                allRoutesArrivedToTile = false;

                int conflictedRouteIndex = 0;
                while (conflictedRouteIndex != -1) {
                    conflictedRouteIndex = doesRoutingConflictExist(route.getNextCandidatePath(), routes);
                    if (conflictedRouteIndex == -1) {
                        route.addNextPathToRoute();
                    }
                    else {
                        conflictsCount += 1;
                        int conflictedPathIndex = locateConflictedPath(route.getNextCandidatePath(), routes);
                        CustomRoute conflictedRoute = routes.get(conflictedRouteIndex);
                        conflictedRoute.revert(conflictedPathIndex);
                    }
                }
            }
        }

        RouterLog.log("Route contention to last INT tile complete, " + conflictsCount + " conflicts were encountered.",
                RouterLog.Level.NORMAL);
        RouterLog.indent(-1);

        // From here, all routes have been routed to the end INT tile - use "best worst case" algorithm for last bit
        ArrayList<ArrayList<TileIntPath>> allPaths = new ArrayList<ArrayList<TileIntPath>>();
        for (CustomRoute route : routes) {
            allPaths.add(route.getPathSubs().get(route.getPathSubs().size() - 1));
        }

        ArrayList<TileIntPath> endPaths = determineBestLastPaths(allPaths);
        for (int i = 0; i < routes.size(); i++) {
            routes.get(i).addPathToRoute(endPaths.get(i));
            routes.get(i).setRouteComplete();
        }

    }

    /*
     * Perform omni-directional BFS search
     *
     * Routes returned only have enter/exit junctions filled, but not the specific TileIntPaths between them
     */
    public static ArrayList<CustomRoute> createRouteTemplates(Design d, EnteringTileJunction srcJunc,
                                                              ExitingTileJunction snkJunc) {

        ArrayList<CustomRoute> results = new ArrayList<CustomRoute>();

        Tile srcIntTile = d.getDevice().getTile(srcJunc.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snkJunc.getTileName());

        //int separationX = Math.abs(srcIntTile.getTileXCoordinate() - snkIntTile.getTileXCoordinate());
        //int separationY = Math.abs(srcIntTile.getTileYCoordinate() - snkIntTile.getTileYCoordinate());

        // Having a default max depth is not shown to be helpful
        int maxDepth = 99;

        RouterLog.log("Running BFS for " + srcJunc + " --> " + snkJunc + ".", RouterLog.Level.NORMAL);
        RouterLog.indent();

        RouterLog.log("Maximum BFS traversal depth set to " + maxDepth + ".", RouterLog.Level.VERBOSE);

        // To speed up search time, stop searching overlapped nodes, but don't consider overlaps within same depth
        ArrayList<String> nodeFootprint = new ArrayList<String>();
        ArrayList<String> currentDepthFootprint = new ArrayList<String>();

        JunctionsTracer start = new JunctionsTracer(srcJunc, 0, 0);

        Queue<JunctionsTracer> queue = new LinkedList<JunctionsTracer>();
        queue.add(start);

        int lastDepth = 0;

        while (!queue.isEmpty()) {
            JunctionsTracer routeTemplate = queue.remove();
            int size = routeTemplate.enJuncs.size();
            EnteringTileJunction enJunc = routeTemplate.enJuncs.get(size - 1);
            if (size > maxDepth)
                continue;

            if (size > lastDepth) {
                lastDepth = size;
                nodeFootprint.addAll(currentDepthFootprint);
                currentDepthFootprint.clear();

                RouterLog.log("BFS traversal for depth " + (lastDepth - 1) + " finished; continuing to next level.",
                        RouterLog.Level.VERBOSE);
            }

            int remainingX = Math.abs(snkIntTile.getTileXCoordinate()
                    - d.getDevice().getTile(enJunc.getTileName()).getTileXCoordinate());
            int remainingY = Math.abs(snkIntTile.getTileYCoordinate()
                    - d.getDevice().getTile(enJunc.getTileName()).getTileYCoordinate());

            if (remainingX == 0 && remainingY == 0) {
                if (TileBrowser.isJunctionReachable(d, enJunc, snkJunc)) {
                    results.add(new CustomRoute(d, srcJunc, snkJunc, routeTemplate.enJuncs));
                    // Let all other routes within the same depth finish
                    maxDepth = size;
                    continue;
                }
            }

            if ((CustomRouter.globalNodeFootprint.contains(enJunc.getNodeName())
                    || nodeFootprint.contains(enJunc.getNodeName()))
                    && lastDepth != 1)
                continue;

            ArrayList<ExitingTileJunction> exits;
            /*
             * Not sure how to do this for all 4 directions
            // To speed up search time, only increase in speed
            if (enJunc.getWireLength() <= remainingDistance)
                exits = TileBrowser.findReachableExits(d, enJunc.getTileName(), enJunc, enJunc.getWireLength(),
                        remainingDistance, dir);
            else
                exits = TileBrowser.findReachableExits(d, enJunc.getTileName(), enJunc, 1, remainingDistance, dir);
            */

            // Only speed up
            exits = TileBrowser.findReachableExits(d, enJunc.getTileName(), enJunc, enJunc.getHighestSpeeds(),
                    new int[]{remainingY, remainingY, remainingX, remainingX});

            //Collections.sort(exits);

            for (ExitingTileJunction exJunc : exits) {
                JunctionsTracer rtCopy = new JunctionsTracer(routeTemplate.enJuncs,
                        routeTemplate.fastestX, routeTemplate.fastestY);
                rtCopy.enJuncs.add(exJunc.getWireDestJunction(d, enJunc.getHighestSpeeds()));
                queue.add(rtCopy);
            }

            currentDepthFootprint.add(enJunc.getNodeName());
        }

        RouterLog.log("Found " + results.size() + " potential routes.", RouterLog.Level.NORMAL);

        RouterLog.indent();
        for (CustomRoute route : results) {
            RouterLog.log(route.getRouteTemplate().toString(), RouterLog.Level.VERBOSE);
        }
        RouterLog.indent(-1);
        RouterLog.indent(-1);

        return results;
    }
}
