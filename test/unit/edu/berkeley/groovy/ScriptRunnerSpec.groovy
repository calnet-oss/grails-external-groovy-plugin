package edu.berkeley.groovy

import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class ScriptRunnerSpec extends Specification {

    File scriptDirectory = new File("external-scripts/running")

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
            scriptRunner.statistics.compiledCount == 2
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
            scriptRunner.statistics.compiledCount == 2
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
            scriptRunner.statistics.compiledCount == 1
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
            scriptRunner.statistics.compiledCount == 2
    }
}
