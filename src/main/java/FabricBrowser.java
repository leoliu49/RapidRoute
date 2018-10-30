import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.PIP;
import com.xilinx.rapidwright.device.Tile;

import java.util.*;

public class FabricBrowser {


    public static HashMap<String, ArrayList<PIP>> pipCache = new HashMap<>();
    public static HashMap<String, Set<String>> exitFanOutCache = new HashMap<>();
    public static HashMap<String, Set<String>> entranceFanOutCache = new HashMap<>();

    public static final int TILE_TRAVERSAL_MAX_DEPTH = 4;
    private static class NodeDepthPair {
        /*
         * Internal class used to track depth of BFS searches
         */
        private String nodeName;
        private int depth;

        public NodeDepthPair(String nodeName) {
            this.nodeName = nodeName;
            depth = 0;
        }

        public NodeDepthPair(String nodeName, int depth) {
            this.nodeName = nodeName;
            this.depth = depth;
        }

        public String getNodeName() {
            return nodeName;
        }

        public int getDepth() {
            return depth;
        }
    }

    public static Set<String> globalNodeFootprint = new HashSet<>();

    public static void setGlobalNodeFootprint(Set<String> footprint) {
        globalNodeFootprint = footprint;
    }

    public static ArrayList<PIP> getTilePIPs(Design d, String tileName) {
        if (!pipCache.containsKey(tileName))
            pipCache.put(tileName, d.getDevice().getTile(tileName).getPIPs());
        return pipCache.get(tileName);
    }

    public static Set<PIP> getFwdPIPs(Design d, String tileName, String nodeName) {
        Set<PIP> pipSet = new HashSet<>();

        for (PIP pip : getTilePIPs(d, tileName)) {
            if (RouteUtil.getPIPNodeName(tileName, pip.getStartWireName()).equals(nodeName))
                pipSet.add(pip);
        }
        return pipSet;
    }

    public static Set<PIP> getBkwdPIPs(Design d, String tileName, String nodeName) {
        Set<PIP> pipSet = new HashSet<>();

        for (PIP pip : getTilePIPs(d, tileName)) {
            if (RouteUtil.getPIPNodeName(tileName, pip.getEndWireName()).equals(nodeName))
                pipSet.add(pip);
        }
        return pipSet;
    }

    /*
     * Find all entering wire junctions that can be routed to the exit junction
     *   Checks cache first before searching
     */
    public static Set<EnterWireJunction> getExitFanOut(Design d, ExitWireJunction exit) {

        if (!exitFanOutCache.containsKey(exit.getWireName()))
            updateExitFanOut(d, exit.getTileName(), exit.getWireName());

        Set<EnterWireJunction> entrances = new HashSet<>();
        String tileName = exit.getTileName();
        for (String wireName : exitFanOutCache.get(exit.getWireName())) {
            entrances.add(new EnterWireJunction(d, tileName, wireName));
        }
        return entrances;
    }

    /*
     * Find all exiting wire junctions that can be routed from the entrance junction
     *   Checks cache first before searching
     */
    public static Set<ExitWireJunction> getEntranceFanOut(Design d, EnterWireJunction entrance) {

        if (!entranceFanOutCache.containsKey(entrance.getWireName()))
            updateEntranceFanOut(d, entrance.getTileName(), entrance.getWireName());

        Set<ExitWireJunction> exits = new HashSet<>();
        String tileName = entrance.getTileName();
        for (String wireName : entranceFanOutCache.get(entrance.getWireName())) {
            exits.add(new ExitWireJunction(d, tileName, wireName));
        }

        return exits;
    }

    /*
     * BFS search for all entering wires that can be routed to the exit junction
     *   Results are cached in exitFanOutCache, replacing previous cache if there are any
     */
    private static void updateExitFanOut(Design d, String tileName, String exitWireName) {

        Set<String> results = new HashSet<>();

        Queue<NodeDepthPair> queue = new LinkedList<>();
        queue.add(new NodeDepthPair(tileName + "/" + exitWireName));

        HashSet<String> footprint = new HashSet<>();

        while (!queue.isEmpty()) {
            NodeDepthPair trav = queue.remove();

            if (trav.getDepth() >= TILE_TRAVERSAL_MAX_DEPTH)
                break;

            for (PIP pip : getBkwdPIPs(d, tileName, trav.getNodeName())) {
                String nextNodeName = RouteUtil.getPIPNodeName(tileName, pip.getStartWireName());

                WireDirection dir = RouteUtil.extractEnterWireDirection(d, tileName, pip.getStartWireName());
                int wireLength = RouteUtil.extractEnterWireLength(d, tileName, pip.getStartWireName());

                if (footprint.contains(nextNodeName))
                    continue;

                if (dir != null && dir!= WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(nextNodeName))
                    results.add(pip.getStartWireName());
                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName))
                    queue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));

                footprint.add(nextNodeName);
            }
        }

        exitFanOutCache.put(exitWireName, results);
    }

    /*
     * BFS search for all exiting wires that can be routed from the entrance junction
     *   Results are cached in entranceFanOutCache, replacing previous cache if there are any
     */
    private static void updateEntranceFanOut(Design d, String tileName, String entranceWireName) {

        Set<String> results = new HashSet<>();

        Queue<NodeDepthPair> queue = new LinkedList<>();
        queue.add(new NodeDepthPair(tileName + "/" + entranceWireName));

        HashSet<String> footprint = new HashSet<>();

        while (!queue.isEmpty()) {
            NodeDepthPair trav = queue.remove();

            if (trav.getDepth() >= TILE_TRAVERSAL_MAX_DEPTH)
                break;

            for (PIP pip : getFwdPIPs(d, tileName, trav.getNodeName())) {
                String nextNodeName = RouteUtil.getPIPNodeName(tileName, pip.getEndWireName());

                WireDirection dir = RouteUtil.extractExitWireDirection(d, tileName, pip.getEndWireName());
                int wireLength = RouteUtil.extractExitWireLength(d, tileName, pip.getEndWireName());

                if (footprint.contains(nextNodeName))
                    continue;

                if (dir != null && dir != WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(nextNodeName))
                    results.add(pip.getEndWireName());
                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName))
                    queue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));

                footprint.add(nextNodeName);
            }
        }

        entranceFanOutCache.put(entranceWireName, results);
    }

    /*
     * Conduct BFS for all entrances to exit junction independent of fan-out caches
     *   Takes into consideration the router global footprint and any locked nodes
     */
    public static Set<EnterWireJunction> findReachableEntrances(Design d, ExitWireJunction exit) {
        Set<EnterWireJunction> results = new HashSet<>();
        String tileName = exit.getTileName();

        Queue<NodeDepthPair> queue = new LinkedList<>();
        queue.add(new NodeDepthPair(exit.getNodeName()));

        HashSet<String> footprint = new HashSet<>();

        while (!queue.isEmpty()) {
            NodeDepthPair trav = queue.remove();

            if (trav.getDepth() >= TILE_TRAVERSAL_MAX_DEPTH)
                break;

            for (PIP pip : getBkwdPIPs(d, exit.getTileName(), trav.getNodeName())) {
                String nextNodeName = RouteUtil.getPIPNodeName(tileName, pip.getStartWireName());

                WireDirection dir = RouteUtil.extractEnterWireDirection(d, tileName, pip.getStartWireName());
                int wireLength = RouteUtil.extractEnterWireLength(d, tileName, pip.getStartWireName());

                if (globalNodeFootprint.contains(nextNodeName) || footprint.contains(nextNodeName)
                        || CustomRouter.isLocked(nextNodeName))
                    continue;

                if (dir != null && dir != WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(nextNodeName))
                    results.add(new EnterWireJunction(d, tileName, pip.getStartWireName()));
                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName))
                    queue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));

                footprint.add(nextNodeName);
            }
        }

        return results;
    }

    /*
     * Conduct BFS for all exits from entrance junction independent of fan-out caches
     *   Takes into consideration the router global footprint and any locked nodes
     */
    public static Set<ExitWireJunction> findReachableExits(Design d, EnterWireJunction entrance) {
        Set<ExitWireJunction> results = new HashSet<>();
        String tileName = entrance.getTileName();

        Queue<NodeDepthPair> queue = new LinkedList<>();
        queue.add(new NodeDepthPair(entrance.getNodeName()));

        HashSet<String> footprint = new HashSet<>();

        while (!queue.isEmpty()) {
            NodeDepthPair trav = queue.remove();

            if (trav.getDepth() >= TILE_TRAVERSAL_MAX_DEPTH)
                continue;

            for (PIP pip : getFwdPIPs(d, tileName, trav.getNodeName())) {
                String nextNodeName = RouteUtil.getPIPNodeName(tileName, pip.getEndWireName());

                WireDirection dir = RouteUtil.extractExitWireDirection(d, tileName, pip.getEndWireName());
                int wireLength = RouteUtil.extractExitWireLength(d, tileName, pip.getEndWireName());

                if (globalNodeFootprint.contains(nextNodeName) || footprint.contains(nextNodeName)
                        || CustomRouter.isLocked(nextNodeName))
                    continue;

                if (dir != null && dir != WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(nextNodeName))
                    results.add(new ExitWireJunction(d, tileName, pip.getEndWireName()));
                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName))
                    queue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));

                footprint.add(nextNodeName);
            }
        }

        return results;
    }

    /*
     * BFS search for all possible INT tile paths from entrance to exit, that are sufficiently fast
     *   Returned list is in order of lowest-to-highest cost
     */
    public static ArrayList<TilePath> findTilePaths(Design d, EnterWireJunction entrance,
                                                    ExitWireJunction exit) {
        ArrayList<TilePath> results = new ArrayList<>();

        // Not applicable unless entrance and exit are on the same INT tile.
        if (!entrance.getTileName().equals(exit.getTileName()))
            return results;

        String tileName = entrance.getTileName();

        Queue<TilePath> queue = new LinkedList<>();
        queue.add(new TilePath(entrance, exit));

        while (!queue.isEmpty()) {
            TilePath trav = queue.remove();

            if (trav.getCost() >= TILE_TRAVERSAL_MAX_DEPTH + 1)
                break;

            for (PIP pip : getFwdPIPs(d, tileName, trav.getNodeName(-2))) {
                String nextNodeName = RouteUtil.getPIPNodeName(tileName, pip.getEndWireName());

                if (nextNodeName.equals(exit.getNodeName())) {
                    results.add(new TilePath(trav));
                }
                else if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName)) {

                    if (globalNodeFootprint.contains(nextNodeName)
                            || (CustomRouter.isLocked(nextNodeName)))
                        continue;

                    TilePath travCopy = new TilePath(trav);

                    // To prevent cycles in buffer traversal, don't queue previously traversed buffers
                    if (travCopy.addNode(nextNodeName))
                        queue.add(travCopy);
                }
            }
        }

        RouterLog.log("Found " + results.size() + " INT tile paths for " + entrance + " --> " + exit + ".",
                RouterLog.Level.INFO);
        if (!results.isEmpty()) {
            RouterLog.indent();
            RouterLog.log("Minimum cost of tile paths is " + results.get(0).getCost() + ".", RouterLog.Level.VERBOSE);
            RouterLog.indent(-1);
        }

        return results;
    }

    /*
     * Same as above, with custom max traversal depth
     */
    public static ArrayList<TilePath> findTilePaths(Design d, int maxDepth, EnterWireJunction entrance,
                                                    ExitWireJunction exit) {
        ArrayList<TilePath> results = new ArrayList<>();

        // Not applicable unless entrance and exit are on the same INT tile.
        if (!entrance.getTileName().equals(exit.getTileName()))
            return results;

        String tileName = entrance.getTileName();

        Queue<TilePath> queue = new LinkedList<>();
        queue.add(new TilePath(entrance, exit));

        while (!queue.isEmpty()) {
            TilePath trav = queue.remove();

            if (trav.getCost() >= maxDepth + 1)
                break;

            for (PIP pip : getFwdPIPs(d, tileName, trav.getNodeName(-2))) {
                String nextNodeName = RouteUtil.getPIPNodeName(tileName, pip.getEndWireName());

                if (nextNodeName.equals(exit.getNodeName())) {
                    results.add(new TilePath(trav));
                }
                else if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName)) {

                    if (globalNodeFootprint.contains(nextNodeName)
                            || (CustomRouter.isLocked(nextNodeName)))
                        continue;

                    TilePath travCopy = new TilePath(trav);

                    // To prevent cycles in buffer traversal, don't queue previously traversed buffers
                    if (travCopy.addNode(nextNodeName))
                        queue.add(travCopy);
                }
            }
        }

        RouterLog.log("Found " + results.size() + " INT tile paths for " + entrance + " --> " + exit + ".",
                RouterLog.Level.INFO);
        if (!results.isEmpty()) {
            RouterLog.indent();
            RouterLog.log("Minimum cost of tile paths is " + results.get(0).getCost() + ".", RouterLog.Level.VERBOSE);
            RouterLog.indent(-1);
        }

        return results;
    }

    /*
     * Verifies that there is a path between entrance and exit
     */
    public static boolean isPathPossible(Design d, EnterWireJunction entrance, ExitWireJunction exit) {

        // Not applicable unless entrance and exit are on the same INT tile.
        if (!entrance.getTileName().equals(exit.getTileName()))
            return false;

        String tileName = entrance.getTileName();

        Queue<NodeDepthPair> queue = new LinkedList<>();
        queue.add(new NodeDepthPair(entrance.getNodeName()));

        HashSet<String> footprint = new HashSet<>();

        while (!queue.isEmpty()) {
            NodeDepthPair trav = queue.remove();

            if (trav.getDepth() >= TILE_TRAVERSAL_MAX_DEPTH)
                return false;

            for (PIP pip : getFwdPIPs(d, tileName, trav.getNodeName())) {
                String nextNodeName = RouteUtil.getPIPNodeName(tileName, pip.getEndWireName());

                if (nextNodeName.equals(exit.getNodeName())) {
                    return true;
                }

                if (globalNodeFootprint.contains(nextNodeName) || footprint.contains(nextNodeName)
                        || CustomRouter.isLocked(nextNodeName))
                    continue;

                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName))
                    queue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));

                footprint.add(nextNodeName);
            }
        }


        return false;
    }
}
