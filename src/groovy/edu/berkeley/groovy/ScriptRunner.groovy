package edu.berkeley.groovy

interface ScriptRunner {
    public Object runScript(String className) throws ScriptRunnerException
    public Statistics getStatistics()
}
