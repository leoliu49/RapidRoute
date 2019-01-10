package com.uwaterloo.watcag.router.elements;

import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.router.elements.CustomRoute;
import com.uwaterloo.watcag.router.RouteForge;
import com.uwaterloo.watcag.router.elements.TilePath;
import com.uwaterloo.watcag.util.RouterLog;
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

    public void removeRoute(CustomRoute route) {
        routeNetMap.remove(route);

        int routeBit = -1;
        for (int i : indexRouteMap.keySet()) {
            if (indexRouteMap.get(i) == route)
                routeBit = i;
        }
        indexRouteMap.remove(routeBit);
    }

    public void add(CustomRoute route, Net net) {
        routeNetMap.put(route, net);
        indexRouteMap.put(route.getRouteIndex(), route);
    }

    public void commit(Design d) {
        RouterLog.log("Committing routes for connection " + registerConnection.toString() + ".", RouterLog.Level.INFO);
        for (CustomRoute route : routeNetMap.keySet()) {
            Net net = routeNetMap.get(route);
            route.commitToNet(d, net);
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
