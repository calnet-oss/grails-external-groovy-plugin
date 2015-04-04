package edu.berkeley.groovy

interface Statistics {
    void signalClassLoaderLoad()

    void signalClassLoaderUnload()

    void signalCompiled()
}
