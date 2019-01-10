package com.uwaterloo.watcag.router;

public class ThreadedJob extends Thread {

    // -1 indicates main thread, -2 indicates blocked
    protected int threadID = -1;

    public int getThreadID() {
        return threadID;
    }

    public void setThreadID(int threadID) {
        this.threadID = threadID;
    }

    @Override
    public String toString() {
        if (threadID == -1)
            return "<thread>[main]";
        else if (threadID == -2)
            return "<thread>[blocked]";
        return "<thread>[" + threadID + "]";
    }
}
