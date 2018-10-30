public class WireJunction {


    protected WireDirection direction;
    protected String nodeName;
    protected String tileName;

    protected String wireName;
    protected int wireLength;

    public WireJunction(String tileName, String wireName) {
        this.tileName = tileName;
        this.wireName = wireName;

        this.nodeName = tileName + "/" + wireName;
    }

    public WireDirection getDirection() {
        return direction;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getTileName() {
        return tileName;
    }

    public String getWireName() {
        return wireName;
    }

    public int getWireLength() {
        return wireLength;
    }

    public boolean equals(WireJunction o) {
        return nodeName.equals(o.getNodeName());
    }

    @Override
    public String toString() {
        return "<" + nodeName + ">";
    }
}
