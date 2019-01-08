package com.uwaterloo.watcag.router.elements;

import com.uwaterloo.watcag.router.RouteForge;
import com.uwaterloo.watcag.util.RouteUtil;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.device.Tile;

import java.util.ArrayList;

public class TilePath {

    private int cost;

    private EnterWireJunction enterJunction;
    private ExitWireJunction exitJunction;

    private String tileName;

    // Inclusive of entering/exiting nodes
    private ArrayList<String> nodePath;

    public TilePath(EnterWireJunction enterJunction, ExitWireJunction exitJunction) {
        cost = 1;

        this.enterJunction = enterJunction;
        this.exitJunction = exitJunction;

        tileName = enterJunction.getTileName();

        nodePath = new ArrayList<>();
        nodePath.add(enterJunction.getNodeName());
        nodePath.add(exitJunction.getNodeName());
    }

    public TilePath(EnterWireJunction enterJunction, ExitWireJunction exitJunction, ArrayList<String> nodePath) {
        cost = 1;

        this.enterJunction = enterJunction;
        this.exitJunction = exitJunction;

        tileName = enterJunction.getTileName();

        this.nodePath = new ArrayList<>(nodePath);
    }

    /*
     * Deep copy constructor
     */
    public TilePath(TilePath ref) {
        cost = ref.getCost();

        enterJunction = ref.getEnterJunction();
        exitJunction = ref.getExitJunction();

        tileName = ref.getTileName();

        nodePath = new ArrayList<>(ref.getNodePath());
    }

    public TilePath copyWithOffset(Design d, int dx, int dy) {
        Tile offsetTile = d.getDevice().getTile(tileName).getTileXYNeighbor(dx, dy);

        TilePath copy = new TilePath(enterJunction.copyWithOffset(d, dx, dy), exitJunction.copyWithOffset(d, dx, dy));

        for (String nodeName : nodePath)
            copy.addNode(offsetTile.getName() + "/" + RouteUtil.extractNodeWireName(nodeName));

        return copy;
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

    public boolean addNode(String nodeName) {
        if (nodePath.contains(nodeName))
            return false;
        nodePath.add(nodePath.size() - 1, nodeName);
        cost += 1;
        return true;
    }

    public void commitPIPsToNet(Design d, Net net) {
        for (int i = 0; i < nodePath.size() - 1; i++) {
            RouteForge.findAndRoute(d, net, tileName, nodePath.get(i), nodePath.get(i + 1));
        }
    }

    public boolean equals(TilePath o) {
        if (this == o) {
            RouterLog.indent();
            RouterLog.log("TRUE BECAUSE THE OBJECTS ARE EQUAL", RouterLog.Level.NORMAL);
            RouterLog.indent(-1);
            return true;
        }

        if (nodePath.size() != o.getNodePath().size()) {
            RouterLog.indent();
            RouterLog.log("FALSE BECAUSE THE SIZES ARE NOT EQUAL", RouterLog.Level.NORMAL);
            RouterLog.indent(-1);
            return false;
        }

        for (int i = 0; i < nodePath.size(); i++) {
            if (!nodePath.get(i).equals(o.getNodePath().get(i))) {
                RouterLog.indent();
                RouterLog.log("FALSE BECAUSE " + nodePath.get(i) + " != " + o.getNodePath().get(i), RouterLog.Level.NORMAL);
                RouterLog.indent(-1);
                return false;
            }
        }

        RouterLog.indent();
        RouterLog.log("TRUE", RouterLog.Level.NORMAL);
        RouterLog.indent(-1);

        return true;
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
