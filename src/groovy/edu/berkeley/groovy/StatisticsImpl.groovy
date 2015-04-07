package edu.berkeley.groovy

class StatisticsImpl implements Statistics {
    private volatile long loaderInstantiationCount // tracks ScriptClassLoader instantiations
    private volatile long loaderFinalizationCount // tracks ScriptClassLoader finalizations
    private volatile long compiledCount // compiled for the first time
    private volatile long recompiledCount // recompiled
    private Map<Integer, Long> compileCountMap = [:] // tracks total compilations for a class name

    synchronized void signalClassLoaderLoad() {
        loaderInstantiationCount++
    }

    synchronized void signalClassLoaderUnload() {
        loaderFinalizationCount++
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

    synchronized long getLoaderInstantiationCount() {
        return loaderInstantiationCount
    }

    synchronized long getLoaderFinalizationCount() {
        return loaderFinalizationCount
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
