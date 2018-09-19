import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;

import java.util.ArrayList;

public class CustomRoute {

    /*
     * Wrapper around an array of TileIntPaths, which make up a complete connection
     */

    private int cost;

    private boolean isRouteComplete;

    private EnteringTileJunction srcJunction;
    private ExitingTileJunction snkJunction;

    // +/- determines distance
    private int distanceX;
    private int distanceY;

    private int bitIndex;

    private ArrayList<TileIntPath> route;
    private ArrayList<TileJunction> routeTemplate;

    // Alternatives to route above
    private ArrayList<ArrayList<TileIntPath>> pathSubs;

    public CustomRoute(Design d, EnteringTileJunction srcJunction, ExitingTileJunction snkJunction) {

        cost = 0;
        isRouteComplete = false;

        this.srcJunction = srcJunction;
        this.snkJunction = snkJunction;

        distanceX = d.getDevice().getTile(snkJunction.getTileName()).getTileXCoordinate()
                - d.getDevice().getTile(srcJunction.getTileName()).getTileXCoordinate();
        distanceY = d.getDevice().getTile(snkJunction.getTileName()).getTileYCoordinate()
                - d.getDevice().getTile(srcJunction.getTileName()).getTileYCoordinate();

        route = new ArrayList<TileIntPath>();
    }

    public CustomRoute(Design d, EnteringTileJunction srcJunction, ExitingTileJunction snkJunction,
                       ArrayList<EnteringTileJunction> templateEnterJunctions) {

        cost = 0;
        isRouteComplete = false;

        this.srcJunction = srcJunction;
        this.snkJunction = snkJunction;

        distanceX = d.getDevice().getTile(snkJunction.getTileName()).getTileXCoordinate()
                - d.getDevice().getTile(srcJunction.getTileName()).getTileXCoordinate();
        distanceY = d.getDevice().getTile(snkJunction.getTileName()).getTileYCoordinate()
                - d.getDevice().getTile(srcJunction.getTileName()).getTileYCoordinate();

        route = new ArrayList<TileIntPath>();

        routeTemplate = new ArrayList<TileJunction>();
        routeTemplate.add(srcJunction);
        for (int i = 1; i < templateEnterJunctions.size(); i++) {
            routeTemplate.add(templateEnterJunctions.get(i).getWireSourceJunction(d));
            routeTemplate.add(templateEnterJunctions.get(i));
        }
        routeTemplate.add(snkJunction);
    }

    public int getCost() {
        return cost;
    }

    public boolean isRouteComplete() {
        return isRouteComplete;
    }

    public void setRouteComplete() {
        isRouteComplete = true;
    }

    public EnteringTileJunction getSrcJunction() {
        return srcJunction;
    }

    public ExitingTileJunction getSnkJunction() {
        return snkJunction;
    }

    public int getDistanceX() {
        return distanceX;
    }

    public int getDistanceY() {
        return distanceY;
    }

    public int getBitIndex() {
        return bitIndex;
    }

    public void setBitIndex(int bitIndex) {
        this.bitIndex = bitIndex;
    }

    public ArrayList<TileIntPath> getRoute() {
        return route;
    }

    public void addPathToRoute(TileIntPath path) {
        int index = route.size();
        route.add(path);
        cost += path.getCost();
    }

    public void addNextPathToRoute() {
        int index = route.size();
        TileIntPath path = pathSubs.get(index).get(0);
        route.add(path);
        cost += path.getCost();
    }

    public void ignoreNextPath() {
        int index = route.size();
        TileIntPath path = pathSubs.get(index).get(0);

        // Path is not actually removed, but shuffled to the back
        pathSubs.get(index).remove(path);
        pathSubs.get(index).add(path);
    }

    public void revert(int index) {
        while (route.size() != index) {
            TileIntPath revertedPath = route.get(route.size() - 1);
            cost -= revertedPath.getCost();
            route.remove(route.size() - 1);
        }

        // Path is not actually removed, but shuffled to the back
        TileIntPath deadPath = pathSubs.get(index).get(0);
        pathSubs.get(index).remove(0);
        pathSubs.get(index).add(deadPath);
    }

    public ArrayList<TileJunction> getRouteTemplate() {
        return routeTemplate;
    }

    public int getRouteTemplateSize() {
        // 2 tile junctions for every TileIntPath
        return routeTemplate.size() / 2;
    }

    public void setRouteTemplate(ArrayList<TileJunction> template) {
        this.routeTemplate = template;
    }

    public ArrayList<ArrayList<TileIntPath>> getPathSubs() {
        return pathSubs;
    }

    public TileIntPath getNextCandidatePath() {
        int index = route.size();
        return pathSubs.get(index).get(0);
    }

    public void derivePathSubsFromTemplate(Design d) {
        int totalPerms = 1;

        pathSubs = new ArrayList<ArrayList<TileIntPath>>();

        RouterLog.log("Deriving TileIntPaths for each TileJunction pair for <" + srcJunction + "> --> <"
                + snkJunction + ">.", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (int i = 0; i < routeTemplate.size(); i += 2) {
            ArrayList<TileIntPath> paths = TileBrowser.findIntPaths(d, routeTemplate.get(i).getTileName(),
                    (EnteringTileJunction) routeTemplate.get(i), (ExitingTileJunction) routeTemplate.get(i + 1));
            pathSubs.add(paths);
            RouterLog.log("Added " + paths.size() + " paths to " + routeTemplate.get(i).toString()
                    + " --> " + routeTemplate.get(i + 1).toString(), RouterLog.Level.VERBOSE);
            totalPerms *= paths.size();
        }
        RouterLog.log("" + totalPerms + " possible routes found.",
                RouterLog.Level.NORMAL);

        RouterLog.indent(-1);
    }

    public void commitToNet(Design d, Net net) {
        for (TileIntPath path : route)
            path.commitPIPsToNet(d, net);
    }

    @Override
    public String toString() {
        String repr = "[route]";
        repr += srcJunction.toString();
        repr += " --> ";
        repr += snkJunction.toString();
        //repr += "[" + RouteUtil.directionToString(direction) + distance + "]";

        return repr;
    }

    /*
     * Joins 2 routes back-to-back
     * pathSubs destroyed
     */
    public static CustomRoute join(Design d, CustomRoute r1, CustomRoute r2) {
        CustomRoute res = new CustomRoute(d, r1.getSrcJunction(), r2.getSnkJunction());
        ArrayList<TileJunction> routeTemplate = r1.getRouteTemplate();

        routeTemplate.addAll(r2.getRouteTemplate());
        res.setRouteTemplate(routeTemplate);

        if (r1.getRoute() != null) {
            for (TileIntPath path : r1.getRoute())
                res.addPathToRoute(path);
        }
        if (r2.getRoute() != null) {
            for (TileIntPath path : r2.getRoute())
                res.addPathToRoute(path);
        }
        return res;
    }

    /*
     * Create clone and then shift every TileIntPath by dx & dy
     * pathSubs destroyed
     */
    public static CustomRoute duplWithShift(Design d, CustomRoute ref, int dx, int dy) {
        CustomRoute res = new CustomRoute(d, EnteringTileJunction.duplWithShift(d, ref.getSrcJunction(), dx, dy),
                ExitingTileJunction.duplWithShift(d, ref.getSnkJunction(), dx, dy));
        res.cost = ref.getCost();
        res.isRouteComplete = ref.isRouteComplete();
        res.distanceX = ref.getDistanceX();
        res.distanceY = ref.getDistanceY();
        res.setBitIndex(ref.getBitIndex());

        for (TileIntPath path : ref.getRoute()) {
            res.getRoute().add(TileIntPath.duplWithShift(d, path, dx, dy));
        }
        return res;
    }

}
