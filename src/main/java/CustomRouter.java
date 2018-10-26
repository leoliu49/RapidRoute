import java.util.*;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.PIP;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFTools;

public class CustomRouter {

    /*
     * Collection of static functions which can route registers
     */

    private static Set<String> nodeLock = new HashSet<>();

    private static final int SUGGESTED_STANDARD_TILE_TRAVERSAL_MAX_DEPTH = 8;
    private static final int SUGGESTED_SINK_TILE_TRAVERSAL_MAX_DEPTH = 10;

    public static void flushNodeLock() {
        nodeLock.clear();
    }

    public static boolean lock(String nodeName) {
        if (nodeLock.contains(nodeName))
            return false;
        nodeLock.add(nodeName);
        return true;
    }

    public static boolean isLocked(String nodeName) {
        return nodeLock.contains(nodeName);
    }

    public static void unlock(String nodeName) {
        nodeLock.remove(nodeName);
    }

    // ArrayList of all used nodes
    public static Set<String> globalNodeFootprint = new HashSet<>();

    static {
        FabricBrowser.setGlobalNodeFootprint(globalNodeFootprint);
    }

    public static void sanitizeNets(Design d) {
        EDIFNetlist n = d.getNetlist();
        Map<String, String> parentNetMap = n.getParentNetMap();
        for (Net net : new ArrayList<>(d.getNets())) {
            if (net.getPins().size() > 0 && net.getSource() == null) {
                if (net.isStaticNet())
                    continue;
                String parentNet = parentNetMap.get(net.getName());
                if (parentNet.equals(EDIFTools.LOGICAL_VCC_NET_NAME)) {
                    d.movePinsToNewNetDeleteOldNet(net, d.getVccNet(), true);
                    continue;
                }
                else if (parentNet.equals(EDIFTools.LOGICAL_GND_NET_NAME)) {
                    d.movePinsToNewNetDeleteOldNet(net, d.getGndNet(), true);
                    continue;
                }
                Net parent = d.getNet(parentNet);
                if (parent == null) {
                    continue;
                }
                d.movePinsToNewNetDeleteOldNet(net, parent, true);

            }
        }
    }


    public static void findAndRoute(Design d, Net n, String tileName, String startNodeName, String endNodeName) {
        for (PIP pip : d.getDevice().getTile(tileName).getPIPs()) {
            if (pip.getStartNode().getName().equals(startNodeName) && pip.getEndNode().getName().equals(endNodeName)) {
                RouterLog.log("Junction <" + startNodeName + "> ---> <" + endNodeName + ">", RouterLog.Level.INFO);
                String startWire = Integer
                        .toString(d.getDevice().getTile(tileName).getGlobalWireID(pip.getStartWire()));
                String endWire = Integer.toString(d.getDevice().getTile(tileName).getGlobalWireID(pip.getEndWire()));

                n.addPIP(pip);
                return;
            }
        }
        RouterLog.log("Junction <" + startNodeName + "> ---> <" + endNodeName + "> failed.", RouterLog.Level.ERROR);
    }

    public static RoutingFootprint routeConnection(Design d, RegisterConnection connection) {
        sanitizeNets(d);

        RouterLog.log("Routing " + connection.toString() + ".", RouterLog.Level.NORMAL);
        ComplexRegister srcReg = connection.getSrcReg();
        ComplexRegister snkReg = connection.getSnkReg();

        RoutingFootprint footprint = new RoutingFootprint();

        int bitwidth = connection.getBitWidth();

        long tBegin = System.currentTimeMillis();

        /*
         * Step 0: Stop router from using register input/output PIPs as buffers
         */
        RouterLog.log("0: Locking in/out PIPs of registers.", RouterLog.Level.INFO);
        for (RegisterComponent component : srcReg.getComponents()) {
            String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
            for (int i = 0; i < component.getBitWidth(); i++) {
                lock(intTileName + "/" + component.getInPIPName(i));
                lock(intTileName + "/" + component.getOutPIPName(i));
            }
        }

        for (RegisterComponent component : snkReg.getComponents()) {
            String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
            for (int i = 0; i < component.getBitWidth(); i++) {
                lock(intTileName + "/" + component.getInPIPName(i));
                lock(intTileName + "/" + component.getOutPIPName(i));
            }
        }

        /*
         * Step 1: Determine which source and sink junctions to route together
         */
        RouterLog.log("1: Finding corresponding src/snk junctions.", RouterLog.Level.INFO);
        ArrayList<EnterWireJunction> srcJunctions = new ArrayList<>();
        ArrayList<ExitWireJunction> snkJunctions = new ArrayList<>();

        {
            int bitIndex = 0;
            for (RegisterComponent component : srcReg.getComponents()) {
                String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
                for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                    if (bitIndex >= connection.getSrcRegLowestBit() && bitIndex <= connection.getSrcRegHighestBit()) {
                        srcJunctions.add(EnterWireJunction.newSrcJunction(intTileName, component.getOutPIPName(i)));
                    }
                }
            }
        }
        {
            int bitIndex = 0;
            for (RegisterComponent component : snkReg.getComponents()) {
                String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
                for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                    if (bitIndex >= connection.getSnkRegLowestBit() && bitIndex <= connection.getSnkRegHighestBit()) {
                        snkJunctions.add(ExitWireJunction.newSnkJunction(intTileName, component.getInPIPName(i)));
                    }
                }
            }
        }
        RouterLog.indent();
        for (int i = 0; i < bitwidth; i++) {
            RouterLog.log(srcJunctions.get(i) + " --> " + snkJunctions.get(i), RouterLog.Level.VERBOSE);
        }
        RouterLog.indent(-1);


        boolean success = false;
        ArrayList<RouteTemplate> templates;
        ArrayList<CustomRoute> routes;
        do {
            /*
             * Step 2: Calculate which hops (WireJunctions) should be used to connect each source/sink
             *  If there are conflicts in hops, reroute
             */
            RouterLog.log("2: Calculating route templates", RouterLog.Level.INFO);
            RouterLog.indent();
            long tStep2Begin = System.currentTimeMillis();
            templates = CustomRoutingCalculator.createBussedRouteTemplates(d, srcJunctions, snkJunctions);
            RouterLog.indent(-1);
            RouterLog.log("All templates found in " + (System.currentTimeMillis() - tStep2Begin) + " ms.",
                    RouterLog.Level.NORMAL);

            /*
             * Step 3: Iterate through all templates, construct CustomRoute's based on templates, then populate with path
             *   subs
             */
            RouterLog.log("3: Calculating tile paths for templates.", RouterLog.Level.INFO);
            RouterLog.indent();
            long tStep3Begin = System.currentTimeMillis();

            routes = new ArrayList<>();
            for (RouteTemplate template : templates)
                routes.add(new CustomRoute(template));

            for (CustomRoute route : routes) {
                RouteTemplate template = route.getTemplate();

                for (int i = 0; i < template.getTemplate().size() / 2 - 1; i++) {
                    route.setPathSub(i, FabricBrowser.findTilePaths(d, SUGGESTED_STANDARD_TILE_TRAVERSAL_MAX_DEPTH,
                            (EnterWireJunction) template.getTemplate(i * 2),
                            (ExitWireJunction) template.getTemplate(i * 2 + 1)));
                }
                // Leave more slack for sink tile path
                route.setPathSub(-1, FabricBrowser.findTilePaths(d, SUGGESTED_SINK_TILE_TRAVERSAL_MAX_DEPTH,
                        (EnterWireJunction) template.getTemplate(-2),
                        (ExitWireJunction) template.getTemplate(-1)));

            }
            RouterLog.indent(-1);
            RouterLog.log("All tile paths found in " + (System.currentTimeMillis() - tStep3Begin) + " ms.",
                    RouterLog.Level.NORMAL);

            /*
             * Step 4: Since sink tile paths suffer heavy congestion, use "easing" method to determine best-case sink paths
             *  TODO: Is this necessary?
             */
            RouterLog.log("4: Using \"easing\" method to find best sink tile paths.", RouterLog.Level.INFO);
            RouterLog.indent();
            long tStep4Begin = System.currentTimeMillis();
            if (!CustomRoutingCalculator.deriveBestSinkPaths(d, routes)) {
                RouterLog.indent(-1);
                RouterLog.log("Failed to find sink tile paths. The connection will be rerouted completely.",
                        RouterLog.Level.ERROR);
                RoutingErrorSalvage.deriveBestSinkPathsDeadlockReport.actOnReport();
                continue;
            }
            RouterLog.indent(-1);
            RouterLog.log("Sink paths found in " + (System.currentTimeMillis() - tStep4Begin) + " ms.",
                    RouterLog.Level.NORMAL);

            /*
             * Step 5: Programmatically determine which INT tile paths to take
             */
            RouterLog.log("5: Performing route contention", RouterLog.Level.INFO);
            RouterLog.indent();
            long tStep5Begin = System.currentTimeMillis();

            if (!CustomRoutingCalculator.routeContention(d, routes)) {
                RouterLog.indent(-1);
                RouterLog.log("Failed to complete route contention. The connection will be rerouted completely.",
                        RouterLog.Level.ERROR);
                RoutingErrorSalvage.routeContentionLiveLockReport.actOnReport();
                continue;
            }
            RouterLog.indent(-1);
            RouterLog.log("Route contention completed in " + (System.currentTimeMillis() - tStep5Begin) + " ms.",
                    RouterLog.Level.NORMAL);

            success = true;

        } while (!success);

        /*
         * Step 6: Associate each CustomRoute with its corresponding net
         */
        {
            int bitIndex = 0;
            int routeIndex = 0;
            for (RegisterComponent component : srcReg.getComponents()) {
                for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                    if (bitIndex >= connection.getSrcRegLowestBit() && bitIndex <= connection.getSrcRegHighestBit()) {
                        Net net = d.getNet(srcReg.getName() + "_" + component.getName() + "/"
                                + ComplexRegister.OUTPUT_NAME + "[" + i + "]");

                        // This is the route's true bit index
                        routes.get(routeIndex).setBitIndex(bitIndex);
                        footprint.add(routes.get(routeIndex), net);
                        routeIndex += 1;
                    }
                }
            }
        }



        RouterLog.log("Connection routed in " + (System.currentTimeMillis() - tBegin) + " ms.",
                RouterLog.Level.NORMAL);
        flushNodeLock();

        return footprint;
    }


}
