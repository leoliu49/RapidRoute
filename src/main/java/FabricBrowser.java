import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.PIP;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class FabricBrowser {

    private static class FanOutBundle {
        /*
         * Internal class to track cost of entrance-to-exit fan out costs
         */
        private String wireName;
        private int pathCost;

        public FanOutBundle(String wireName, int pathCost) {
            this.wireName = wireName;
            this.pathCost = pathCost;
        }

        public String getWireName() {
            return wireName;
        }

        public int getPathCost() {
            return pathCost;
        }
    }

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

    public static final HashMap<String, ArrayList<PIP>> pipCache = new HashMap<>();
    public static final HashMap<String, Set<FanOutBundle>> exitFanOutCache = new HashMap<>();
    public static final HashMap<String, Set<FanOutBundle>> entranceFanOutCache = new HashMap<>();

    public static final int TILE_TRAVERSAL_MAX_DEPTH = 4;

    public static ArrayList<PIP> getTilePIPs(Design d, String tileName) {
        synchronized (pipCache) {
            if (!pipCache.containsKey(tileName))
                pipCache.put(tileName, d.getDevice().getTile(tileName).getPIPs());
            return pipCache.get(tileName);
        }
    }

    public static Set<PIP> getFwdPIPs(Design d, String tileName, String nodeName) {
        Set<PIP> pipSet = new HashSet<>();

        synchronized (pipCache) {
            for (PIP pip : getTilePIPs(d, tileName)) {
                if (RouteUtil.getPIPNodeName(tileName, pip.getStartWireName()).equals(nodeName))
                    pipSet.add(pip);
            }
        }
        return pipSet;
    }

    public static Set<PIP> getBkwdPIPs(Design d, String tileName, String nodeName) {
        Set<PIP> pipSet = new HashSet<>();

        synchronized (pipCache) {
            for (PIP pip : getTilePIPs(d, tileName)) {
                if (RouteUtil.getPIPNodeName(tileName, pip.getEndWireName()).equals(nodeName))
                    pipSet.add(pip);
            }
        }
        return pipSet;
    }

    /*
     * Find all entering wire junctions that can be routed to the exit junction
     *   Checks cache first before searching
     */
    public static Set<EnterWireJunction> getExitFanOut(Design d, ExitWireJunction exit) {

        synchronized (exitFanOutCache) {
            if (!exitFanOutCache.containsKey(exit.getWireName()))
                updateExitFanOut(d, exit.getTileName(), exit.getWireName());

            Set<EnterWireJunction> entrances = new LinkedHashSet<>();
            String tileName = exit.getTileName();
            for (FanOutBundle bundle : exitFanOutCache.get(exit.getWireName())) {
                EnterWireJunction entrance = new EnterWireJunction(d, tileName, bundle.getWireName());
                entrance.setTilePathCost(bundle.getPathCost());
                entrances.add(entrance);
            }
            return entrances;
        }
    }

    /*
     * Find all exiting wire junctions that can be routed from the entrance junction
     *   Checks cache first before searching
     */
    public static synchronized Set<ExitWireJunction> getEntranceFanOut(Design d, EnterWireJunction entrance) {

        synchronized (entranceFanOutCache) {
            if (!entranceFanOutCache.containsKey(entrance.getWireName()))
                updateEntranceFanOut(d, entrance.getTileName(), entrance.getWireName());

            Set<ExitWireJunction> exits = new LinkedHashSet<>();
            String tileName = entrance.getTileName();
            for (FanOutBundle bundle : entranceFanOutCache.get(entrance.getWireName())) {
                ExitWireJunction exit = new ExitWireJunction(d, tileName, bundle.getWireName());
                exit.setTilePathCost(bundle.getPathCost());
                exits.add(exit);
            }

            return exits;
        }
    }

    /*
     * BFS search for all entering wires that can be routed to the exit junction
     *   Results are cached in exitFanOutCache, replacing previous cache if there are any
     */
    private static void updateExitFanOut(Design d, String tileName, String exitWireName) {

        Set<FanOutBundle> results = new LinkedHashSet<>();

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

                if (dir != null && dir!= WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(nextNodeName)) {
                    results.add(new FanOutBundle(pip.getStartWireName(), trav.getDepth()));
                }
                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName))
                    queue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));

                footprint.add(nextNodeName);
            }
        }

        synchronized (exitFanOutCache) {
            exitFanOutCache.put(exitWireName, results);
        }
    }

    /*
     * BFS search for all exiting wires that can be routed from the entrance junction
     *   Results are cached in entranceFanOutCache, replacing previous cache if there are any
     */
    private static void updateEntranceFanOut(Design d, String tileName, String entranceWireName) {

        Set<FanOutBundle> results = new LinkedHashSet<>();

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

                if (dir != null && dir != WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(nextNodeName)) {
                    results.add(new FanOutBundle(pip.getEndWireName(), trav.getDepth()));
                }
                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName))
                    queue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));

                footprint.add(nextNodeName);
            }
        }

        synchronized (entranceFanOutCache) {
            entranceFanOutCache.put(entranceWireName, results);
        }
    }

    /*
     * Conduct BFS for all entrances to exit junction independent of fan-out caches
     *   Takes into consideration the router global footprint and any locked nodes
     */
    public static Set<EnterWireJunction> findReachableEntrances(Design d, ExitWireJunction exit) {
        return findReachableEntrances(d, TILE_TRAVERSAL_MAX_DEPTH, exit);
    }

    public static Set<EnterWireJunction> findReachableEntrances(Design d, int maxDepth, ExitWireJunction exit) {
        Set<EnterWireJunction> results = new LinkedHashSet<>();
        String tileName = exit.getTileName();

        Queue<NodeDepthPair> queue = new LinkedList<>();
        queue.add(new NodeDepthPair(exit.getNodeName()));

        HashSet<String> footprint = new HashSet<>();

        while (!queue.isEmpty()) {
            NodeDepthPair trav = queue.remove();

            if (trav.getDepth() >= maxDepth)
                break;

            for (PIP pip : getBkwdPIPs(d, exit.getTileName(), trav.getNodeName())) {
                String nextNodeName = RouteUtil.getPIPNodeName(tileName, pip.getStartWireName());

                WireDirection dir = RouteUtil.extractEnterWireDirection(d, tileName, pip.getStartWireName());
                int wireLength = RouteUtil.extractEnterWireLength(d, tileName, pip.getStartWireName());

                if (footprint.contains(nextNodeName) || RouteForge.isLocked(nextNodeName))
                    continue;

                if (dir != null && dir != WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(nextNodeName)) {
                    EnterWireJunction entrance = new EnterWireJunction(d, tileName, pip.getStartWireName());
                    entrance.setTilePathCost(trav.getDepth());
                    results.add(entrance);
                }
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
        return findReachableExits(d, TILE_TRAVERSAL_MAX_DEPTH, entrance);
    }

    public static Set<ExitWireJunction> findReachableExits(Design d, int maxDepth, EnterWireJunction entrance) {
        Set<ExitWireJunction> results = new LinkedHashSet<>();
        String tileName = entrance.getTileName();

        Queue<NodeDepthPair> queue = new LinkedList<>();
        queue.add(new NodeDepthPair(entrance.getNodeName()));

        HashSet<String> footprint = new HashSet<>();

        while (!queue.isEmpty()) {
            NodeDepthPair trav = queue.remove();

            if (trav.getDepth() >= maxDepth)
                continue;

            for (PIP pip : getFwdPIPs(d, tileName, trav.getNodeName())) {
                String nextNodeName = RouteUtil.getPIPNodeName(tileName, pip.getEndWireName());

                WireDirection dir = RouteUtil.extractExitWireDirection(d, tileName, pip.getEndWireName());
                int wireLength = RouteUtil.extractExitWireLength(d, tileName, pip.getEndWireName());

                if (footprint.contains(nextNodeName) || RouteForge.isLocked(nextNodeName))
                    continue;

                if (dir != null && dir != WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(nextNodeName)) {
                    ExitWireJunction exit = new ExitWireJunction(d, tileName, pip.getEndWireName());
                    exit.setTilePathCost(trav.getDepth());
                    results.add(exit);
                }
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
        return findTilePaths(d, TILE_TRAVERSAL_MAX_DEPTH, entrance, exit);
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

                    if (RouteForge.isLocked(nextNodeName))
                        continue;

                    TilePath travCopy = new TilePath(trav);

                    // To prevent cycles in buffer traversal, don't queue previously traversed buffers
                    if (travCopy.addNode(nextNodeName))
                        queue.add(travCopy);
                }
            }
        }

        return results;
    }
}
