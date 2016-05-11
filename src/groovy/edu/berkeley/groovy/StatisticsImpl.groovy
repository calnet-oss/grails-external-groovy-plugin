/**
 * Copyright (c) 2016, Regents of the University of California and
 * contributors.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
