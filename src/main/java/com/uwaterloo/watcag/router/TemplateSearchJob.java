package com.uwaterloo.watcag.router;

import com.uwaterloo.watcag.DesignFailureException;
import com.uwaterloo.watcag.router.browser.FabricBrowser;
import com.uwaterloo.watcag.router.browser.JunctionsTracer;
import com.uwaterloo.watcag.router.elements.EnterWireJunction;
import com.uwaterloo.watcag.router.elements.ExitWireJunction;
import com.uwaterloo.watcag.router.elements.RouteTemplate;
import com.uwaterloo.watcag.router.elements.WireDirection;
import com.uwaterloo.watcag.util.RouteUtil;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

public class TemplateSearchJob {

    /*
     * Creates and populates a route template given a source/sink pair
     * Routing is done via a PriorityQueue, prioritizing lowest estimated cost
     */

    private static final int V_LONG_LINE_THRESHOLD = 12;
    private static final int H_LONG_LINE_THRESHOLD = 6;

    private long tBegin;
    private long tEnd;

    private Design coreDesign;

    private EnterWireJunction src;
    private ExitWireJunction snk;

    private int batchSize;
    private Set<String> banList;

    private PriorityQueue<JunctionsTracer> searchQueue;
    private Set<String> searchFootprint;
    private Set<EnterWireJunction> leadIns;

    private ArrayList<RouteTemplate> results;

    public TemplateSearchJob(Design d, EnterWireJunction src, ExitWireJunction snk) {
        super();

        coreDesign = d;

        this.src = src;
        this.snk = snk;

        batchSize = 1;
        banList = new HashSet<>();

        searchQueue = new PriorityQueue<>(new RoutingCalculator.JunctionsTracerCostComparator());
        searchFootprint = new HashSet<>();

        results = new ArrayList<>();
    }

    public void beginTiming() {
        tBegin = System.currentTimeMillis();
    }

    public void finishTiming() {
        tEnd = System.currentTimeMillis();
    }

    public long getElapsedTime() {
        return tEnd - tBegin;
    }

    public EnterWireJunction getSrc() {
        return src;
    }

    public ExitWireJunction getSnk() {
        return snk;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Set<String> getBanList() {
        return banList;
    }

    public void setBanList(Set<String> banList) {
        this.banList = banList;
    }

    public PriorityQueue<JunctionsTracer> getSearchQueue() {
        return searchQueue;
    }

    public void setSearchQueue(PriorityQueue<JunctionsTracer> searchQueue) {
        this.searchQueue = searchQueue;
    }

    public Set<String> getSearchFootprint() {
        return searchFootprint;
    }

    public void setSearchFootprint(Set<String> searchFootprint) {
        this.searchFootprint = searchFootprint;
    }

    public Set<EnterWireJunction> getLeadIns() {
        return leadIns;
    }

    public void setLeadIns(Set<EnterWireJunction> leadIns) {
        this.leadIns = leadIns;
    }

    public ArrayList<RouteTemplate> getResults() {
        return results;
    }

    public void run() {

        beginTiming();

        Tile srcIntTile = coreDesign.getDevice().getTile(src.getTileName());
        Tile snkIntTile = coreDesign.getDevice().getTile(snk.getTileName());

        int snkTileX = snkIntTile.getTileXCoordinate();
        int snkTileY = snkIntTile.getTileYCoordinate();

        int srcTileX = srcIntTile.getTileXCoordinate();
        int srcTileY = srcIntTile.getTileYCoordinate();

        for (EnterWireJunction leadIn : leadIns)
            System.out.println(leadIn.toString());

        if (searchQueue.isEmpty()) {

            JunctionsTracer srcTracer = JunctionsTracer.newHeadTracer(src);
            for (ExitWireJunction exit : FabricBrowser.findReachableExits(coreDesign, src)) {
                EnterWireJunction wireDest = exit.getDestJunction(coreDesign);
                if (RouteForge.isLocked(wireDest.getNodeName()) || RouteForge.isLocked(exit.getNodeName()))
                    continue;

                searchFootprint.add(wireDest.getNodeName());
                searchQueue.add(new JunctionsTracer(wireDest, srcTracer, exit.getTilePathCost()));
            }
        }

        int templateCount = 0;
        while (templateCount < batchSize) {
            JunctionsTracer trav = searchQueue.remove();
            EnterWireJunction travJunction = (EnterWireJunction) trav.getJunction();
            Tile travTile = coreDesign.getDevice().getTile(travJunction.getTileName());

            if (trav.getDepth() > 1000)
                throw new DesignFailureException("Route template search limit exceeded.");

            int distX = snkTileX - travTile.getTileXCoordinate();
            int distY = snkTileY - travTile.getTileYCoordinate();

            if (distX == 0 && distY == 0) {
                System.out.println("Checking: " + travJunction);

                boolean foundTemplate = false;
                EnterWireJunction validLeadIn = null;
                for (EnterWireJunction leadIn : leadIns) {
                    if (travJunction.equals(leadIn)) {
                        RouteTemplate template = new RouteTemplate(coreDesign, src, snk);
                        snk.setTilePathCost(leadIn.getTilePathCost());

                        while (trav.getDepth() > 0) {
                            template.pushEnterWireJunction(coreDesign, (EnterWireJunction) trav.getJunction());
                            trav = trav.getParent();
                        }

                        template.readjustCost();
                        results.add(template);

                        foundTemplate = true;
                        validLeadIn = leadIn;

                        searchFootprint.add(validLeadIn.getNodeName());
                        break;
                    }
                }

                if (foundTemplate) {
                    templateCount += 1;
                    leadIns.remove(validLeadIn);
                }

                continue;
            }

            ArrayList<WireDirection> primaryDirs = RouteUtil.primaryDirections(distX, distY);
            Set<ExitWireJunction> fanOut = FabricBrowser.getEntranceFanOut(coreDesign, travJunction);

            boolean isRepeatableLongLine = false;
            if (travJunction.getWireLength() >= H_LONG_LINE_THRESHOLD
                    && (Math.abs(distX) > H_LONG_LINE_THRESHOLD || Math.abs(distY) > V_LONG_LINE_THRESHOLD)) {
                isRepeatableLongLine = true;
            }

            if (isRepeatableLongLine) {
                for (ExitWireJunction exit : fanOut) {

                    if (exit.getDirection().equals(travJunction.getDirection())
                            && exit.getWireLength() >= H_LONG_LINE_THRESHOLD) {
                        EnterWireJunction wireDest = exit.getDestJunction(coreDesign);

                        if (RouteForge.isLocked(wireDest.getNodeName()) || RouteForge.isLocked(exit.getNodeName()))
                            continue;

                        if (searchFootprint.contains(wireDest.getNodeName()))
                            continue;

                        if (!primaryDirs.contains(exit.getDirection()))
                            continue;

                        if (banList.contains(wireDest.getNodeName()) || banList.contains(exit.getNodeName()))
                            continue;

                        searchQueue.add(new JunctionsTracer(wireDest, trav, 0));
                        searchFootprint.add(wireDest.getNodeName());
                    }
                }
            }
            else {
                for (ExitWireJunction exit : fanOut) {
                    EnterWireJunction wireDest = exit.getDestJunction(coreDesign);

                    if (wireDest == null)
                        continue;

                    if (RouteForge.isLocked(wireDest.getNodeName()) || RouteForge.isLocked(exit.getNodeName()))
                        continue;

                    if (searchFootprint.contains(wireDest.getNodeName()))
                        continue;

                    if (!primaryDirs.contains(exit.getDirection()))
                        continue;

                    if (banList.contains(wireDest.getNodeName()) || banList.contains(exit.getNodeName()))
                        continue;

                    searchQueue.add(new JunctionsTracer(wireDest, trav, exit.getTilePathCost()));
                    searchFootprint.add(wireDest.getNodeName());
                }
            }
        }

        finishTiming();
    }

}
