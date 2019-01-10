package com.uwaterloo.watcag.router;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class ThreadPool {

    private static int THREAD_POOL_SIZE = 4;
    private static final ArrayList<ThreadedJob> threadPool = new ArrayList<>();
    private static final LinkedList<Integer> freeThreads = new LinkedList<>();
    private static final LinkedList<ThreadedJob> waitQueue = new LinkedList<>();

    private static final Set<ThreadedJob> deadThreads = new HashSet<>();

    public static int getThreadPoolSize() {
        return THREAD_POOL_SIZE;
    }

    public static void initThreadPool(int threadPoolSize) {
        THREAD_POOL_SIZE = threadPoolSize;
        reset();
    }

    public static void reset() {
        threadPool.clear();
        freeThreads.clear();
        waitQueue.clear();

        for (int i = 0; i < THREAD_POOL_SIZE; i++) {
            threadPool.add(null);
            freeThreads.add(i);
        }
    }

    private static boolean isAllThreadsFree() {
        synchronized (freeThreads) {
            return freeThreads.size() == THREAD_POOL_SIZE;
        }
    }

    /*
     * Returns -1 if no threads available
     * Return allocated thread ID otherwise
     */
    private static int tryAllocThread(ThreadedJob thread) {
        int threadID;
        synchronized (freeThreads) {
            if (freeThreads.isEmpty())
                return -1;

            threadID = freeThreads.remove();
        }

        synchronized (threadPool) {
            threadPool.set(threadID, thread);
        }

        return threadID;
    }

    private static void deallocThread(int threadID) {
        synchronized (freeThreads) {
            if (!freeThreads.contains(threadID))
                freeThreads.add(threadID);
        }
        synchronized (threadPool) {
            threadPool.set(threadID, null);
        }
    }

    private static boolean isWaitQueueEmpty() {
        synchronized (waitQueue) {
            return waitQueue.isEmpty();
        }
    }

    /*
     * Remove longest-waiting thread
     * Returns null if no threads waiting
     */
    private static ThreadedJob popNextWaitingThread() {
        synchronized (waitQueue) {
            if (waitQueue.isEmpty())
                return null;
            return waitQueue.remove();
        }
    }

    private static void registerNewDeadThread(ThreadedJob thread) {
        synchronized (deadThreads) {
            deadThreads.add(thread);
        }
    }

    public static void scheduleNewJob(ThreadedJob thread) {
        int nextThreadID = tryAllocThread(thread);
        if (nextThreadID == -1) {
            thread.setThreadID(-2);
            waitQueue.add(thread);
        }
        else {
            thread.setThreadID(nextThreadID);
            thread.start();
        }
    }

    /*
     * Clear current thread
     * If any threads are waiting, allow it proceed
     */
    public static void completeJob(int threadID, ThreadedJob thread) {
        if (threadID != -1) {
            deallocThread(threadID);
            registerNewDeadThread(thread);

            ThreadedJob nextThread = popNextWaitingThread();
            if (nextThread != null)
                scheduleNewJob(nextThread);
        }
    }

    /*
     * Yield until all child threads run to completion
     */
    public static void yieldUntilChildThreadsFinish(Set<ThreadedJob> children) throws InterruptedException {
        while (true) {
            synchronized (deadThreads) {
                if (deadThreads.containsAll(children))
                    break;
            }
            Thread.sleep(1000);
        }

        for (ThreadedJob child : children) {
            child.join();
            deadThreads.remove(child);
        }
    }

    public static void runToCompletion() throws InterruptedException {

        boolean isComplete = false;
        while (!isComplete) {
            isComplete = isWaitQueueEmpty() && isAllThreadsFree();
            Thread.sleep(500);
        }

        // Clean up remaining threads
        for (Thread t : threadPool) {
            if (t != null)
                t.join();
        }

        for (Thread t : deadThreads) {
            t.join();
        }
        deadThreads.clear();
    }


}
