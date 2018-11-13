import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class RoutesCache {

    /*
     * Cache of previous RegisterConnections, and RoutingFootprints
     */

    public static HashMap<RegisterConnection, RoutingFootprint> connectionToFootprintMap = new HashMap<>();

    public static RoutingFootprint getFootprint(RegisterConnection connection) {
        return connectionToFootprintMap.get(connection);
    }

    public static void cache(RegisterConnection connection, RoutingFootprint footprint) {
        connectionToFootprintMap.put(connection, footprint);
    }

    public static ArrayList<RegisterConnection> searchCacheForCongruentConnection(Design d, RegisterConnection connection) {
        ArrayList<RegisterConnection> results = new ArrayList<>();
        for (RegisterConnection c : connectionToFootprintMap.keySet()) {
            if (c.isCongruentWith(d, connection))
                results.add(c);
        }
        return results;
    }

    public static int getXOffsetOfCongruentConnection(Design d, RegisterConnection ref, RegisterConnection offset) {
        Tile refIntTile = d.getDevice().getSite(ref.getSrcReg().getComponent(0).getSiteName()).getIntTile();
        Tile offsetIntTile = d.getDevice().getSite(offset.getSrcReg().getComponent(0).getSiteName()).getIntTile();

        return offsetIntTile.getTileXCoordinate() - refIntTile.getTileXCoordinate();
    }

    public static int getYOffsetOfCongruentConnection(Design d, RegisterConnection ref, RegisterConnection offset) {
        Tile refIntTile = d.getDevice().getSite(ref.getSrcReg().getComponent(0).getSiteName()).getIntTile();
        Tile offsetIntTile = d.getDevice().getSite(offset.getSrcReg().getComponent(0).getSiteName()).getIntTile();

        return offsetIntTile.getTileYCoordinate() - refIntTile.getTileYCoordinate();
    }

}
