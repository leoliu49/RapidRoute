package com.uwaterloo.watcag.router.elements;

import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class RouteFootprint {

    private HashMap<CustomRoute, Net> routeNetMap;
    private HashMap<Integer, CustomRoute> routeIndexMap;

    public RouteFootprint() {
        routeNetMap = new HashMap<>();
        routeIndexMap = new HashMap<>();
    }

    public Set<CustomRoute> getRoutes() {
        return routeNetMap.keySet();
    }

    public Net getCorrespondingNet(CustomRoute route) {
        return routeNetMap.get(route);
    }

    public CustomRoute getRouteByIndex(int index) {
        return routeIndexMap.get(index);
    }

    public void addRoute(CustomRoute route, Net net) {
        routeNetMap.put(route, net);
        routeIndexMap.put(route.getRouteIndex(), route);
    }

    public void removeRoute(CustomRoute route) {
        routeNetMap.remove(route);

        int routeIndex = -1;
        for (int i : routeIndexMap.keySet()) {
            if (routeIndexMap.get(i) == route)
                routeIndex = i;
        }
        routeIndexMap.remove(routeIndex);
    }

    public void commit(Design d) {
        for (CustomRoute route : routeNetMap.keySet()) {
            Net net = routeNetMap.get(route);
            route.commitToNet(d, net);
        }
    }
}
