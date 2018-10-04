public class JunctionsTracer {

    private int depth;

    private JunctionsTracer parent;
    private ExitWireJunction junction;

    public JunctionsTracer(ExitWireJunction junction, JunctionsTracer parent, int depth) {
        this.junction = junction;
        this.parent = parent;
        this.depth = depth;
    }

    public JunctionsTracer(ExitWireJunction junction, int depth) {
        this.junction = junction;
        this.parent = null;
        this.depth = depth;
    }

    public int getDepth() {
        return depth;
    }

    public JunctionsTracer getParent() {
        return parent;
    }

    public ExitWireJunction getJunction() {
        return junction;
    }
}
