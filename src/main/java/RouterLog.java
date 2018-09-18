import java.util.HashMap;

public class RouterLog {

    public enum Level {
        VERBOSE, NORMAL, WARNING, ERROR
    }

    private static HashMap<Integer, String> prefixMap = null;
    private static int indentLevel = 0;
    private static Level logLevel = Level.VERBOSE;

    public static void init(Level level) {
        logLevel = level;

        prefixMap = new HashMap<Integer, String>();
        prefixMap.put(Level.VERBOSE.ordinal(), "VERBOSE\t");
        prefixMap.put(Level.NORMAL.ordinal(), "NORMAL\t");
        prefixMap.put(Level.WARNING.ordinal(), "WARNING\t");
        prefixMap.put(Level.ERROR.ordinal(), "ERROR\t");
    }

    public static void log(String msg, Level level) {
        if (prefixMap == null)
            init(level);
        if (level.ordinal() >= logLevel.ordinal()) {
            msg = msg.replace("\n", "\n" + prefixMap.get(level.ordinal()) + "\t");
            System.out.println(prefixMap.get(level.ordinal()) + msg);
        }
    }

    public static void debug(String msg) {
        msg = msg.replace("\n", "\nDEBUG\t");
        System.out.println("DEBUG\t" + msg);
    }

    public static void setLevel(Level level) {
        RouterLog.logLevel = level;
    }

    public static void indent() {
        indentLevel += 1;
        for (int key : prefixMap.keySet()) {
            prefixMap.replace(key, prefixMap.get(key) + "\t");
        }
    }

    public static void indent(int delta) {
        indentLevel += delta;
        String indentString = "";
        for (int i = 0; i < indentLevel; i++)
            indentString += "\t";

        prefixMap.put(Level.VERBOSE.ordinal(), "VERBOSE\t" + indentString);
        prefixMap.put(Level.NORMAL.ordinal(), "NORMAL\t" + indentString);
        prefixMap.put(Level.WARNING.ordinal(), "WARNING\t" + indentString);
        prefixMap.put(Level.ERROR.ordinal(), "ERROR\t" + indentString);
    }

}
