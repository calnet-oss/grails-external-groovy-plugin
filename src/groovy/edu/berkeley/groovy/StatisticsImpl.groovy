package edu.berkeley.groovy

class StatisticsImpl implements Statistics {
    private volatile long loadedCount // tracks ScriptClassLoader instantiations
    private volatile long unloadedCount // tracks ScriptClassLoader finalizations
    private volatile long compiledCount // compiled for the first time
    private volatile long recompiledCount // recompiled
    private Map<Integer, Long> compileCountMap = [:] // tracks total compilations for a class name

    synchronized void signalClassLoaderLoad() {
        loadedCount++
    }

    synchronized void signalClassLoaderUnload() {
        unloadedCount++
    }

    synchronized void signalCompiled(String className) {
        Integer key = className.hashCode()
        if (compileCountMap.containsKey(key)) {
            // exists so it's getting recompiled
            recompiledCount++
        } else {
            // doesn't exist yet so getting compiled for the first time
            compiledCount++
        }
        incrementTotalCompilationCountForClass(key)
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

    synchronized long getRecompiledCount() {
        return recompiledCount
    }

    synchronized long getTotalCompilationCount() {
        return compiledCount + recompiledCount
    }

    synchronized protected void incrementTotalCompilationCountForClass(Integer key) {
        if (!compileCountMap.containsKey(key)) {
            // not yet compiled, initialize to 1 compilation
            compileCountMap[key] = 1L
        } else {
            // been compiled before, increment the count
            compileCountMap[key] += 1L
        }
    }

    synchronized Long getTotalCompiledCountForClass(String className) {
        return compileCountMap[className.hashCode()]
    }
}
