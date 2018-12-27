public class DesignFailureException extends RuntimeException {

    public DesignFailureException(String msg) {
        super(msg);
        RouterLog.error(msg);
        RouterLog.log("Design will exit prematurely.", RouterLog.Level.WARNING);
    }

}
