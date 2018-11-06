import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class RoutesCache {

    /*
     * Cache of previous RegisterConnections, and RoutingFootprints
     */

    public static HashMap<RegisterConnection, List<RoutingFootprint>> connectionToFootprintMap = new HashMap<>();

    public static List<RoutingFootprint> getFootprints(RegisterConnection connection) {
        return connectionToFootprintMap.get(connection);
    }

    public static void cache(RegisterConnection connection, RoutingFootprint footprint) {
        if (connectionToFootprintMap.containsKey(connection))
            connectionToFootprintMap.get(connection).add(footprint);
        else {
            List<RoutingFootprint> footprints = new LinkedList<>();
            footprints.add(footprint);
            connectionToFootprintMap.put(connection, footprints);
        }
    }

    public static RegisterConnection searchCacheForCongruentConnection(Design d, RegisterConnection connection) {
        for (RegisterConnection c : connectionToFootprintMap.keySet()) {
            if (c.isCongruentWith(d, connection))
                return c;
        }
        return null;
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
