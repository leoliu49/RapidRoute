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

    /*
     * ArrayList of all nodes routed
     */
    public static Set<String> globalNodeFootprint = new HashSet<>();

    static {
        TileBrowser.setGlobalNodeFootprint(globalNodeFootprint);
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

    /*
     * Low-level function which marks 2 PIP junctions to be connected to
     * the physical net
     */
    public static void findAndRoute(Design d, Net n, String tileName, String startNodeName, String endNodeName) {
        for (PIP pip : d.getDevice().getTile(tileName).getPIPs()) {
            if (pip.getStartNode().getName().equals(startNodeName) && pip.getEndNode().getName().equals(endNodeName)) {
                RouterLog.log("Junction <" + startNodeName + "> ---> <" + endNodeName + ">", RouterLog.Level.NORMAL);
                String startWire = Integer
                        .toString(d.getDevice().getTile(tileName).getGlobalWireID(pip.getStartWire()));
                String endWire = Integer.toString(d.getDevice().getTile(tileName).getGlobalWireID(pip.getEndWire()));

                n.addPIP(pip);
                return;
            }
        }
        RouterLog.log("Junction <" + startNodeName + "> ---> <" + endNodeName + "> failed.", RouterLog.Level.ERROR);
    }

    /*
     * Non-bussed routes only
     */
    /*
    public static void routeSimpleOneDimRegister(Design d, Net physNet, SimpleOneDimRegister startReg, SimpleOneDimRegister endReg, int hopLimit, WireDirection dir) {

        sanitizeNets(d);

        Tile startIntTile = startReg.getSite().getIntTile();
        Tile endIntTile = endReg.getSite().getIntTile();

        createRouteTemplates(d, physNet, startIntTile.getName(),
                startIntTile.getName() + "/" + SimpleOneDimRegister.outPIPName,
                endIntTile.getName(),
                endIntTile.getName() + "/" + SimpleOneDimRegister.inPIPName, dir);

    }
    */

    public static RoutingFootprint routeComplexRegisters(Design d, ComplexRegister startReg, ComplexRegister endReg) {

        sanitizeNets(d);

        RouterLog.log("Routing <" + startReg.getName() + "> --> <" + endReg.getName() + ">", RouterLog.Level.NORMAL);

        RoutingFootprint footprint = new RoutingFootprint();

        int bitWidth = startReg.getBitWidth();

        // Stop router from using register input/output PIPs as buffers
        for (RegisterComponent component : startReg.getComponents()) {
            String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
            for (int i = 0; i < component.getBitWidth(); i++) {
                globalNodeFootprint.add(intTileName + "/" + component.getInPIPName(i));
                globalNodeFootprint.add(intTileName + "/" + component.getOutPIPName(i));
            }
        }

        for (RegisterComponent component : endReg.getComponents()) {
            String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
            for (int i = 0; i < component.getBitWidth(); i++) {
                globalNodeFootprint.add(intTileName + "/" + component.getInPIPName(i));
                globalNodeFootprint.add(intTileName + "/" + component.getOutPIPName(i));
            }
        }

        ArrayList<EnteringTileJunction> srcJunctions = new ArrayList<EnteringTileJunction>();
        ArrayList<ExitingTileJunction> snkJunctions = new ArrayList<ExitingTileJunction>();

        for (RegisterComponent component : startReg.getComponents()) {
            String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
            for (int i = 0; i < component.getBitWidth(); i++) {
                srcJunctions.add(new EnteringTileJunction(intTileName, intTileName + "/" + component.getOutPIPName(i),
                        component.getOutPIPName(i), 0, true, null));
            }
        }

        for (RegisterComponent component : endReg.getComponents()) {
            String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
            for (int i = 0; i < component.getBitWidth(); i++) {
                snkJunctions.add(new ExitingTileJunction(intTileName, intTileName + "/" + component.getInPIPName(i),
                        component.getInPIPName(i), 0, true, null));
            }
        }


        ArrayList<ArrayList<CustomRoute>> allRoutes = new ArrayList<ArrayList<CustomRoute>>();
        for (int i = 0; i < bitWidth; i++) {
            allRoutes.add(CustomRoutingCalculator.createRouteTemplates(d, srcJunctions.get(i),
                    snkJunctions.get(i)));
        }


        ArrayList<CustomRoute> routes = CustomRoutingCalculator.findBestRouteTemplates(d, allRoutes);

        float templateSize = 0;
        RouterLog.log("Following routing templates will be routed:", RouterLog.Level.VERBOSE);
        RouterLog.indent();
        for (CustomRoute route : routes) {
            RouterLog.log(route.getRouteTemplate().toString(), RouterLog.Level.VERBOSE);
            templateSize += (float) route.getRouteTemplateSize();
        }
        RouterLog.indent(-1);
        RouterLog.log("Average routing template size is " + templateSize / (float) routes.size(),
                RouterLog.Level.NORMAL);

        CustomRoutingCalculator.completeRouting(d, routes);

        RouterLog.log("Routing complete. Stats on routing result:", RouterLog.Level.NORMAL);
        RouterLog.indent();

        ArrayList<Integer> costs = new ArrayList<Integer>();
        for (CustomRoute route : routes)
            costs.add(route.getCost());
        RouterLog.log("Cost breakdown: " + costs, RouterLog.Level.NORMAL);

        RouterLog.indent(-1);

        // Bit index grows, but the components themselves have their own internal bit counts
        int bitIndex = 0;
        for (RegisterComponent component : startReg.getComponents()) {
            for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                Net net = d.getNet(startReg.getName() + "_" + component.getName() + "/"
                        + ComplexRegister.OUTPUT_NAME + "[" + i + "]");

                // TODO: this probably needs to be earlier
                routes.get(bitIndex).setBitIndex(bitIndex);
                footprint.add(routes.get(bitIndex), net);
            }
        }

        // Remove in/out PIPs from globalNodeFootprint, since we'll be committing them shortly
        for (RegisterComponent component : startReg.getComponents()) {
            String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
            for (int i = 0; i < component.getBitWidth(); i++) {
                globalNodeFootprint.remove(intTileName + "/" + component.getInPIPName(i));
                globalNodeFootprint.remove(intTileName + "/" + component.getOutPIPName(i));
            }
        }

        for (RegisterComponent component : endReg.getComponents()) {
            String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
            for (int i = 0; i < component.getBitWidth(); i++) {
                globalNodeFootprint.remove(intTileName + "/" + component.getInPIPName(i));
                globalNodeFootprint.remove(intTileName + "/" + component.getOutPIPName(i));
            }
        }

        footprint.addToNodeFootprint(CustomRouter.globalNodeFootprint);

        return footprint;
    }

    /*
     * Bussed routes
     */
    /*
    public static RoutingFootprint routeSimpleOneDimRegister(Design d, SimpleOneDimRegister startReg,
                                                             SimpleOneDimRegister endReg, WireDirection dir) {
        sanitizeNets(d);

        RouterLog.log("Routing <" + startReg.getName() + "> --> <" + endReg.getName() + ">", RouterLog.Level.NORMAL);

        RoutingFootprint footprint = new RoutingFootprint();

        Tile startIntTile = startReg.getSite().getIntTile();
        Tile endIntTile = endReg.getSite().getIntTile();

        // Stop router from using register input/output PIPs as buffers
        for (int i = 0; i < SimpleOneDimRegister.bitWidth; i++) {
            globalNodeFootprint.add(endReg.getSite().getIntTile().getName() + "/" + SimpleOneDimRegister.inPIPNames.get(i));
            globalNodeFootprint.add(endReg.getSite().getIntTile().getName() + "/" + SimpleOneDimRegister.outPIPNames.get(i));
            globalNodeFootprint.add(startReg.getSite().getIntTile().getName() + "/" + SimpleOneDimRegister.inPIPNames.get(i));
            globalNodeFootprint.add(startReg.getSite().getIntTile().getName() + "/" + SimpleOneDimRegister.outPIPNames.get(i));
        }

        ArrayList<EnteringTileJunction> srcJunctions = new ArrayList<EnteringTileJunction>();
        ArrayList<ExitingTileJunction> snkJunctions = new ArrayList<ExitingTileJunction>();
        for (int i = 0; i < SimpleOneDimRegister.bitWidth; i++) {
            srcJunctions.add(new EnteringTileJunction(startIntTile.getName(), startIntTile.getName() + "/"
                    + SimpleOneDimRegister.outPIPNames.get(i), SimpleOneDimRegister.outPIPNames.get(i), 0, true, dir));
            snkJunctions.add(new ExitingTileJunction(endIntTile.getName(), endIntTile.getName() + "/"
                    + SimpleOneDimRegister.inPIPNames.get(i), SimpleOneDimRegister.inPIPNames.get(i), 0, true, dir));
        }

        // For some odd reason, bit0 (LOGIC_OUTS_W30) on SOUTH can't do SS12's unless routed first N or E
        if (dir.equals(WireDirection.SOUTH)
                && startIntTile.getTileYCoordinate() - endIntTile.getTileYCoordinate() >= 16) {
            //RegisterExceptions.fixSouthUnroutableLongLines(d, srcJunctions);
        }

        ArrayList<ArrayList<CustomRoute>> allRoutes = new ArrayList<ArrayList<CustomRoute>>();
        for (int i = 0; i < SimpleOneDimRegister.bitWidth; i++) {
            allRoutes.add(CustomRoutingCalculator.createRouteTemplates(d, srcJunctions.get(i),
                    snkJunctions.get(i), dir));
        }

        if (dir.equals(WireDirection.SOUTH)
                && startIntTile.getTileYCoordinate() - endIntTile.getTileYCoordinate() >= 16) {
            //RegisterExceptions.fixSouthUnroutableLongLines(d, startIntTile.getName(), allRoutes.get(0));
        }

        //ArrayList<CustomRoute> routes = CustomRoutingCalculator.findValidRouteTemplates(allRoutes);
        ArrayList<CustomRoute> routes = CustomRoutingCalculator.findBestRouteTemplates(d, allRoutes);

        float templateSize = 0;
        RouterLog.log("Following routing templates will be routed:", RouterLog.Level.VERBOSE);
        RouterLog.indent();
        for (CustomRoute route : routes) {
            RouterLog.log(route.getRouteTemplate().toString(), RouterLog.Level.VERBOSE);
            templateSize += (float) route.getRouteTemplateSize();
        }
        RouterLog.indent(-1);
        RouterLog.log("Average routing template size is " + templateSize / (float) routes.size(),
                RouterLog.Level.NORMAL);

        CustomRoutingCalculator.completeRouting(d, routes);

        RouterLog.log("Routing complete. Stats on routing result:", RouterLog.Level.NORMAL);
        RouterLog.indent();

        ArrayList<Integer> costs = new ArrayList<Integer>();
        for (CustomRoute route : routes)
            costs.add(route.getCost());
        RouterLog.log("Cost breakdown: " + costs, RouterLog.Level.NORMAL);

        RouterLog.indent(-1);

        for (int i = 0; i < SimpleOneDimRegister.bitWidth; i++) {
            Net net = d.getNet(startReg.getName() + "/" + SimpleOneDimRegister.OUTPUT_NAME + "[" + i + "]");

            // TODO: this probably needs to be earlier
            routes.get(i).setBitIndex(i);
            footprint.add(routes.get(i), net);
            //routes.get(i).commitToNet(d, net);
        }

        for (int i = 0; i < SimpleOneDimRegister.bitWidth; i++) {
            globalNodeFootprint.remove(endReg.getSite().getIntTile().getName() + "/"
                    + SimpleOneDimRegister.inPIPNames.get(i));
            globalNodeFootprint.remove(endReg.getSite().getIntTile().getName() + "/"
                    + SimpleOneDimRegister.outPIPNames.get(i));
            globalNodeFootprint.remove(startReg.getSite().getIntTile().getName() + "/"
                    + SimpleOneDimRegister.inPIPNames.get(i));
            globalNodeFootprint.remove(startReg.getSite().getIntTile().getName() + "/"
                    + SimpleOneDimRegister.outPIPNames.get(i));
        }

        footprint.addToNodeFootprint(CustomRouter.globalNodeFootprint);

        return footprint;

    }
    */

    /*
    public static RoutingFootprint routeOneDimRing(Design d, Ring ring) {
        RoutingFootprint footprint = new RoutingFootprint();

        HashMap<Integer, ArrayList<SimpleOneDimRegister>> sepToRegistersMap
                = new HashMap<Integer, ArrayList<SimpleOneDimRegister>>();
        HashMap<Integer, ArrayList<RoutingFootprint>> sepToFootprintsMap
                = new HashMap<Integer, ArrayList<RoutingFootprint>>();

        {
            // Route loopback first, since it's likely the worst timing path
            RoutingFootprint fp = routeSimpleOneDimRegister(d, (SimpleOneDimRegister) ring.get(-1), (SimpleOneDimRegister) ring.get(0),
                    ring.getLoopbackDir());

            footprint.add(fp);
        }

        for (int i = 0; i < ring.size - 1; i++) {

            RoutingFootprint fp = null;

            int sep = ring.getRegisterSeparations().get(i);
            SimpleOneDimRegister reg = (SimpleOneDimRegister) ring.get(i);

            if (sepToRegistersMap.containsKey(sep)) {
                // Route of this kind previously defined - try to duplicate routes with shift
                RouterLog.log("Potential duplicable route found for <" + ring.get(i).getName() + "> --> <"
                        + ring.get(i + 1).getName() + ">", RouterLog.Level.NORMAL);

                boolean isConflicted = true;
                for (int j = 0; j < sepToRegistersMap.get(sep).size(); j++) {
                    SimpleOneDimRegister refReg = sepToRegistersMap.get(sep).get(j);
                    RoutingFootprint refFp = sepToFootprintsMap.get(sep).get(j);

                    fp = new RoutingFootprint();

                    Tile tile = reg.getSite().getTile();
                    Tile refTile = refReg.getSite().getTile();

                    int dx = tile.getTileXCoordinate() - refTile.getTileXCoordinate();
                    int dy = tile.getTileYCoordinate() - refTile.getTileYCoordinate();

                    for (CustomRoute refRoute : refFp.getRoutes()) {
                        Net net = reg.getOutnet(d, refRoute.getBitIndex());
                        fp.add(CustomRoute.duplWithShift(d, refRoute, dx, dy), net);
                    }

                    if (!fp.isConflictedWithFootprint(footprint)) {
                        // Found a previously-defined routes which has no conflicts
                        RouterLog.log("Duplicating route based on <" + refReg.getName() + ">", RouterLog.Level.NORMAL);

                        isConflicted = false;
                        sepToRegistersMap.get(sep).add(reg);
                        sepToFootprintsMap.get(sep).add(fp);

                        fp.addToNodeFootprint(CustomRouter.globalNodeFootprint);

                        break;
                    }
                }

                // No previously-defined routes could be used - must calculate route from scratch
                if (isConflicted) {
                    RouterLog.log("Reference routes were conflicted and could not be duplicated. Routing from scratch.",
                            RouterLog.Level.NORMAL);

                    fp = routeSimpleOneDimRegister(d, (SimpleOneDimRegister) ring.get(i),
                            (SimpleOneDimRegister) ring.get(i + 1), ring.getPrimaryDir());

                    sepToRegistersMap.get(sep).add((SimpleOneDimRegister) ring.get(i));
                    sepToFootprintsMap.get(sep).add(fp);
                }
            }
            else {
                fp = routeSimpleOneDimRegister(d, (SimpleOneDimRegister) ring.get(i),
                        (SimpleOneDimRegister) ring.get(i + 1), ring.getPrimaryDir());

                ArrayList<SimpleOneDimRegister> newRegisterEntry = new ArrayList<SimpleOneDimRegister>();
                newRegisterEntry.add(reg);
                sepToRegistersMap.put(sep, newRegisterEntry);

                ArrayList<RoutingFootprint> newFootprintEntry = new ArrayList<RoutingFootprint>();
                newFootprintEntry.add(fp);
                sepToFootprintsMap.put(sep, newFootprintEntry);
            }

            footprint.add(fp);

        }

        return footprint;
    }
    */

}
