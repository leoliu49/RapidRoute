import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;

import java.util.*;

public class RoutingFootprint {

    private RegisterConnection registerConnection;

    private HashMap<CustomRoute, Net> routeNetMap;
    private HashMap<Integer, CustomRoute> indexRouteMap;

    public RoutingFootprint(RegisterConnection connection) {
        registerConnection = connection;

        routeNetMap = new HashMap<>();
        indexRouteMap = new HashMap<>();
    }

    public RegisterConnection getRegisterConnection() {
        return registerConnection;
    }

    public HashMap<CustomRoute, Net> getRouteMap() {
        return routeNetMap;
    }

    public Set<CustomRoute> getRoutes() {
        return routeNetMap.keySet();
    }

    public CustomRoute getRoute(int i) {
        return indexRouteMap.get(i);
    }

    public void add(CustomRoute route, Net net) {
        routeNetMap.put(route, net);
        indexRouteMap.put(route.getRouteIndex(), route);
    }

    public void commit(Design d) {
        RouterLog.log("Committing routes to design <" + d.getName() + ">:", RouterLog.Level.INFO);
        for (CustomRoute route : routeNetMap.keySet()) {
            Net net = routeNetMap.get(route);

            RouterLog.log("Committing PIPs to net <" + net.getName() + ">:", RouterLog.Level.INFO);
            RouterLog.indent();

            route.commitToNet(d, net);

            RouterLog.indent(-1);
        }
    }

    public void clear() {
        for (CustomRoute route : routeNetMap.keySet()) {
            for (TilePath path : route.getRoute()) {
                for (String nodeName : path.getNodePath()) {
                    RouteForge.unlock(nodeName);
                    RouteForge.unOccupy(nodeName);
                }
            }
        }
    }
}
