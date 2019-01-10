package com.uwaterloo.watcag.router.elements;

import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;

import java.util.ArrayList;

public class CustomRoute {

    private int cost;

    private int bitIndex;
    private int routeIndex;

    private EnterWireJunction src;
    private ExitWireJunction snk;

    private int distanceX;
    private int distanceY;

    private RouteTemplate template;
    private ArrayList<TilePath> route;

    public CustomRoute(RouteTemplate template) {
        cost = 0;

        src = template.getSrc();
        snk = template.getSnk();

        distanceX = template.getDistanceX();
        distanceY = template.getDistanceY();

        this.template = template;

        route = new ArrayList<>();
        for (int i = 0; i < template.getTemplate().size(); i += 2) {
            route.add(null);
        }
    }

    public CustomRoute copyWithOffset(Design d, int dx, int dy) {
        CustomRoute copy = new CustomRoute(template.copyWithOffset(d, dx, dy));

        copy.setBitIndex(bitIndex);
        copy.setRouteIndex(routeIndex);

        ArrayList<TilePath> copyRoute = new ArrayList<>();
        for (int i = 0; i < route.size(); i++)
            copyRoute.add(route.get(i).copyWithOffset(d, dx, dy));
        copy.setRoute(copyRoute);

        return copy;
    }

    public int getCost() {
        return cost;
    }

    public int getBitIndex() {
        return bitIndex;
    }

    public void setBitIndex(int bitIndex) {
        this.bitIndex = bitIndex;
    }

    public int getRouteIndex() {
        return routeIndex;
    }

    public void setRouteIndex(int routeIndex) {
        this.routeIndex = routeIndex;
    }

    public EnterWireJunction getSrc() {
        return src;
    }

    public ExitWireJunction getSnk() {
        return snk;
    }

    public int getDistanceX() {
        return distanceX;
    }

    public int getDistanceY() {
        return distanceY;
    }

    public RouteTemplate getTemplate() {
        return template;
    }

    public ArrayList<TilePath> getRoute() {
        return route;
    }

    public void setPath(int i, TilePath path) {
        if (i < 0)
            i += route.size();
        route.set(i, path);
    }

    public void setPath(TilePath newPath) {
        for (int i = 0; i < route.size(); i++) {
            TilePath path = route.get(i);
            if (path.getEnterJunction().equals(newPath.getEnterJunction())
                    && path.getExitJunction().equals(newPath.getExitJunction())) {
                route.set(i, path);
                return;
            }
        }
    }

    public void setRoute(ArrayList<TilePath> route) {
        this.route = route;
    }

    public void commitToNet(Design d, Net net) {
        RouterLog.log("Committing PIPs to net <" + net.getName() + ">:", RouterLog.Level.INFO);
        RouterLog.indent();
        for (TilePath path : route)
            path.commitPIPsToNet(d, net);
        RouterLog.indent(-1);
    }

    public String shortHand() {
        return src.toString() + " --> " + snk.toString();
    }
}
