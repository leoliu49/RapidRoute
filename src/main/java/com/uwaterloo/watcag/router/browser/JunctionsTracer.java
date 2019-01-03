package com.uwaterloo.watcag.router.browser;

import com.uwaterloo.watcag.router.elements.WireJunction;

import java.util.*;

public class JunctionsTracer {

    private int depth;
    private WireJunction junction;
    private JunctionsTracer parent;

    private JunctionsTracer(WireJunction head) {
        junction = head;
        depth = 0;
        parent = null;
    }

    public JunctionsTracer(WireJunction head, JunctionsTracer parent) {
        junction = head;
        depth = parent.getDepth() + 1;
        this.parent = parent;
    }

    public int getDepth() {
        return depth;
    }

    public WireJunction getJunction() {
        return junction;
    }

    public void fastForward(WireJunction next) {
        JunctionsTracer copy = new JunctionsTracer(junction, parent);
        junction = next;
        depth += 1;
        parent = copy;
    }

    public JunctionsTracer getParent() {
        return parent;
    }

    public static JunctionsTracer newHeadTracer(WireJunction head) {
        return new JunctionsTracer(head);
    }
}
