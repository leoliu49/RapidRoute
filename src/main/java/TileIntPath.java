import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.device.Tile;

import java.util.ArrayList;

public class TileIntPath {

    /*
     * A path within a single switchbox (essentially a collection of PIPs)
     */

    private WireDirection direction;
    private int cost;

    private EnteringTileJunction enteringJunction;
    private ExitingTileJunction exitingJunction;
    private int exitWireLength;

    private String tileName;

    // Inclusive of entering/exiting nodes
    private ArrayList<String> nodePath;

    public TileIntPath(EnteringTileJunction enteringJunction, WireDirection direction) {
        this.direction = direction;
        this.enteringJunction = enteringJunction;
        tileName = enteringJunction.getTileName();

        nodePath = new ArrayList<String>();
        nodePath.add(enteringJunction.getNodeName());
    }

    /*
     * Deep copy constructor
     */
    public TileIntPath(TileIntPath ref) {
        direction = ref.getDirection();
        cost = ref.getCost();

        enteringJunction = ref.getEnteringJunction();
        exitingJunction = ref.getExitingJunction();
        exitWireLength = ref.getExitWireLength();

        tileName = ref.getTileName();
        nodePath = new ArrayList<String>(ref.getNodePath());

    }

    public WireDirection getDirection() {
        return direction;
    }

    public int getCost() {
        return cost;
    }

    public EnteringTileJunction getEnteringJunction() {
        return enteringJunction;
    }

    public ExitingTileJunction getExitingJunction() {
        return exitingJunction;
    }

    public void setExitingJunction(ExitingTileJunction exitingJunction) {
        this.exitingJunction = exitingJunction;
        exitWireLength = exitingJunction.getWireLength();
        nodePath.add(exitingJunction.getNodeName());
        cost += 1;
    }

    public int getExitWireLength() {
        return exitWireLength;
    }

    public String getNodeTransform(Design d) {
        return RouteUtil.nodeEndTransform(d, tileName, exitWireLength, exitingJunction.getWireName(), direction);
    }

    public String getWireTransform(Design d) {
        return RouteUtil.wireEndTransform(d, tileName, exitingJunction.getWireName());
    }

    public String getTileName() {
        return tileName;
    }

    public ArrayList<String> getNodePath() {
        return nodePath;
    }

    public String getLastNode() {
        return nodePath.get(nodePath.size() - 1);
    }

    public boolean addNode(String nodeName) {
        if (nodePath.contains(nodeName))
            return false;
        nodePath.add(nodeName);
        cost += 1;
        return true;
    }

    public boolean addNode(String nodeName, ArrayList<String> footprint) {
        if (nodePath.contains(nodeName) || footprint.contains(nodeName))
            return false;
        nodePath.add(nodeName);
        footprint.add(nodeName);
        cost += 1;
        return true;
    }

    public void commitPIPsToNet(Design d, Net n) {
        for (int i = 0; i < nodePath.size() - 1; i++) {
            //CustomRouter.findAndRoute(d, n, tileName, nodePath.get(i), nodePath.get(i + 1));
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

    public static TileIntPath duplWithShift(Design d, TileIntPath ref, int dx, int dy) {

        Tile refTile = d.getDevice().getTile(ref.getTileName());
        Tile resTile = refTile.getTileXYNeighbor(dx, dy);

        TileIntPath res = new TileIntPath(ref);
        res.enteringJunction = EnteringTileJunction.duplWithShift(d, ref.getEnteringJunction(), dx, dy);
        res.exitingJunction = ExitingTileJunction.duplWithShift(d, ref.getExitingJunction(), dx, dy);

        res.tileName = resTile.getName();

        for (int i = 0; i < res.getNodePath().size(); i++) {
            String resNodeName = res.getNodePath().get(i).replaceAll("INT_X\\d+Y\\d+", resTile.getName());
            res.getNodePath().set(i, resNodeName);
        }

        return res;
    }

}
