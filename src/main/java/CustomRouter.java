import java.util.*;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.PIP;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFTools;

public class CustomRouter {

    /*
     * Collection of static functions which can route registers
     */

    private static Set<String> nodeLock = new HashSet<>();

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

        /*
         * Step 2: Calculate which hops (WireJunctions) should be used to connect each source/sink
         *  If there are conflicts in hops, reroute
         */
        RouterLog.log("2: Calculating route templates", RouterLog.Level.INFO);
        RouterLog.indent();
        long tStep2Begin = System.currentTimeMillis();

        ArrayList<RouteTemplate> templates = new ArrayList<>();

        for (int i = 0; i < bitwidth; i++) {
            RouteTemplate template = CustomRoutingCalculator.createRouteTemplate(d, srcJunctions.get(i),
                    snkJunctions.get(i));
            if (template.isEmpty()) {
                RouterLog.indent(-1);
                RouterLog.log("Failed to find route template.", RouterLog.Level.ERROR);
                return null;
            }
            template.setBitIndex(i);
            templates.add(template);
        }

        int rerouteCount = 0;
        for (int i = 0; i < bitwidth; i++) {
            RouteTemplate template = templates.get(i);

            // If conflicts exist, reroute
            if (CustomRoutingCalculator.isRouteTemplateConflicted(template)) {
                RouterLog.log("Template conflict detected. Rerouting.", RouterLog.Level.INFO);

                RouteTemplate newTemplate = CustomRoutingCalculator.createRouteTemplate(d, template.getSrc(),
                        template.getSnk());
                if (newTemplate.isEmpty()) {
                    RouterLog.indent(-1);
                    RouterLog.log("Failed to find route template.", RouterLog.Level.ERROR);
                    return null;
                }
                newTemplate.setBitIndex(template.getBitIndex());
                templates.set(i, newTemplate);
                rerouteCount += 1;
            }
            CustomRoutingCalculator.lockRouteTemplate(template);
        }
        RouterLog.indent(-1);
        RouterLog.log("Templates found in " + (System.currentTimeMillis() - tStep2Begin) + " ms with " + rerouteCount
                + " reroutes.", RouterLog.Level.NORMAL);

        /*
         * Step 3: Since tile paths at the source/sink INT tiles have the most traffic, verify that these paths are
         *   possible
         */
        RouterLog.log("3: Finding tile paths at for source/sink junctions.", RouterLog.Level.INFO);
        RouterLog.indent();

        ArrayList<CustomRoute> routes = new ArrayList<>();
        // Fully populated bitwise in step 4
        for (int i = 0; i < templates.size(); i++)
            routes.add(null);

        boolean hasFailed = false;
        Set<String> carelesslyLockedNodes = new HashSet<>();

        for (int i = 0; i < templates.size(); i++) {
            RouteTemplate template = templates.get(i);

            // Release locked nodes after success
            if (!hasFailed) {
                for (String nodeName : carelesslyLockedNodes)
                    unlock(nodeName);
            }

            // Try source
            ArrayList<TilePath> srcPathChoices = FabricBrowser.findTilePaths(d, template.getSrc(),
                    (ExitWireJunction) template.getTemplate(1));
            if (srcPathChoices.isEmpty()) {
                // There is no way to route source outwards: recreate templates and try again
                RouterLog.log("Conflict at source tile detected. Rerouting.", RouterLog.Level.INFO);

                // Lock this node because it is no long accessible from the source
                lock(template.getTemplate(1).getNodeName());

                // While these are locked for now, they're not actually getting used up, so we should unlock them later
                carelesslyLockedNodes.add(template.getTemplate(1).getNodeName());

                RouteTemplate newTemplate = CustomRoutingCalculator.createRouteTemplate(d, template.getSrc(),
                        template.getSnk());
                if (newTemplate.isEmpty()) {
                    RouterLog.indent(-1);
                    RouterLog.log("Failed to find route template.", RouterLog.Level.ERROR);
                    return null;
                }
                newTemplate.setBitIndex(template.getBitIndex());
                templates.set(i, newTemplate);
                rerouteCount += 1;
                i -= 1;

                hasFailed = true;

                continue;
            }

            // Try sink
            ArrayList<TilePath> snkPathChoices = FabricBrowser.findTilePaths(d,
                    (EnterWireJunction) template.getTemplate(-2), template.getSnk());
            if (snkPathChoices.isEmpty()) {
                // There is no way to route source outwards: recreate templates and try again
                RouterLog.log("Conflict at sink tile detected. Rerouting.", RouterLog.Level.INFO);

                // Lock this node because it can no longer access the sink
                lock(template.getTemplate(-2).getNodeName());
                carelesslyLockedNodes.add(template.getTemplate(-2).getNodeName());

                RouteTemplate newTemplate = CustomRoutingCalculator.createRouteTemplate(d, template.getSrc(),
                        template.getSnk());
                if (newTemplate.isEmpty()) {
                    RouterLog.indent(-1);
                    RouterLog.log("Failed to find route template.", RouterLog.Level.ERROR);
                    return null;
                }
                newTemplate.setBitIndex(template.getBitIndex());
                templates.set(i, newTemplate);
                rerouteCount += 1;
                i -= 1;

                hasFailed = true;

                continue;
            }

            template = templates.get(i);

            // Lock exclusive nodes
            for (String nodeName : CustomRoutingCalculator.deriveExclusiveNodes(srcPathChoices))
                lock(nodeName);
            for (String nodeName : CustomRoutingCalculator.deriveExclusiveNodes(snkPathChoices))
                lock(nodeName);

            hasFailed = false;

            CustomRoute route = new CustomRoute(template);
            route.setPathSub(0, srcPathChoices);
            route.setPathSub(-1, snkPathChoices);

            routes.set(template.getBitIndex(), route);

        }
        RouterLog.indent(-1);

        /*
         * Step 4: Iterate through all templates, construct CustomRoute's based on templates, then populate with path
         *   subs
         */
        RouterLog.log("4: Calculating tile paths for templates.", RouterLog.Level.INFO);
        RouterLog.indent();
        long tStep4Begin = System.currentTimeMillis();

        for (CustomRoute route : routes) {
            RouteTemplate template = route.getTemplate();
            for (int i = 1; i < (template.getTemplate().size() / 2) - 1; i ++) {
                route.setPathSub(i, FabricBrowser.findTilePaths(d, (EnterWireJunction) template.getTemplate(i * 2),
                        (ExitWireJunction) template.getTemplate(i * 2 + 1)));
            }
        }
        RouterLog.indent(-1);
        RouterLog.log("All tile paths found in " + (System.currentTimeMillis() - tStep4Begin) + " ms.",
                RouterLog.Level.NORMAL);

        /*
         * Step 5: Programmatically determine which INT tile paths to take
         *   TODO: Use easing technique for sink (or maybe source) tile paths
         */
        RouterLog.log("5: Performing route contention", RouterLog.Level.INFO);
        RouterLog.indent();

        if (!CustomRoutingCalculator.routeContention(d, routes)) {
            RouterLog.indent(-1);
            RouterLog.log("Failed to complete route contention.", RouterLog.Level.ERROR);
            return null;
        }
        RouterLog.indent(-1);


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
