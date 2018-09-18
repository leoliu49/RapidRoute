package com.uwaterloo.watcag.router.browser;

import com.uwaterloo.watcag.router.elements.WireJunction;
import com.uwaterloo.watcag.util.RouteUtil;

public class JunctionsTracer {

    private static final int V_LONG_LINE_LENGTH = 12;
    private static final int H_LONG_LINE_LENGTH = 6;

    private int depth;
    private int estimatedCost;

    private WireJunction junction;
    private JunctionsTracer parent;

    private JunctionsTracer(WireJunction junction) {
        depth = 0;
        estimatedCost = junction.getTilePathCost();

        this.junction = junction;
        parent = null;
    }

    public JunctionsTracer(WireJunction junction, JunctionsTracer parent, int switchCost) {
        depth = parent.getDepth() + 1;
        estimatedCost = parent.getEstimatedCost() + switchCost;
        this.junction = junction;
        this.parent = parent;

        readjustCost(junction);
    }

    public int getDepth() {
        return depth;
    }

    public int getEstimatedCost() {
        return estimatedCost;
    }

    private void readjustCost(WireJunction newJunction) {
        int delta = 0;

        // Punish short hops
        if (newJunction.getWireLength() < H_LONG_LINE_LENGTH)
            delta += 4;
        // Punish reversals, although sometimes they are necessary
        if (RouteUtil.reverseDirection(newJunction.getDirection()).equals(parent.getJunction().getDirection()))
            delta += 2;
            // Punish orthogonal turns, which are very slow
        else if (RouteUtil.isOrthogonal(newJunction.getDirection(), parent.getJunction().getDirection()))
            delta += 8;

        estimatedCost += delta;
    }

    public WireJunction getJunction() {
        return junction;
    }

    public JunctionsTracer getParent() {
        return parent;
    }

    public static JunctionsTracer newHeadTracer(WireJunction head) {
        return new JunctionsTracer(head);
    }
}
