package edu.berkeley.groovy

class StatisticsImpl implements Statistics {
    private volatile long loadedCount
    private volatile long unloadedCount
    private volatile long compiledCount

    synchronized void signalClassLoaderLoad() {
        loadedCount++
    }

    synchronized void signalClassLoaderUnload() {
        unloadedCount++
    }

    synchronized void signalCompiled() {
        compiledCount++
    }

    synchronized long getLoadedCount() {
        return loadedCount
    }

    synchronized long getUnloadedCount() {
        return unloadedCount
    }

    synchronized long getCompiledCount() {
        return compiledCount
    }
}
