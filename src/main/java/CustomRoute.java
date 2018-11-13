import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.device.Tile;

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
    private ArrayList<ArrayList<TilePath>> pathSubs;

    public CustomRoute(RouteTemplate template) {
        cost = 0;

        src = template.getSrc();
        snk = template.getSnk();

        distanceX = template.getDistanceX();
        distanceY = template.getDistanceY();

        this.template = template;

        int size = template.getTemplate().size();
        route = new ArrayList<>();
        pathSubs = new ArrayList<>();
        for (int i = 0; i < size / 2; i++) {
            route.add(null);
            pathSubs.add(null);
        }
    }

    public CustomRoute copyWithOffset(Design d, int dx, int dy) {
        CustomRoute copy = new CustomRoute(template.copyWithOffset(d, dx, dy));

        copy.setBitIndex(bitIndex);
        copy.setRouteIndex(routeIndex);

        for (int i = 0; i < route.size(); i++) {
            copy.setPath(i, route.get(i).copyWithOffset(d, dx, dy));
        }

        return copy;
    }

    /*
    private CustomRoute(Design d, EnterWireJunction src, ExitWireJunction snk) {
        cost = 0;

        this.src = src;
        this.snk = snk;

        Tile srcIntTile = d.getDevice().getTile(src.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snk.getTileName());

        distanceX = snkIntTile.getTileXCoordinate() - srcIntTile.getTileXCoordinate();
        distanceY = snkIntTile.getTileYCoordinate() - srcIntTile.getTileYCoordinate();

        route = new ArrayList<>();
    }
    */

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

    public TilePath getPath(int i) {
        if (i < 0)
            i += route.size();
        return route.get(i);
    }

    public TilePath getNextPossiblePath(int i) {
        if (i < 0)
            i += pathSubs.size();

        ArrayList<TilePath> pathSub = pathSubs.get(i);
        if (pathSub.isEmpty())
            return null;

        pathSub.add(pathSub.remove(0));
        return pathSub.get(pathSub.size() - 1);
    }

    public void setPath(int i, TilePath path) {
        if (i < 0)
            i += pathSubs.size();
        route.set(i, path);
        cost += path.getCost();
    }

    public void removePath(int i) {
        cost -= route.get(i).getCost();
        route.set(i, null);
    }

    public ArrayList<TilePath> getPathSub(int i) {
        if (i < 0)
            i += pathSubs.size();
        return pathSubs.get(i);
    }

    public void setPathSub(int i, ArrayList<TilePath> pathSub) {
        if (i < 0)
            i += pathSubs.size();
        pathSubs.set(i, pathSub);
    }

    public void commitToNet(Design d, Net net) {
        for (TilePath path : route)
            path.commitPIPsToNet(d, net);
    }
}
