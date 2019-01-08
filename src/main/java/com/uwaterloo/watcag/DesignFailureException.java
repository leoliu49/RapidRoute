package com.uwaterloo.watcag;

import com.uwaterloo.watcag.util.RouterLog;

public class DesignFailureException extends RuntimeException {

    public DesignFailureException(String msg) {
        super(msg);
        RouterLog.error(msg);
        RouterLog.log("Design will exit prematurely.", RouterLog.Level.WARNING);
    }

}
