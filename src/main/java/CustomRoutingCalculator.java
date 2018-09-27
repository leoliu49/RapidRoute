import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

import java.util.*;

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

        int fastestX, fastestY;

        public JunctionsTracer(EnteringTileJunction enJunc) {
            enJuncs = new ArrayList<EnteringTileJunction>();
            enJuncs.add(enJunc);

            fastestX = 0;
            fastestY = 0;
        }

        public JunctionsTracer(JunctionsTracer ref) {
            enJuncs = new ArrayList<EnteringTileJunction>(ref.enJuncs);

            fastestX = ref.fastestX;
            fastestY = ref.fastestY;
        }

        public EnteringTileJunction get(int i) {
            if (i < 0)
                i = enJuncs.size() + i;
            return enJuncs.get(i);
        }

        public void append(EnteringTileJunction enJunc) {
            enJuncs.add(enJunc);

            if (RouteUtil.isVertical(enJunc.getDirection()))
                fastestY = fastestY >= enJunc.getWireLength() ? fastestY : enJunc.getWireLength();
            else
                fastestX = fastestX >= enJunc.getWireLength() ? fastestX : enJunc.getWireLength();
        }
    }

    private static final int longLineTolerance = 3;
    private static final int depthTolerance = 3;
    private static Set<String> nodeLock = new HashSet<>();

    public static void flushNodeLock() {
        nodeLock.clear();
    }

    public static boolean lock(String nodeName) {
        RouterLog.log("Locking <" + nodeName + ">", RouterLog.Level.VERBOSE);
        if (nodeLock.contains(nodeName))
            return false;
        nodeLock.add(nodeName);
        return true;
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
     * Omni-directional BFS to nearest long line (in the right direction)
     *
     * All potential routes returned
     */
    private static ArrayList<CustomRoute> findNearestLongLines(Design d, EnteringTileJunction srcJunc,
                                                                   WireDirection dir) {

        ArrayList<CustomRoute> results = new ArrayList<CustomRoute>();

        // Having a default max depth is not shown to be helpful
        int maxDepth = 99;

        boolean isVert = RouteUtil.isVertical(dir);
        Tile srcIntTile = d.getDevice().getTile(srcJunc.getTileName());
        int longLineLength = isVert ? TileBrowser.LONG_LINE_Y : TileBrowser.LONG_LINE_X;

        RouterLog.log("Running BFS for " + srcJunc + " to nearest long lines.", RouterLog.Level.NORMAL);
        RouterLog.indent();

        RouterLog.log("Maximum BFS traversal depth set to " + maxDepth + ".", RouterLog.Level.VERBOSE);

        // To speed up search time, stop searching overlapped nodes, but don't consider overlaps within same depth
        ArrayList<String> nodeFootprint = new ArrayList<String>();
        ArrayList<String> currentDepthFootprint = new ArrayList<String>();

        JunctionsTracer start = new JunctionsTracer(srcJunc);

        Queue<JunctionsTracer> queue = new LinkedList<JunctionsTracer>();
        queue.add(start);

        int lastDepth = 0;

        // Ensures that BFS captures not too many long lines, which may drastically slow search time
        while (!queue.isEmpty() && results.size() <= longLineTolerance) {
            JunctionsTracer routeTemplate = queue.remove();
            int size = routeTemplate.enJuncs.size();
            EnteringTileJunction enJunc = routeTemplate.enJuncs.get(size - 1);

            if (size > maxDepth)
                break;

            if (size > lastDepth) {
                lastDepth = size;
                nodeFootprint.addAll(currentDepthFootprint);
                currentDepthFootprint.clear();

                RouterLog.log("BFS traversal for depth " + (lastDepth - 1) + " finished; continuing to next level.",
                        RouterLog.Level.VERBOSE);
            }


            if ((CustomRouter.globalNodeFootprint.contains(enJunc.getNodeName())
                    || nodeFootprint.contains(enJunc.getNodeName()) || nodeLock.contains(enJunc.getNodeName()))
                    && lastDepth != 1)
                continue;

            int score = isVert
                    ? Math.abs(d.getDevice().getTile(enJunc.getTileName()).getTileXCoordinate()
                    - srcIntTile.getTileXCoordinate())
                    : Math.abs(d.getDevice().getTile(enJunc.getTileName()).getTileYCoordinate()
                    - srcIntTile.getTileYCoordinate());
            ArrayList<ExitingTileJunction> exits;

            // Leash BFS so search is bounded in orthogonal direction
            if (score > 0)
                exits = TileBrowser.findReachableExits(d, enJunc.getTileName(), enJunc, dir);
            else
                exits = TileBrowser.findReachableExits(d, enJunc.getTileName(), enJunc);

            boolean isLongLineFound = false;
            for (ExitingTileJunction exJunc : exits) {
                if (exJunc.getWireLength() == longLineLength && exJunc.getDirection().equals(dir)
                        && TileBrowser.isJunctionRepeatable(d, exJunc) && !nodeLock.contains(exJunc.getNodeName())) {
                    results.add(new CustomRoute(d, srcJunc, exJunc, new JunctionsTracer(routeTemplate).enJuncs));
                    isLongLineFound = true;

                    // Stop search only when a zero-score long line has been found (otherwise no guarantee of success)
                    if (score == 0)
                        // Let all other routes within the same depth finish
                        maxDepth = size;
                }
            }
            if (isLongLineFound)
                continue;
            // Condition if a long line has been found - no need to further queue up
            else if (size == maxDepth)
                continue;

            for (ExitingTileJunction exJunc : exits) {
                JunctionsTracer rtCopy = new JunctionsTracer(routeTemplate);
                rtCopy.enJuncs.add(exJunc.getWireDestJunction(d));

                queue.add(rtCopy);
            }

            currentDepthFootprint.add(enJunc.getNodeName());

        }

        RouterLog.log("Found " + results.size() + " potential long line feeders.", RouterLog.Level.NORMAL);

        RouterLog.indent();
        for (CustomRoute route : results) {
            RouterLog.log(route.getRouteTemplate().toString(), RouterLog.Level.VERBOSE);
        }
        RouterLog.indent(-1);
        RouterLog.indent(-1);

        return results;
    }

    /*
     * Omni-directional BFS from source to sink. Modifications are added so that BFS is sped up.
     */
    private static ArrayList<CustomRoute> completeEndRouteTemplates(Design d, EnteringTileJunction srcJunc,
                                                                    ExitingTileJunction snkJunc) {
        ArrayList<CustomRoute> results = new ArrayList<CustomRoute>();

        Tile srcIntTile = d.getDevice().getTile(srcJunc.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snkJunc.getTileName());

        int maxDepth = depthTolerance;

        RouterLog.log("Running BFS for " + srcJunc + " --> " + snkJunc + ".", RouterLog.Level.NORMAL);
        RouterLog.indent();

        RouterLog.log("Maximum BFS traversal depth set to " + maxDepth + ".", RouterLog.Level.VERBOSE);

        // To speed up search time, stop searching overlapped nodes, but don't consider overlaps within same depth
        ArrayList<String> nodeFootprint = new ArrayList<String>();
        ArrayList<String> currentDepthFootprint = new ArrayList<String>();

        JunctionsTracer start = new JunctionsTracer(srcJunc);

        Queue<JunctionsTracer> queue = new LinkedList<JunctionsTracer>();
        queue.add(start);

        int lastDepth = 0;

        while (!queue.isEmpty()) {
            JunctionsTracer routeTemplate = queue.remove();
            int size = routeTemplate.enJuncs.size();
            EnteringTileJunction enJunc = routeTemplate.enJuncs.get(size - 1);

            //System.out.println(enJunc);

            if (size > maxDepth)
                break;

            if (size > lastDepth) {
                lastDepth = size;
                nodeFootprint.addAll(currentDepthFootprint);
                currentDepthFootprint.clear();

                RouterLog.log("BFS traversal for depth " + (lastDepth - 1) + " finished; continuing to next level.",
                        RouterLog.Level.VERBOSE);
            }

            if ((CustomRouter.globalNodeFootprint.contains(enJunc.getNodeName())
                    || nodeFootprint.contains(enJunc.getNodeName()) || nodeLock.contains(enJunc.getNodeName()))
                    && lastDepth != 1)
                continue;

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
            // Condition if a route has been already found - no need to further queue up
            else if (size == maxDepth)
                continue;

            ArrayList<ExitingTileJunction> exits = TileBrowser.findReachableExits(d, enJunc.getTileName(), enJunc);

            for (ExitingTileJunction exit : exits) {
                JunctionsTracer rtCopy = new JunctionsTracer(routeTemplate);
                rtCopy.append(exit.getWireDestJunction(d));
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

    /*
     * Singly directional BFS from source to sink. Modifications are added so that BFS is sped up.
     */
    private static ArrayList<CustomRoute> completeEndRouteTemplates(Design d, EnteringTileJunction srcJunc,
                                                                    ExitingTileJunction snkJunc, WireDirection dir) {
        ArrayList<CustomRoute> results = new ArrayList<CustomRoute>();

        boolean isVert = RouteUtil.isVertical(dir);
        Tile srcIntTile = d.getDevice().getTile(srcJunc.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snkJunc.getTileName());

        int maxDepth = 99;

        RouterLog.log("Running BFS for " + srcJunc + " --> " + snkJunc + ".", RouterLog.Level.NORMAL);
        RouterLog.indent();

        RouterLog.log("Maximum BFS traversal depth set to " + maxDepth + ".", RouterLog.Level.VERBOSE);

        // To speed up search time, stop searching overlapped nodes, but don't consider overlaps within same depth
        ArrayList<String> nodeFootprint = new ArrayList<String>();
        ArrayList<String> currentDepthFootprint = new ArrayList<String>();

        JunctionsTracer start = new JunctionsTracer(srcJunc);

        Queue<JunctionsTracer> queue = new LinkedList<JunctionsTracer>();
        queue.add(start);

        int lastDepth = 0;

        while (!queue.isEmpty()) {
            JunctionsTracer routeTemplate = queue.remove();
            int size = routeTemplate.enJuncs.size();
            EnteringTileJunction enJunc = routeTemplate.enJuncs.get(size - 1);

            if (size > maxDepth)
                break;

            if (size > lastDepth) {
                lastDepth = size;
                nodeFootprint.addAll(currentDepthFootprint);
                currentDepthFootprint.clear();

                RouterLog.log("BFS traversal for depth " + (lastDepth - 1) + " finished; continuing to next level.",
                        RouterLog.Level.VERBOSE);
            }

            if ((CustomRouter.globalNodeFootprint.contains(enJunc.getNodeName())
                    || nodeFootprint.contains(enJunc.getNodeName()) || nodeLock.contains(enJunc.getNodeName()))
                    && lastDepth != 1)
                continue;

            int remainingDistance = isVert
                    ? Math.abs(snkIntTile.getTileYCoordinate() - d.getDevice().getTile(enJunc.getTileName()).getTileYCoordinate())
                    : Math.abs(snkIntTile.getTileXCoordinate() - d.getDevice().getTile(enJunc.getTileName()).getTileXCoordinate());

            if (remainingDistance == 0) {
                if (TileBrowser.isJunctionReachable(d, enJunc, snkJunc)) {
                    results.add(new CustomRoute(d, srcJunc, snkJunc, routeTemplate.enJuncs));
                    // Let all other routes within the same depth finish
                    maxDepth = size;
                    continue;
                }
            }
            // Condition if a route has been already found - no need to further queue up
            else if (size == maxDepth)
                continue;

            // To speed up search time, ignore hops which are slower
            ArrayList<ExitingTileJunction> exits;
            if (enJunc.getWireLength() > remainingDistance)
                exits = TileBrowser.findReachableExits(d, enJunc.getTileName(), enJunc, 1, remainingDistance,
                        dir);
            else
                exits = TileBrowser.findReachableExits(d, enJunc.getTileName(), enJunc, enJunc.getWireLength(),
                        remainingDistance, dir);

            for (ExitingTileJunction exit : exits) {
                JunctionsTracer rtCopy = new JunctionsTracer(routeTemplate);
                rtCopy.append(exit.getWireDestJunction(d));
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

    /*
     * Helper class which deals with the nightmare of completing long line routing
     */
    private static ArrayList<CustomRoute> completeLongLineRouteTemplates(Design d, ExitingTileJunction snkJunc,
                                                                         ArrayList<CustomRoute> longLineFeeders,
                                                                         ArrayList<CustomRoute> longLineFeederSubs,
                                                                         WireDirection dir,
                                                                         HashMap<String, ArrayList<CustomRoute>> endRoutesMap) {
        ArrayList<CustomRoute> results = new ArrayList<CustomRoute>();
        Tile snkIntTile = d.getDevice().getTile(snkJunc.getTileName());

        int longLineLength = RouteUtil.isVertical(dir) ? TileBrowser.LONG_LINE_Y : TileBrowser.LONG_LINE_X;
        int shiftX = RouteUtil.isVertical(dir) ? 0 : (dir.equals(WireDirection.EAST)
                ? longLineLength : -1 * longLineLength);
        int shiftY = RouteUtil.isVertical(dir) ? (dir.equals(WireDirection.NORTH)
                ? longLineLength : -1 * longLineLength) : 0;

        // Automatically assign long lines, as we know they are repeatable
        for (CustomRoute feederRoute : longLineFeeders) {
            JunctionsTracer longLineTracer = new JunctionsTracer(feederRoute.getSnkJunction()
                    .getWireDestJunction(d));
            int remainingDistance = RouteUtil.isVertical(dir)
                    ? snkIntTile.getTileYCoordinate() - d.getDevice().getTile(longLineTracer.get(0).getTileName())
                    .getTileYCoordinate()
                    : snkIntTile.getTileXCoordinate() - d.getDevice().getTile(longLineTracer.get(0).getTileName())
                    .getTileXCoordinate();

            while (Math.abs(remainingDistance) >= longLineLength) {
                longLineTracer.enJuncs.add(EnteringTileJunction.duplWithShift(d, longLineTracer.get(-1), shiftX,
                        shiftY));

                remainingDistance -= shiftX;
                remainingDistance -= shiftY;
            }

            // Join long line route with feeder route
            EnteringTileJunction lastLongLine = longLineTracer.get(-1);
            longLineTracer.enJuncs.remove(longLineTracer.enJuncs.size() - 1);
            CustomRoute longLineRoute;
            if (!longLineTracer.enJuncs.isEmpty()) {
                longLineRoute = CustomRoute.join(d, feederRoute, new CustomRoute(d, longLineTracer.get(0),
                        lastLongLine.getWireSourceJunction(d), longLineTracer.enJuncs));
            }
            else {
                // Case where no long lines were stacked
                longLineRoute = feederRoute;
            }

            // Search and join end routes
            if (!endRoutesMap.containsKey(lastLongLine.getNodeName())) {
                ArrayList<CustomRoute> endRoutes = completeEndRouteTemplates(d, lastLongLine, snkJunc);

                // Last-ditch effort for no end routes found: back up to previous long line and try again
                if (endRoutes.isEmpty() && longLineTracer.enJuncs.size() != 0) {
                    RouterLog.log("Failed to route end of long line to sink junction. Backing up and trying at a further distance",
                            RouterLog.Level.VERBOSE);

                    lastLongLine = longLineTracer.get(-1);
                    longLineTracer.enJuncs.remove(longLineTracer.enJuncs.size() - 1);

                    if (!longLineTracer.enJuncs.isEmpty())
                        longLineRoute = CustomRoute.join(d, feederRoute, new CustomRoute(d, longLineTracer.get(0),
                                lastLongLine.getWireSourceJunction(d), longLineTracer.enJuncs));
                    else
                        longLineRoute = feederRoute;

                    endRoutes = completeEndRouteTemplates(d, lastLongLine, snkJunc);

                }

                endRoutesMap.put(lastLongLine.getNodeName(), endRoutes);
            }

            for (CustomRoute endRoute : endRoutesMap.get(lastLongLine.getNodeName())) {
                results.add(CustomRoute.join(d, longLineRoute, endRoute));
            }
        }

        if (!results.isEmpty())
            return results;

        if (!longLineFeederSubs.isEmpty())
            RouterLog.log("Main long line feeders unavailable. Attempting sub-optimal line feeders.",
                    RouterLog.Level.VERBOSE);
        // Main long line feeders have proven invalid; use sub-optimal feeder subs instead
        for (CustomRoute feederRoute : longLineFeederSubs) {
            JunctionsTracer longLineTracer = new JunctionsTracer(feederRoute.getSnkJunction()
                    .getWireDestJunction(d));
            int remainingDistance = RouteUtil.isVertical(dir)
                    ? snkIntTile.getTileYCoordinate() - d.getDevice().getTile(longLineTracer.get(0).getTileName())
                    .getTileYCoordinate()
                    : snkIntTile.getTileXCoordinate() - d.getDevice().getTile(longLineTracer.get(0).getTileName())
                    .getTileXCoordinate();

            while (Math.abs(remainingDistance) >= longLineLength) {
                longLineTracer.enJuncs.add(EnteringTileJunction.duplWithShift(d, longLineTracer.get(-1), shiftX,
                        shiftY));

                remainingDistance -= shiftX;
                remainingDistance -= shiftY;
            }

            // Join long line route with feeder route
            EnteringTileJunction lastLongLine = longLineTracer.get(-1);
            longLineTracer.enJuncs.remove(longLineTracer.enJuncs.size() - 1);
            CustomRoute longLineRoute;
            if (!longLineTracer.enJuncs.isEmpty()) {
                longLineRoute = CustomRoute.join(d, feederRoute, new CustomRoute(d, longLineTracer.get(0),
                        lastLongLine.getWireSourceJunction(d), longLineTracer.enJuncs));
            }
            else {
                // Case where no long lines were stacked
                longLineRoute = feederRoute;
            }

            // Search and join end routes
            if (!endRoutesMap.containsKey(lastLongLine.getNodeName())) {
                endRoutesMap.put(lastLongLine.getNodeName(), completeEndRouteTemplates(d, lastLongLine, snkJunc));
            }

            for (CustomRoute endRoute : endRoutesMap.get(lastLongLine.getNodeName())) {
                results.add(CustomRoute.join(d, longLineRoute, endRoute));
            }
        }

        return results;
    }

    /*
     * Analyzes whether long lines are necessary to be routed:
     * 1. Too close to use long lines: call BFS with completeEndRouteTemplates
     * 2. Route to nearest long lines, and then route from long lines back to sink
     */
    public static ArrayList<CustomRoute> createRouteTemplates(Design d, EnteringTileJunction srcJunc,
                                                              ExitingTileJunction snkJunc, WireDirection dir) {

        ArrayList<CustomRoute> results = new ArrayList<CustomRoute>();

        Tile srcIntTile = d.getDevice().getTile(srcJunc.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snkJunc.getTileName());

        int totalDistance = RouteUtil.isVertical(dir)
                ? snkIntTile.getTileYCoordinate() - srcIntTile.getTileYCoordinate()
                : snkIntTile.getTileXCoordinate() - srcIntTile.getTileXCoordinate();

        int longLineLength = RouteUtil.isVertical(dir) ? TileBrowser.LONG_LINE_Y : TileBrowser.LONG_LINE_X;

        /*
         * Step 0: If separation is low enough that long lines are not beneficial, do single-dir BFS
         */
        if (Math.abs(totalDistance) < longLineLength)
            return completeEndRouteTemplates(d, srcJunc, snkJunc, dir);

        /*
         * Step 1: Find all routes which feed into long lines
         */
        ArrayList<CustomRoute> longLineFeeders = new ArrayList<CustomRoute>();
        ArrayList<CustomRoute> longLineFeederSubs = new ArrayList<>();
        boolean lowScoreLongLineExists = false;
        for (CustomRoute route : findNearestLongLines(d, srcJunc, dir)) {
            // Determine if long lines will overshoot
            Tile longLineBaseTile = d.getDevice().getTile(route.getSnkJunction().getTileName());
            int remainingDistance = RouteUtil.isVertical(dir)
                    ? snkIntTile.getTileYCoordinate() - longLineBaseTile.getTileYCoordinate()
                    : snkIntTile.getTileXCoordinate() - longLineBaseTile.getTileXCoordinate();

            /*
             * Determine if the long line is part of a different set of tiles altogether:
             * 1. Yes (score > 0): consider only if there are no other zero-score long lines
             * 2. No (score == 0): remove all other non-zero long lines
             */
            int score = RouteUtil.isVertical(dir)
                    ? Math.abs(longLineBaseTile.getTileXCoordinate() - srcIntTile.getTileXCoordinate())
                    : Math.abs(longLineBaseTile.getTileYCoordinate() - srcIntTile.getTileYCoordinate());

            if (Math.abs(remainingDistance) >= longLineLength) {
                if (score == 0) {
                    if (!lowScoreLongLineExists) {
                        longLineFeederSubs.addAll(longLineFeeders);
                        longLineFeeders.clear();
                    }
                    longLineFeeders.add(route);
                    lowScoreLongLineExists = true;
                }
                else {
                    if (!lowScoreLongLineExists)
                        longLineFeeders.add(route);
                    else
                        longLineFeederSubs.add(route);
                }
            }
        }

        /*
         * Step 2: Since long lines are infinitely repeatable, stack +12/+6 lines as much as we can, for each feeder
         */
        HashMap<String, ArrayList<CustomRoute>> endRoutesMap = new HashMap<String, ArrayList<CustomRoute>>();
        results = completeLongLineRouteTemplates(d, snkJunc, longLineFeeders, longLineFeederSubs, dir, endRoutesMap);

        // In the case where long line routing is unsuccessful, do basic single-dir BFS
        if (results.isEmpty()) {
            RouterLog.log("Long line routing failed. Performing directional BFS instead.", RouterLog.Level.VERBOSE);
            results = completeEndRouteTemplates(d, srcJunc, snkJunc, dir);
        }

        // Find and lock nodes which we must have exclusive rights to
        HashMap<String, Integer> exclusivityMap = new HashMap<String, Integer>();
        for (CustomRoute route : results) {
            for (TileJunction tj : route.getRouteTemplate())
                if (exclusivityMap.containsKey(tj.getNodeName()))
                    exclusivityMap.replace(tj.getNodeName(), exclusivityMap.get(tj.getNodeName()) + 1);
                else
                    exclusivityMap.put(tj.getNodeName(), 1);
        }
        for (String nodeName : exclusivityMap.keySet()) {
            if (exclusivityMap.get(nodeName) == results.size())
                lock(nodeName);
        }

        return results;
    }

    /*
     * Perform omni-directional BFS search
     *
     * Routes returned only have enter/exit junctions filled, but not the specific TileIntPaths between them
     *
     * TODO: multi-directional
     *
     */
    public static ArrayList<CustomRoute> createRouteTemplates(Design d, EnteringTileJunction srcJunc,
                                                              ExitingTileJunction snkJunc) {

        Tile srcIntTile = d.getDevice().getTile(srcJunc.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snkJunc.getTileName());

        int distX = snkIntTile.getTileXCoordinate() - srcIntTile.getTileXCoordinate();
        int distY = snkIntTile.getTileYCoordinate() - srcIntTile.getTileYCoordinate();

        WireDirection primHDir = RouteUtil.primaryHDirection(distX);
        WireDirection primVDir = RouteUtil.primaryVDirection(distY);

        if (primHDir == null)
            return createRouteTemplates(d, srcJunc, snkJunc, primVDir);
        if (primVDir == null)
            return createRouteTemplates(d, srcJunc, snkJunc, primHDir);

        // TODO:
        return null;
    }
}
