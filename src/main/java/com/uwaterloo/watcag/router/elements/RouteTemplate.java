package com.uwaterloo.watcag.router.elements;

import com.uwaterloo.watcag.util.RouteUtil;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

import java.util.*;

public class RouteTemplate {

    /*
     * Wrapper around an ArrayList of WireJunctions, which describe the hops needed to complete a route
     */

    private static final int V_LONG_LINE_LENGTH = 12;
    private static final int H_LONG_LINE_LENGTH = 6;

    private int estimatedCost;

    private EnterWireJunction src;
    private ExitWireJunction snk;

    private int distanceX;
    private int distanceY;

    // Inclusive of src and snk
    private ArrayList<WireJunction> template;

    public RouteTemplate(Design d, EnterWireJunction src, ExitWireJunction snk) {
        estimatedCost = 0;

        this.src = src;
        this.snk = snk;

        Tile srcIntTile = d.getDevice().getTile(src.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snk.getTileName());

        distanceX = snkIntTile.getTileXCoordinate() - srcIntTile.getTileXCoordinate();
        distanceY = snkIntTile.getTileYCoordinate() - srcIntTile.getTileYCoordinate();

        template = new ArrayList<>();
        template.add(src);
        template.add(snk);
    }

    public RouteTemplate copyWithOffset(Design d, int dx, int dy) {
        RouteTemplate copy = new RouteTemplate(d, src.copyWithOffset(d, dx, dy), snk.copyWithOffset(d, dx, dy));
        for (int i = 1; i < template.size() - 1; i++) {
            copy.getTemplate().add(i, template.get(i).copyWithOffset(d, dx, dy));
        }
        return copy;
    }

    public int getEstimatedCost() {
        return estimatedCost;
    }

    public void readjustCost() {
        estimatedCost = 0;

        for (int i = 1; i < template.size() - 1; i += 2) {
            WireJunction junction = template.get(i);

            int delta = junction.getTilePathCost();
            // Punish short hops
            if (junction.getWireLength() < H_LONG_LINE_LENGTH)
                delta += 4;
            // Punish reversals, although sometimes they are necessary
            if (RouteUtil.reverseDirection(junction.getDirection()).equals(template.get(i - 1).getDirection()))
                delta += 2;
            // Punish orthogonal turns, which are very slow
            else if (RouteUtil.isOrthogonal(junction.getDirection(), template.get(i - 1).getDirection()))
                delta += 8;

            estimatedCost += delta;
        }
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

    public ArrayList<WireJunction> getTemplate() {
        return template;
    }

    public WireJunction getTemplate(int i) {
        if (i < 0)
            i += template.size();
        return template.get(i);
    }

    public boolean isEmpty() {
        return template.isEmpty();
    }

    public void pushEnterWireJunction(Design d, EnterWireJunction enJunc) {
        template.add(1, enJunc);
        template.add(1, enJunc.getSrcJunction(d));
    }

    public void replaceTemplate(Design d, EnterWireJunction enter, ExitWireJunction exit, RouteTemplate replacement) {
        ArrayList<WireJunction> newTemplate = new ArrayList<>();
        int startIndex = 0;
        int endIndex = 0;
        for (int i = 0; i < template.size(); i++) {
            if (enter.equals(template.get(i)))
                startIndex = i;
            else if (exit.equals(template.get(i)))
                endIndex = i;
        }

        for (int i = 0; i < startIndex; i++) {
            newTemplate.add(template.get(i));
        }
        for (WireJunction junction : replacement.getTemplate()) {
            newTemplate.add(junction);
        }
        for (int i = endIndex + 1; i < template.size(); i++) {
            newTemplate.add(template.get(i));
        }

        template = newTemplate;

        readjustCost();
    }

    @Override
    public String toString() {
        String repr = "";
        for (int i = 0; i < template.size() - 1; i++)
            repr += template.get(i).toString() + " --> ";
        repr += template.get(template.size() - 1);
        return repr;
    }

    public Set<String> getUsage() {
        Set<String> usage = new HashSet<>();
        for (WireJunction junction : template)
            usage.add(junction.getNodeName());
        return usage;
    }

    public String hopSummary() {
        String repr = "<";
        for (int i = 1; i < template.size() - 3; i += 2) {
            repr += RouteUtil.directionToString(template.get(i).getDirection());
            repr += template.get(i).getWireLength();
            repr += " ";
        }
        repr += RouteUtil.directionToString(template.get(template.size() - 2).getDirection());
        repr += template.get(template.size() - 2).getWireLength();
        repr += ">[" + estimatedCost + "]";
        return repr;
    }
}
