package com.uwaterloo.watcag.router.browser;

import com.uwaterloo.watcag.router.elements.EnterWireJunction;
import com.uwaterloo.watcag.router.elements.ExitWireJunction;
import com.uwaterloo.watcag.router.elements.TilePath;

import java.util.LinkedList;

public class TilePathTracer {

    private EnterWireJunction entrance;
    private ExitWireJunction exit;

    private String tileName;

    private boolean isReversed;
    private LinkedList<String> nodePath;

    public TilePathTracer(EnterWireJunction entrance) {
        this.entrance = entrance;

        tileName = entrance.getTileName();

        isReversed = false;
        nodePath = new LinkedList<>();
        nodePath.add(entrance.getNodeName());
    }

    public TilePathTracer(ExitWireJunction exit) {
        this.exit = exit;

        tileName = exit.getTileName();

        isReversed = true;
        nodePath = new LinkedList<>();
        nodePath.add(exit.getNodeName());
    }

    /*
     * Deep copy constructor
     */
    public TilePathTracer(TilePathTracer ref) {
        entrance = ref.getEntrance();
        exit = ref.getExit();

        tileName = ref.getTileName();

        isReversed = ref.isReversed;
        nodePath = new LinkedList<>(ref.getNodePath());
    }

    public int getLength() {
        return nodePath.size();
    }

    public EnterWireJunction getEntrance() {
        return entrance;
    }

    public void setEntrance(EnterWireJunction entrance) {
        this.entrance = entrance;
        nodePath.addFirst(entrance.getNodeName());
    }

    public ExitWireJunction getExit() {
        return exit;
    }

    public void setExit(ExitWireJunction exit) {
        this.exit = exit;
        nodePath.addLast(exit.getNodeName());
    }

    public String getTileName() {
        return tileName;
    }

    public LinkedList<String> getNodePath() {
        return nodePath;
    }

    public String getSearchHead() {
        return isReversed ? nodePath.getFirst() : nodePath.getLast();
    }

    public boolean addNode(String nodeName) {
        if (nodePath.contains(nodeName))
            return false;

        if (isReversed)
            nodePath.addFirst(nodeName);
        else
            nodePath.addLast(nodeName);
        return true;
    }
}
