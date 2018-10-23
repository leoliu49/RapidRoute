import com.kenai.jffi.Array;
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
    public static Set<String> deriveExclusiveNodes(ArrayList<TilePath> pathChoices) {
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
                    break;
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
    public static void lockTilePath(TilePath path) {
        for (String nodeName : path.getNodePath())
            CustomRouter.lock(nodeName);
    }

    public static boolean isTilePathConflicted(TilePath path) {
        // Source and sink junctions are assumed to be unlocked
        for (int i = 1; i < path.getNodePath().size() - 1; i++) {
            if (CustomRouter.isLocked(path.getNodeName(i))) {
                System.out.println(path.getNodeName(i));
                return true;
            }
        }

        return false;
    }
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
                        || footprint.contains(wireSrc.getNodeName()) || CustomRouter.isLocked(wireSrc.getNodeName()))
                    continue;

                queue.add(new JunctionsTracer(wireSrc, head, head.getDepth() + 1));
                footprint.add(wireSrc.getNodeName());
            }

        }

        RouterLog.indent(-1);
        RouterLog.log("Failed to determine routing templates.", RouterLog.Level.ERROR);

        return new RouteTemplate(d, src, snk);
    }

    public static ArrayList<RouteTemplate> createBussedRouteTemplates(Design d, ArrayList<EnterWireJunction> srcs,
                                                                      ArrayList<ExitWireJunction> snks) {
        int bitwidth = srcs.size();
        int rerouteCount = 0;
        int preemptCount = 0;

        ArrayList<RouteTemplate> results = new ArrayList<>();
        ArrayList<Set<String>> banLists = new ArrayList<>();

        HashMap<Integer, Set<String>> srcSnkExclusives = new HashMap<>();

        Queue<Integer> queue = new LinkedList<>();

        for (int i = 0; i < bitwidth; i++) {
            results.add(null);
            banLists.add(new HashSet<>());
            srcSnkExclusives.put(i, new HashSet<>());

            queue.add(i);
        }

        while (!queue.isEmpty()) {
            int bitIndex = queue.remove();

            EnterWireJunction src = srcs.get(bitIndex);
            ExitWireJunction snk = snks.get(bitIndex);
            Set<String> banList = banLists.get(bitIndex);

            RouteTemplate template = results.get(bitIndex);

            for (String node : banList)
                CustomRouter.lock(node);

            // Purge any old usages
            srcSnkExclusives.put(bitIndex, new HashSet<>());

            do {
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
                        RouteTemplate t = results.get(b);
                        if (t.getCost() > costForPreemption)
                            costForPreemption = t.getCost();
                    }

                    if (costForPreemption > template.getCost()) {
                        template = null;

                        RouterLog.log("Conflicted detected at source: rerouting current template at a later time.",
                                RouterLog.Level.INFO);

                        // Ban this src exit since it is no accessible
                        banList.add(template.getTemplate(1).getNodeName());

                        queue.add(bitIndex);
                        continue;
                    }

                    preemptCount += 1;
                    for (int b : conflictedBits) {
                        RouteTemplate t = results.get(b);
                        results.set(b, null);

                        // Purge usages of preempted routes
                        srcSnkExclusives.put(b, new HashSet<>());
                        banLists.get(b).add(t.getTemplate(1).getNodeName());

                        // Add them back to queue to be re-routed once again
                        queue.add(b);
                    }

                    RouterLog.log("Conflicted detected at source: preempting routes for bits " + conflictedBits + ".",
                            RouterLog.Level.INFO);
                }

                // Do again for snk exclusives
                conflictedBits = locateTemplateCollisions(mustHavesSnk, srcSnkExclusives);
                if (!conflictedBits.isEmpty()) {
                    rerouteCount += 1;
                    int costForPreemption = 0;
                    for (int b : conflictedBits) {
                        RouteTemplate t = results.get(b);
                        if (t.getCost() > costForPreemption)
                            costForPreemption = t.getCost();
                    }

                    if (costForPreemption > template.getCost()) {
                        template = null;

                        RouterLog.log("Conflicted detected at sink: rerouting current template at a later time.",
                                RouterLog.Level.INFO);

                        // Ban this snk entrance since it is no accessible
                        banList.add(template.getTemplate(-2).getNodeName());

                        queue.add(bitIndex);
                        continue;
                    }

                    preemptCount += 1;
                    for (int b : conflictedBits) {
                        RouteTemplate t = results.get(b);
                        results.set(b, null);

                        banLists.get(b).add(t.getTemplate(-2).getNodeName());

                        // Add them back to queue to be re-routed once again
                        queue.add(b);
                    }

                    RouterLog.log("Conflicted detected at sink: preempting routes for bits " + conflictedBits + ".",
                            RouterLog.Level.INFO);

                }

                results.set(bitIndex, template);

                srcSnkExclusives.put(bitIndex, mustHavesSrc);
                srcSnkExclusives.get(bitIndex).addAll(mustHavesSnk);

            } while (template == null);

            for (String node : banList)
                CustomRouter.unlock(node);
        }

        RouterLog.log("Found all templates for bus:", RouterLog.Level.INFO);
        RouterLog.indent();
        for (int i = 0; i < bitwidth; i++) {
            RouteTemplate result = results.get(i);
            RouterLog.log("b" + i + ":\t" + result.hopSummary(), RouterLog.Level.INFO);
        }
        RouterLog.indent(-1);

        RouterLog.log(rerouteCount + " templates were rerouted. " + preemptCount + " of which are rerouted due to preemption.",
                RouterLog.Level.INFO);

        return results;
    }

    public static boolean routeContention(Design d, ArrayList<CustomRoute> routes) {
        int preemptCount = 0;

        Queue<Pair<Integer, Integer>> routeQueue = new LinkedList<>();

        for (int i = 0; i < routes.size(); i++) {
            for (int j = 0; j < routes.get(i).getRoute().size(); j++) {
                routeQueue.add(new ImmutablePair<>(i, j));
            }
        }

        // TODO: May still live-lock (?)
        // TODO: Doesn't produce absolutely optimal solutions
        while (!routeQueue.isEmpty()) {
            Pair next = routeQueue.remove();
            int bitIndex = (int) next.getLeft();
            int pathIndex = (int) next.getRight();

            TilePath candidatePath = routes.get(bitIndex).getNextPossiblePath(pathIndex);

            for (Pair<Integer, Integer> conflict : locateTilePathCollisions(candidatePath, routes)) {
                // During route contention, always preempt conflicts
                CustomRoute conflictedRoute = routes.get(conflict.getLeft());
                conflictedRoute.removePath(conflict.getRight());

                preemptCount += 1;
                routeQueue.add(conflict);
            }

            routes.get(bitIndex).setPath(pathIndex, candidatePath);
        }

        RouterLog.log(preemptCount + " tile paths were rerouted due to contention.", RouterLog.Level.INFO);


        return true;
    }

}
