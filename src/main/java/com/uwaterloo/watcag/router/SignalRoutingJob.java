package com.uwaterloo.watcag.router;

import com.uwaterloo.watcag.router.browser.FabricBrowser;
import com.uwaterloo.watcag.router.elements.*;
import com.xilinx.rapidwright.design.Design;

import java.util.HashSet;
import java.util.Set;

public class SignalRoutingJob {

    private static final int SINK_TILE_TRAVERSAL_MAX_DEPTH = 8;
    private static final int TILE_TRAVERSAL_MAX_DEPTH = FabricBrowser.TILE_TRAVERSAL_MAX_DEPTH;

    private long tBegin;
    private long tEnd;

    private Design coreDesign;

    private EnterWireJunction srcJunction;
    private ExitWireJunction snkJunction;

    private CustomRoute route;

    public SignalRoutingJob(Design d, EnterWireJunction srcJunction, ExitWireJunction snkJunction) {
        coreDesign = d;

        this.srcJunction = srcJunction;
        this.snkJunction = snkJunction;
    }

    public EnterWireJunction getSrcJunction() {
        return srcJunction;
    }

    public ExitWireJunction getSnkJunction() {
        return snkJunction;
    }

    public CustomRoute getRoute() {
        return route;
    }

    public void run() throws Exception {

        RouteForge.lock(srcJunction.getNodeName());
        RouteForge.lock(snkJunction.getNodeName());

        Set<String> banList = new HashSet<>();
        while (true) {
            ThreadedSearchJob job = new ThreadedSearchJob(coreDesign, srcJunction, snkJunction);
            job.setBatchSize(1);
            job.setBanList(banList);
            job.setLeadIns(FabricBrowser.findReachableEntrances(coreDesign, snkJunction));

            RouteTemplate template = job.call().get(0);
            route = new CustomRoute(template);

            boolean pathFailed = false;
            for (int i = 0; i < template.getTemplate().size() - 2; i += 2) {
                TilePath path = FabricBrowser.findClosestTilePath(coreDesign,
                        (EnterWireJunction) template.getTemplate(i),
                        (ExitWireJunction) template.getTemplate(i + 1), new HashSet<>());

                if (path == null) {
                    pathFailed = true;
                    banList.add(template.getTemplate(i).getNodeName());
                    break;
                }

                route.setPath(i / 2, path);
            }

            if (pathFailed)
                continue;

            TilePath sinkPath = FabricBrowser.findClosestTilePath(coreDesign, TILE_TRAVERSAL_MAX_DEPTH,
                    (EnterWireJunction) template.getTemplate(-2), snkJunction, new HashSet<>());

            if (sinkPath == null) {
                banList.add(template.getTemplate(-2).getNodeName());
                continue;
            }

            route.setPath(-1, sinkPath);
            break;
        }
    }


}
