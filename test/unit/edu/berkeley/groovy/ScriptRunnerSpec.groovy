package edu.berkeley.groovy

import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class ScriptRunnerSpec extends Specification {

    File scriptDirectory = new File("external-scripts/running")
    File badScriptDirectory = new File("external-scripts/bad-scripts")

    def setup() {
    }

    def cleanup() {
    }

    void "test testScript run"() {
        given:
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(scriptDirectory)
        when:
            Object result = scriptRunner.runScript("testScript")
        then:
            result == "hello world"
    }

    void "test testRunnableClass run"() {
        given:
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(scriptDirectory)
        when:
            Object result = scriptRunner.runScript("testRunnableClass")
        then:
            result == "hello world"
    }

    void "test script in a package"() {
        given:
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(scriptDirectory)
        when:
            Object result = scriptRunner.runScript("mypackage.testPackageScript")
        then:
            result == "this class running in mypackage"
    }

    void "test script in a package using a noncanonical path script directory"() {
        given:
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(new File(scriptDirectory, "../running"))
        when:
            Object result = scriptRunner.runScript("mypackage.testPackageScript")
        then:
            result == "this class running in mypackage"
    }

    void "test caller injected property"() {
        given:
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(scriptDirectory)
        when:
            Object result = scriptRunner.runScript("testInjectedProperty", [testProperty: "my test property"])
        then:
            result == "my test property"
    }

    void "test existence of injected log instance"() {
        given:
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(scriptDirectory)
        when:
            Object result = scriptRunner.runScript("testLogInstance")
        then:
            result != null
    }

    void "test script with a package name mismatched to its directory"() {
        given:
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(badScriptDirectory)
        when:
            Object result = scriptRunner.runScript("mypackage.badPackageScript")
        then:
            Exception e = thrown()
            e instanceof ScriptClassLoaderException
    }

    void "test script with a missing package name"() {
        given:
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(badScriptDirectory)
        when:
            Object result = scriptRunner.runScript("mypackage.missingPackageScript")
        then:
            Exception e = thrown()
            e instanceof ScriptClassLoaderException
    }

    private void writeSource(File sourceFile, String source) throws IOException {
        FileWriter writer = new FileWriter(sourceFile)
        try {
            writer.write(source, 0, source.length())
        }
        finally {
            writer.close()
        }
    }

    void "test recompiling changed source file, without caching"() {
        given:
            File sourceFile = File.createTempFile("ScriptRunnerSpec", ".groovy")
            String scriptName = sourceFile.getName().replace(".groovy", "")
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(sourceFile.parentFile, false) // no script caching
        when:
            // create a temporary source file
            writeSource(sourceFile, "\"run number 1\"")
            // run the script
            assert scriptRunner.runScript(scriptName) == "run number 1"
            // modify the script and run it again
            writeSource(sourceFile, "\"run number 2\"")
            Object result = scriptRunner.runScript(scriptName)
            println("result = $result")
        then:
            result == "run number 2"
            scriptRunner.statistics.totalCompilationCount == 2
            scriptRunner.statistics.compiledCount == 1 && scriptRunner.statistics.recompiledCount == 1
            scriptRunner.statistics.getTotalCompiledCountForClass(scriptName) == 2
    }

    void "test unchanged source file, without caching"() {
        given:
            File sourceFile = File.createTempFile("ScriptRunnerSpec", ".groovy")
            String scriptName = sourceFile.getName().replace(".groovy", "")
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(sourceFile.parentFile, false) // no script caching
        when:
            // create a temporary source file
            writeSource(sourceFile, "\"run number 1\"")
            // run the script
            assert scriptRunner.runScript(scriptName) == "run number 1"
            // run again
            Object result = scriptRunner.runScript(scriptName)
            println("result = $result")
        then:
            result == "run number 1"
            // unchanged, but caching is disabled, so recompiled twice
            scriptRunner.statistics.totalCompilationCount == 2
            scriptRunner.statistics.compiledCount == 1 && scriptRunner.statistics.recompiledCount == 1
            scriptRunner.statistics.getTotalCompiledCountForClass(scriptName) == 2
    }

    void "test recompiling unchanged source file, with caching enabled"() {
        given:
            File sourceFile = File.createTempFile("ScriptRunnerSpec", ".groovy")
            String scriptName = sourceFile.getName().replace(".groovy", "")
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(sourceFile.parentFile, true) // script caching
        when:
            // create a temporary source file
            writeSource(sourceFile, "\"run number 1\"")
            // run the script
            assert scriptRunner.runScript(scriptName) == "run number 1"
            println("running second round")
            Object result = scriptRunner.runScript(scriptName)
            println("result = $result")
        then:
            result == "run number 1"
            // only one compilation because the second run was unchanged
            // and cached
            scriptRunner.statistics.totalCompilationCount == 1
            scriptRunner.statistics.compiledCount == 1 && scriptRunner.statistics.recompiledCount == 0
            scriptRunner.statistics.getTotalCompiledCountForClass(scriptName) == 1
    }

    void "test recompiling changed source file, with caching enabled"() {
        given:
            File sourceFile = File.createTempFile("ScriptRunnerSpec", ".groovy")
            String scriptName = sourceFile.getName().replace(".groovy", "")
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(sourceFile.parentFile, true) // script caching
        when:
            // create a temporary source file
            writeSource(sourceFile, "\"run number 1\"")
            long firstRunLastModified = sourceFile.lastModified()
            // run the script
            assert scriptRunner.runScript(scriptName) == "run number 1"
            // modify the script and run it again
            sleep(1500) // file lastMod may only be recorded at second precision
            writeSource(sourceFile, "\"run number 2\"")
            println("running second round")
            long secondRunLastModified = sourceFile.lastModified()
            assert secondRunLastModified > firstRunLastModified
            Object result = scriptRunner.runScript(scriptName)
            println("result = $result")
        then:
            result == "run number 2"
            scriptRunner.statistics.totalCompilationCount == 2
            // compiled once, then recompiled after the change
            scriptRunner.statistics.compiledCount == 1 && scriptRunner.statistics.recompiledCount == 1
            scriptRunner.statistics.getTotalCompiledCountForClass(scriptName) == 2
    }
}
