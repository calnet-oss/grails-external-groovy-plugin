package edu.berkeley.groovy

import groovy.transform.Synchronized

class StatisticsImpl implements Statistics {
    private volatile long loaderInstantiationCount // tracks ScriptClassLoader instantiations
    private volatile long loaderFinalizationCount // tracks ScriptClassLoader finalizations
    private volatile long compiledCount // compiled for the first time
    private volatile long recompiledCount // recompiled
    private Map<Integer, Long> compileCountMap = [:] // tracks total compilations for a class name

    @Synchronized
    void signalClassLoaderLoad() {
        loaderInstantiationCount++
    }

    @Synchronized
    void signalClassLoaderUnload() {
        loaderFinalizationCount++
    }

    @Synchronized
    void signalCompiled(String className) {
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

    @Synchronized
    long getLoaderInstantiationCount() {
        return loaderInstantiationCount
    }

    @Synchronized
    long getLoaderFinalizationCount() {
        return loaderFinalizationCount
    }

    @Synchronized
    long getCompiledCount() {
        return compiledCount
    }

    @Synchronized
    long getRecompiledCount() {
        return recompiledCount
    }

    @Synchronized
    long getTotalCompilationCount() {
        return compiledCount + recompiledCount
    }

    @Synchronized
    protected void incrementTotalCompilationCountForClass(Integer key) {
        if (!compileCountMap.containsKey(key)) {
            // not yet compiled, initialize to 1 compilation
            compileCountMap[key] = 1L
        } else {
            // been compiled before, increment the count
            compileCountMap[key] += 1L
        }
    }

    @Synchronized
    Long getTotalCompiledCountForClass(String className) {
        return compileCountMap[className.hashCode()]
    }
}
