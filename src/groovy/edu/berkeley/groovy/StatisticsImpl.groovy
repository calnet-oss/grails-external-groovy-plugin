package edu.berkeley.groovy

class StatisticsImpl implements Statistics {
    private volatile long loadedCount
    private volatile long unloadedCount

    synchronized void signalClassLoaderLoad() {
        loadedCount++
    }

    synchronized void signalClassLoaderUnload() {
        unloadedCount++
    }

    synchronized long getLoadedCount() {
        return loadedCount
    }

    synchronized long getUnloadedCount() {
        return unloadedCount
    }
}
