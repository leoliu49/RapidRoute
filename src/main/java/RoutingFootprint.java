import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;

import java.util.*;

public class RoutingFootprint {

    /*
     * Core class for tracking what's routed
     * Does not check against duplicated routes
     */

    private HashMap<CustomRoute, Net> routeNetMap;

    public RoutingFootprint() {
        routeNetMap  = new HashMap<CustomRoute, Net>();
    }

    public HashMap<CustomRoute, Net> getRouteMap() {
        return routeNetMap;
    }

    public Set<CustomRoute> getRoutes() {
        return routeNetMap.keySet();
    }

    public void add(CustomRoute route, Net net) {
        routeNetMap.put(route, net);
    }

    public void add(RoutingFootprint footprint) {
        routeNetMap.putAll(footprint.getRouteMap());
    }

    public boolean isConflictedWithFootprint(RoutingFootprint footprint) {
        Set<String> nodeUnion = new HashSet<String>();
        for (CustomRoute route : routeNetMap.keySet()) {
            for (TileIntPath path : route.getRoute()) {
                for (String nodeName : path.getNodePath())
                    nodeUnion.add(nodeName);
            }
        }
        for (CustomRoute route : footprint.getRoutes()) {
            for (TileIntPath path : route.getRoute()) {
                for (String nodeName : path.getNodePath()) {
                    if (nodeUnion.contains(nodeName))
                        return true;
                }
            }
        }
        return false;
    }

    public void addToNodeFootprint(Set<String> footprint) {
        for (CustomRoute route : routeNetMap.keySet()) {
            for (TileIntPath path : route.getRoute())
                footprint.addAll(path.getNodePath());
        }
    }

    public void commit(Design d) {

        RouterLog.log("Committing routes to design <" + d.getName() + ">:", RouterLog.Level.NORMAL);
        for (CustomRoute route : routeNetMap.keySet()) {
            Net net = routeNetMap.get(route);

            RouterLog.log("Committing PIPs to net <" + net.getName() + ">:", RouterLog.Level.NORMAL);
            RouterLog.indent();

            route.commitToNet(d, net);

            RouterLog.indent(-1);
        }
    }

}
