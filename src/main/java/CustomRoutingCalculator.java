import com.kenai.jffi.Array;
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
     * Locks RouteTemplate without checking for conflicts
     */
    public static void lockRouteTemplate(RouteTemplate template) {
        for (String nodeName : template.getUsage())
            CustomRouter.lock(nodeName);
    }

    public static boolean isRouteTemplateConflicted(RouteTemplate template) {
        // Source and sink junctions are assumed to be unlocked
        for (int i = 1; i < template.getTemplate().size() - 1; i++) {
            if (CustomRouter.isLocked(template.getTemplate().get(i).getNodeName()))
                return true;
        }

        return false;
    }

    /*
     * Finds, out of a family of RouteTemplate's, which RouteTemplate is colliding with the family
     * 1. Exists: return indexes of collisions
     * 2. DNE: return -1
     */
    private static ArrayList<Integer> locateTemplateCollisions(Set<String> candidateSet, HashMap<Integer, Set<String>> usages) {
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

    public static void lockTilePath(TilePath path) {
        for (String nodeName : path.getNodePath())
            CustomRouter.lock(nodeName);
    }

    public static boolean isTilePathConflicted(TilePath path) {
        // Source and sink junctions are assumed to be unlocked
        for (int i = 1; i < path.getNodePath().size() - 1; i++) {
            if (CustomRouter.isLocked(path.getNodeName(i)))
                return true;
        }

        return false;
    }

    public static RouteTemplate createRouteTemplate(Design d, EnterWireJunction src, ExitWireJunction snk) {

        RouterLog.log("Routing template for " + src + " --> " + snk + " (omni BFS).", RouterLog.Level.INFO);
        RouterLog.indent();

        Tile srcIntTile = d.getDevice().getTile(src.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snk.getTileName());

        int snkTileX = snkIntTile.getTileXCoordinate();
        int snkTileY = snkIntTile.getTileYCoordinate();

        int srcTileX = srcIntTile.getTileXCoordinate();
        int srcTileY = srcIntTile.getTileYCoordinate();

        Set<ExitWireJunction> leadOuts = FabricBrowser.getEntranceFanOut(d, src);

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

            for (EnterWireJunction entrance : FabricBrowser.getExitFanOut(d, head.getJunction())) {
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

    public static boolean routeContention(Design d, ArrayList<CustomRoute> routes) {
        boolean allRoutesArrivedToTile = false;
        while (!allRoutesArrivedToTile) {
            allRoutesArrivedToTile = true;

            for (int i = 0; i < routes.size(); i++) {
                CustomRoute route = routes.get(i);
                int nextPathIndex = route.getNextBlankPathIndex();

                if (nextPathIndex == -1)
                    continue;

                allRoutesArrivedToTile = false;

                // TODO: May live-lock
                TilePath candidatePath;
                do {
                    candidatePath = route.getNextPossiblePath();

                    if (!isTilePathConflicted(candidatePath)) {
                        lockTilePath(candidatePath);
                        break;
                    }

                } while(true);

                route.setAsNextPath(candidatePath);
            }
        }


        return true;
    }

}
