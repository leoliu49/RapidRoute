import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class CustomRoutingCalculator {

    /*
     * Collection of verbose functions for filling in CustomRoute's
     */

    /*
     * From a list of potential paths, find any nodes that are "must-haves"
     */
    private static Set<String> deriveExclusiveNodes(ArrayList<TilePath> pathChoices) {
        HashMap<String, Integer> nodeUsages = new HashMap<>();
        Set<String> exclusives = new HashSet<>();

        int size = pathChoices.size();

        for (TilePath path : pathChoices) {
            for (String node : path.getNodePath()) {
                if (nodeUsages.containsKey(node))
                    nodeUsages.put(node, nodeUsages.get(node) + 1);
                else
                    nodeUsages.put(node, 1);
            }
        }

        for (String node : nodeUsages.keySet()) {
            if (nodeUsages.get(node) >= size)
                exclusives.add(node);
        }

        return exclusives;
    }

    /*
     * Finds, out of a family of RouteTemplate's, which RouteTemplate is colliding with the family
     * Returns indexes of collisions
     */
    private static ArrayList<Integer> locateTemplateCollisions(Set<String> candidateSet,
                                                               HashMap<Integer, Set<String>> usages) {
        ArrayList<Integer> results = new ArrayList<>();

        for (int key : usages.keySet()) {
            for (String nodeName : candidateSet) {
                if (usages.get(key).contains(nodeName)) {
                    results.add(key);
                    break;
                }
            }
        }
        return results;
    }

    private static ArrayList<Pair<Integer, Integer>> locateTilePathCollisions(TilePath candidatePath,
                                                                              ArrayList<CustomRoute> routes) {
        ArrayList<Pair<Integer, Integer>> results = new ArrayList<>();

        Set<String> nodes = new HashSet<>(candidatePath.getNodePath());

        for (int i = 0; i < routes.size(); i++) {
            CustomRoute route = routes.get(i);
            for (int j = 0; j < route.getRoute().size(); j++) {
                TilePath path = route.getRoute().get(j);
                if (path == null)
                    continue;
                for (String nodeName : path.getNodePath()) {
                    if (nodes.contains(nodeName)) {
                        results.add(new ImmutablePair<>(i, j));
                        break;
                    }
                }
            }
        }


        return results;
    }

    /*
     * Run omni-directional BFS from source to sink to find all hops necessary. Hops wires are aggregated and put into
     *  a RouteTemplate
     */
    public static RouteTemplate createRouteTemplate(Design d, EnterWireJunction src, ExitWireJunction snk) {

        RouterLog.log("Routing template for " + src + " --> " + snk + " (omni BFS).", RouterLog.Level.INFO);
        RouterLog.indent();

        Tile srcIntTile = d.getDevice().getTile(src.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snk.getTileName());

        int snkTileX = snkIntTile.getTileXCoordinate();
        int snkTileY = snkIntTile.getTileYCoordinate();

        int srcTileX = srcIntTile.getTileXCoordinate();
        int srcTileY = srcIntTile.getTileYCoordinate();

        Set<ExitWireJunction> leadOuts = FabricBrowser.findReachableExits(d, src);

        HashSet<String> footprint = new HashSet<>();

        Queue<JunctionsTracer> queue = new LinkedList<>();
        queue.add(new JunctionsTracer(snk, 0));

        long tBegin = System.currentTimeMillis();
        while (true) {
            JunctionsTracer head = queue.remove();
            Tile headTile = d.getDevice().getTile(head.getJunction().getTileName());

            if (head.getDepth() > 1000)
                break;

            int distX = headTile.getTileXCoordinate() - srcTileX;
            int distY = headTile.getTileYCoordinate() - srcTileY;

            if (distX == 0 && distY == 0) {
                for (ExitWireJunction leadOut : leadOuts) {
                    if (leadOut.equals(head.getJunction())) {

                        RouteTemplate template = new RouteTemplate(d, src, snk);
                        JunctionsTracer trav = head;
                        while (trav.getParent() != null) {
                            template.pushExitWireJunction(d, trav.getJunction());
                            trav = trav.getParent();
                        }

                        /*
                        if (!FabricBrowser.isPathPossible(d, src, (ExitWireJunction) template.getTemplate(1))
                                || !FabricBrowser.isPathPossible(d, (EnterWireJunction) template.getTemplate(-2), snk))
                            continue;
                        */

                        RouterLog.log("Found template: " + template.hopSummary(), RouterLog.Level.INFO);
                        RouterLog.log("Junctions: " + template, RouterLog.Level.VERBOSE);
                        RouterLog.log("Template found at depth " + head.getDepth() + ".", RouterLog.Level.VERBOSE);
                        RouterLog.log("BFS search took " + (System.currentTimeMillis() - tBegin) + " ms.",
                                RouterLog.Level.VERBOSE);
                        RouterLog.indent(-1);
                        return template;
                    }
                }
            }

            Set<EnterWireJunction> fanOut = head.getJunction().isSnk()
                    ? FabricBrowser.findReachableEntrances(d, head.getJunction())
                    : FabricBrowser.getExitFanOut(d, head.getJunction());
            for (EnterWireJunction entrance : fanOut) {
                ExitWireJunction wireSrc = entrance.getSrcJunction(d);

                if (FabricBrowser.globalNodeFootprint.contains(wireSrc.getNodeName())
                        || footprint.contains(wireSrc.getNodeName()) || CustomRouter.isLocked(wireSrc.getNodeName())
                        || FabricBrowser.globalNodeFootprint.contains(entrance.getNodeName())
                        || footprint.contains(entrance.getNodeName()) || CustomRouter.isLocked(entrance.getNodeName()))
                    continue;

                queue.add(new JunctionsTracer(wireSrc, head, head.getDepth() + 1));
                footprint.add(wireSrc.getNodeName());
            }

        }

        RouterLog.indent(-1);
        RouterLog.log("Failed to determine routing templates.", RouterLog.Level.ERROR);

        return new RouteTemplate(d, src, snk);
    }

    /*
     * Systematically determine a (usually) conflict-free configuration of RouteTemplates for a single bus
     */
    public static ArrayList<RouteTemplate> createBussedRouteTemplates(Design d, ArrayList<EnterWireJunction> srcs,
                                                                      ArrayList<ExitWireJunction> snks) {
        int bitwidth = srcs.size();
        int rerouteCount = 0;
        int preemptCount = 0;

        ArrayList<RouteTemplate> templates = new ArrayList<>();
        ArrayList<Set<String>> banLists = new ArrayList<>();

        /*
        ArrayList<HashSet<TilePath>> activeSnkTilePaths = new ArrayList<>();
        for (int i = 0; i < bitwidth; i++)
            activeSnkTilePaths.add(null);
        */

        HashMap<Integer, Set<String>> srcSnkExclusives = new HashMap<>();

        Queue<Integer> queue = new LinkedList<>();

        for (int i = 0; i < bitwidth; i++) {
            templates.add(null);
            banLists.add(new HashSet<>());
            srcSnkExclusives.put(i, new HashSet<>());

            queue.add(i);
        }

        while (!queue.isEmpty()) {
            int bitIndex = queue.remove();

            EnterWireJunction src = srcs.get(bitIndex);
            ExitWireJunction snk = snks.get(bitIndex);
            Set<String> banList = banLists.get(bitIndex);

            RouteTemplate template;

            // Purge any old usages
            srcSnkExclusives.put(bitIndex, new HashSet<>());

            do {

                for (String node : banList)
                    CustomRouter.lock(node);

                template = createRouteTemplate(d, src, snk);
                template.setBitIndex(bitIndex);

                if (template.isEmpty())
                    return null;

                RouterLog.indent();

                ArrayList<TilePath> srcPathChoices = FabricBrowser.findTilePaths(d, src,
                        (ExitWireJunction) template.getTemplate(1));
                ArrayList<TilePath> snkPathChoices = FabricBrowser.findTilePaths(d,
                        (EnterWireJunction) template.getTemplate(-2), snk);

                RouterLog.indent(-1);

                // Set of nodes which this route must need to be possible
                Set<String> mustHavesSrc = deriveExclusiveNodes(srcPathChoices);
                Set<String> mustHavesSnk = deriveExclusiveNodes(snkPathChoices);

                ArrayList<Integer> conflictedBits = locateTemplateCollisions(mustHavesSrc, srcSnkExclusives);
                if (!conflictedBits.isEmpty()) {
                    rerouteCount += 1;
                    /*
                     * Make a decision based on cost:
                     * 1. Conflicted templates have lower cost: preempt them and reroute
                     * 2. Conflicted templates have higher cost: reroute ourselves
                     */
                    int costForPreemption = 0;
                    for (int b : conflictedBits) {
                        RouteTemplate t = templates.get(b);
                        if (t.getCost() > costForPreemption)
                            costForPreemption = t.getCost();
                    }

                    if (costForPreemption > template.getCost()) {
                        RouterLog.log("Conflicted detected at source: rerouting current template at a later time.",
                                RouterLog.Level.INFO);

                        // Ban this src exit since it is no accessible
                        banList.add(template.getTemplate(1).getNodeName());
                        template = null;

                        queue.add(bitIndex);
                        continue;
                    }
                    else {
                        for (int b : conflictedBits) {
                            preemptCount += 1;
                            RouteTemplate t = templates.get(b);
                            templates.set(b, null);

                            // Purge usages of preempted routes
                            srcSnkExclusives.put(b, new HashSet<>());
                            for (int i = 1; i < t.getTemplate().size() - 1; i++) {
                                CustomRouter.unlock(t.getTemplate(i).getNodeName());
                            }
                            //activeSnkTilePaths.set(b, null);

                            banLists.get(b).add(t.getTemplate(1).getNodeName());

                            // Add them back to queue to be re-routed once again
                            queue.add(b);
                        }

                        RouterLog.log("Conflicted detected at source: preempting routes for bits " + conflictedBits + ".",
                                RouterLog.Level.INFO);
                    }
                }

                // Do again for snk exclusives
                conflictedBits = locateTemplateCollisions(mustHavesSnk, srcSnkExclusives);
                if (!conflictedBits.isEmpty()) {
                    rerouteCount += 1;
                    int costForPreemption = 0;
                    for (int b : conflictedBits) {
                        RouteTemplate t = templates.get(b);
                        if (t.getCost() > costForPreemption)
                            costForPreemption = t.getCost();
                    }

                    if (costForPreemption > template.getCost()) {
                        RouterLog.log("Conflicted detected at sink: rerouting current template at a later time.",
                                RouterLog.Level.INFO);

                        // Ban this snk entrance since it is no accessible
                        banList.add(template.getTemplate(-3).getNodeName());
                        template = null;

                        queue.add(bitIndex);
                        continue;
                    }
                    else {
                        for (int b : conflictedBits) {
                            preemptCount += 1;
                            RouteTemplate t = templates.get(b);
                            templates.set(b, null);

                            // Purge usages of preempted routes
                            srcSnkExclusives.put(b, new HashSet<>());
                            for (int i = 1; i < t.getTemplate().size() - 1; i++) {
                                CustomRouter.unlock(t.getTemplate(i).getNodeName());
                            }
                            //activeSnkTilePaths.set(b, null);

                            banLists.get(b).add(t.getTemplate(-3).getNodeName());

                            // Add them back to queue to be re-routed once again
                            queue.add(b);
                        }

                        RouterLog.log("Conflicted detected at sink: preempting routes for bits " + conflictedBits + ".",
                                RouterLog.Level.INFO);
                    }

                }

                /*
                // Check to see if there are valid tile paths in sink - which is a high-congestion area
                {
                    ArrayList<TilePath> paths = new ArrayList<>();
                    for (int i = 0; i < bitwidth; i++)
                        paths.add(null);
                    ArrayList<HashSet<TilePath>> allPaths = new ArrayList<>();
                    for (HashSet<TilePath> pathChoices : activeSnkTilePaths) {
                        if (pathChoices != null)
                            allPaths.add(pathChoices);
                    }
                    allPaths.add(new HashSet<>(snkPathChoices));

                    if (deriveValidTilePaths(0, paths, new HashSet<>(), allPaths) == null) {
                        rerouteCount += 1;

                        // Failure condition slipped through exclusivity checks - yield self at a later time
                        RouterLog.log("Unroutable configuration detected at sink: rerouting current template at a later time.",
                                RouterLog.Level.INFO);

                        banList.add(template.getTemplate(-2).getNodeName());
                        template = null;

                        queue.add(bitIndex);
                        continue;
                    }
                }
                */

                templates.set(bitIndex, template);

                for (WireJunction junction : template.getTemplate()) {
                    CustomRouter.lock(junction.getNodeName());
                }

                srcSnkExclusives.put(bitIndex, mustHavesSrc);
                srcSnkExclusives.get(bitIndex).addAll(mustHavesSnk);
                //activeSnkTilePaths.set(bitIndex, new HashSet<>(snkPathChoices));

            } while (template == null);

            for (String node : banList)
                CustomRouter.unlock(node);
        }

        RouterLog.log("Found all templates for bus:", RouterLog.Level.INFO);
        RouterLog.indent();
        for (int i = 0; i < bitwidth; i++) {
            RouteTemplate result = templates.get(i);
            RouterLog.log("b" + i + ":\t" + result.hopSummary(), RouterLog.Level.INFO);
        }
        RouterLog.indent(-1);

        RouterLog.log(rerouteCount + " templates were rerouted. " + preemptCount + " of which are rerouted due to preemption.",
                RouterLog.Level.INFO);

        return templates;
    }

    /*
     * Recursive function finding (usually worst-case) any valid tile paths configuration
     */
    private static ArrayList<TilePath> deriveValidTilePaths(int depth, ArrayList<TilePath> validPathsState,
                                                            HashSet<String> footprint,
                                                            ArrayList<HashSet<TilePath>> allPaths) {
        if (depth == allPaths.size())
            return validPathsState;

        HashSet<TilePath> paths = allPaths.get(depth);
        if (paths == null || paths.isEmpty())
            return null;

        for (TilePath candidate : paths) {
            boolean isValid = true;
            for (String nodeName : candidate.getNodePath()) {
                if (footprint.contains(nodeName)) {
                    isValid = false;
                    break;
                }
            }

            if (isValid) {
                HashSet<String> nextDepthFootprint = new HashSet<>(footprint);
                nextDepthFootprint.addAll(candidate.getNodePath());

                validPathsState.set(depth, candidate);

                ArrayList<TilePath> results = deriveValidTilePaths(depth + 1, validPathsState, nextDepthFootprint,
                        allPaths);
                if (results != null)
                    return results;
            }
        }

        return null;
    }

    /*
     * Uses "easing" method to derive the "best-worst-case" configuration of a set of potential routes
     */
    public static boolean deriveBestSinkPaths(Design d, ArrayList<CustomRoute> routes) {

        // Highest cost possible
        int threshMax = 0;
        // Lost cost possible (max of min's across each bit)
        int threshMin = 0;

        ArrayList<HashSet<TilePath>> allPaths = new ArrayList<>();
        {
            for (CustomRoute route : routes) {
                allPaths.add(new HashSet<>(route.getPathSub(-1)));

                int min = 99;
                for (TilePath path : route.getPathSub(-1)) {
                    if (path.getCost() > threshMax)
                        threshMax = path.getCost();
                    if (path.getCost() < min)
                        min = path.getCost();
                }

                if (min > threshMin)
                    threshMin = min;
            }
        }

        for (int threshold = threshMin; threshold <= threshMax; threshold++) {
            ArrayList<HashSet<TilePath>> candidatePool = new ArrayList<>();
            for (HashSet<TilePath> pathChoices : allPaths) {
                HashSet<TilePath> bitCandidates = new HashSet<>();
                for (TilePath path : pathChoices) {
                    if (path.getCost() <= threshold)
                        bitCandidates.add(path);
                }
                candidatePool.add(bitCandidates);
            }

            for (int i = 0; i < routes.size(); i++) {

                ArrayList<HashSet<TilePath>> candidates = new ArrayList<>(candidatePool);

                HashSet<TilePath> threshSet = new HashSet<>();
                for (TilePath path : candidates.get(i)) {
                    if (path.getCost() <= threshold)
                        threshSet.add(path);
                }
                candidates.set(i, threshSet);

                ArrayList<TilePath> results = new ArrayList<>();
                for (int j = 0; j < routes.size(); j++)
                    results.add(null);
                results = deriveValidTilePaths(0, results, new HashSet<>(), candidates);

                if (results != null) {
                    for (int j = 0; j < routes.size(); j++)
                        routes.get(j).setPath(-1, results.get(j));

                    RouterLog.log("Route found at a worst-case cost of " + threshold + ".", RouterLog.Level.INFO);
                    return true;
                }
            }
        }

        RouterLog.log("Deadlock detected. No sink tile paths configuration is possible.", RouterLog.Level.ERROR);
        RoutingErrorSalvage.deriveBestSinkPathsDeadlockReport.report(routes);

        return false;
    }

    public static boolean routeContention(Design d, ArrayList<CustomRoute> routes) {
        int preemptCount = 0;
        int liveLockCount = 0;

        int[] deflectionCount = new int[routes.size()];
        Queue<Pair<Integer, Integer>> routeQueue = new LinkedList<>();

        for (int i = 0; i < routes.size(); i++) {
            for (int j = 0; j < routes.get(i).getRoute().size() - 1; j++) {
                routeQueue.add(new ImmutablePair<>(i, j));
            }
        }

        // TODO: Doesn't produce absolutely optimal solutions
        while (!routeQueue.isEmpty() && liveLockCount < 9999) {
            Pair next = routeQueue.remove();
            liveLockCount += 1;

            int bitIndex = (int) next.getLeft();
            int pathIndex = (int) next.getRight();

            TilePath candidatePath = routes.get(bitIndex).getNextPossiblePath(pathIndex);

            for (Pair<Integer, Integer> conflict : locateTilePathCollisions(candidatePath, routes)) {
                // During route contention, always preempt conflicts
                CustomRoute conflictedRoute = routes.get(conflict.getLeft());
                conflictedRoute.removePath(conflict.getRight());

                preemptCount += 1;
                deflectionCount[bitIndex] += 1;
                routeQueue.add(conflict);
            }

            routes.get(bitIndex).setPath(pathIndex, candidatePath);
        }

        if (liveLockCount >= 9999) {
            RouterLog.log("Route contention aborted (live lock detected).", RouterLog.Level.ERROR);

            RoutingErrorSalvage.routeContentionLiveLockReport.report(deflectionCount, routes);
            return false;
        }

        RouterLog.log(preemptCount + " tile paths were rerouted due to contention.", RouterLog.Level.INFO);


        return true;
    }

}
