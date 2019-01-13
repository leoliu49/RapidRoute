package com.uwaterloo.watcag.router.elements;

import com.uwaterloo.watcag.router.RouteForge;
import com.uwaterloo.watcag.router.browser.TilePathTracer;
import com.uwaterloo.watcag.util.RouteUtil;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.device.Tile;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TilePath {

    private int cost;

    private EnterWireJunction enterJunction;
    private ExitWireJunction exitJunction;

    private String tileName;

    // Inclusive of entering/exiting nodes
    private ArrayList<String> nodePath;

    private TilePath(EnterWireJunction enterJunction, ExitWireJunction exitJunction, List<String> nodePath) {
        cost = nodePath.size() - 1;

        this.enterJunction = enterJunction;
        this.exitJunction = exitJunction;

        tileName = enterJunction.getTileName();

        this.nodePath = new ArrayList<>(nodePath);
    }

    public TilePath(TilePathTracer tracer) {
        cost = tracer.getLength();

        enterJunction = tracer.getEntrance();
        exitJunction = tracer.getExit();

        tileName = tracer.getTileName();

        nodePath = new ArrayList<>(tracer.getNodePath());
    }

    public TilePath copyWithOffset(Design d, int dx, int dy) {
        Tile offsetTile = d.getDevice().getTile(tileName).getTileXYNeighbor(dx, dy);

        ArrayList<String> copyNodePath = new ArrayList<>();
        for (String nodeName : nodePath)
            copyNodePath.add(offsetTile.getName() + "/" + RouteUtil.extractNodeWireName(nodeName));

        return new TilePath(enterJunction.copyWithOffset(d, dx, dy), exitJunction.copyWithOffset(d, dx, dy),
                copyNodePath);
    }

    public int getCost() {
        return cost;
    }

    public EnterWireJunction getEnterJunction() {
        return enterJunction;
    }

    public WireDirection getEnterDirection() {
        return enterJunction.getDirection();
    }

    public int getEnterWireLength() {
        return enterJunction.getWireLength();
    }

    public ExitWireJunction getExitJunction() {
        return exitJunction;
    }

    public WireDirection getExitDirection() {
        return exitJunction.getDirection();
    }

    public int getExitWireLength() {
        return exitJunction.getWireLength();
    }

    public String getTileName() {
        return tileName;
    }

    public ArrayList<String> getNodePath() {
        return nodePath;
    }

    public String getNodeName(int i) {
        if (i < 0)
            i += nodePath.size();
        return nodePath.get(i);
    }

    public void commitPIPsToNet(Design d, Net net) {
        for (int i = 0; i < nodePath.size() - 1; i++) {
            RouteForge.findAndRoute(d, net, tileName, nodePath.get(i), nodePath.get(i + 1));
        }
    }

    @Override
    public String toString() {
        String repr = "";
        for (int i = 0; i < nodePath.size() - 1; i++) {
            repr += "<" + nodePath.get(i) + "> --> ";
        }
        repr += "<" + nodePath.get(nodePath.size() - 1) + ">";

        return repr;
    }

    public String shortHand() {
        return enterJunction.toString() + " --> " + exitJunction.toString();
    }
}
