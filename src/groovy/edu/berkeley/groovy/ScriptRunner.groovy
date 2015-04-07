package edu.berkeley.groovy

interface ScriptRunner {
    public Object runScript(String className, Map<String, Object> propertyInjections) throws ScriptRunnerException

    public Statistics getStatistics()
}
