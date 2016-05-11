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

import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class ScriptRunnerSpec extends Specification {

    File scriptDirectory = new File("external-scripts/running")
    File badScriptDirectory = new File("external-scripts/bad-scripts")
    File tmpDir = new File(System.getProperty("java.io.tmpdir") + "/scriptRunnerSpec")

    def setup() {
        tmpDir.mkdir()
        tmpDir.deleteOnExit()
    }

    def cleanup() {
        tmpDir.delete()
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
        e instanceof ScriptRunnerException
    }

    void "test script with a missing package name"() {
        given:
        ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(badScriptDirectory)
        when:
        Object result = scriptRunner.runScript("mypackage.missingPackageScript")
        then:
        Exception e = thrown()
        e instanceof ScriptRunnerException
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
        File sourceFile = File.createTempFile("ScriptRunnerSpec", ".groovy", tmpDir)
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
        scriptRunner.statistics.loaderInstantiationCount == 2
        scriptRunner.statistics.totalCompilationCount == 2
        scriptRunner.statistics.compiledCount == 1 && scriptRunner.statistics.recompiledCount == 1
        scriptRunner.statistics.getTotalCompiledCountForClass(scriptName) == 2
    }

    void "test unchanged source file, without caching"() {
        given:
        File sourceFile = File.createTempFile("ScriptRunnerSpec", ".groovy", tmpDir)
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
        scriptRunner.statistics.loaderInstantiationCount == 2
        // unchanged, but caching is disabled, so recompiled twice
        scriptRunner.statistics.totalCompilationCount == 2
        scriptRunner.statistics.compiledCount == 1 && scriptRunner.statistics.recompiledCount == 1
        scriptRunner.statistics.getTotalCompiledCountForClass(scriptName) == 2
    }

    void "test recompiling unchanged source file, with caching enabled"() {
        given:
        File sourceFile = File.createTempFile("ScriptRunnerSpec", ".groovy", tmpDir)
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
        scriptRunner.statistics.loaderInstantiationCount == 1
        scriptRunner.statistics.totalCompilationCount == 1
        scriptRunner.statistics.compiledCount == 1 && scriptRunner.statistics.recompiledCount == 0
        scriptRunner.statistics.getTotalCompiledCountForClass(scriptName) == 1
    }

    void "test recompiling unchanged source file after a cache clear, with caching enabled"() {
        given:
        File sourceFile = File.createTempFile("ScriptRunnerSpec", ".groovy", tmpDir)
        String scriptName = sourceFile.getName().replace(".groovy", "")
        ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(sourceFile.parentFile, true) // script caching
        when:
        // create a temporary source file
        writeSource(sourceFile, "\"run number 1\"")
        // run the script
        assert scriptRunner.runScript(scriptName) == "run number 1"
        // clear the class cache
        scriptRunner.reloadClassLoader()
        // run it again
        Object result = scriptRunner.runScript(scriptName)
        then:
        result == "run number 1"
        scriptRunner.statistics.loaderInstantiationCount == 2
        // should be two compilations because we cleared the class cache
        scriptRunner.statistics.totalCompilationCount == 2
        scriptRunner.statistics.compiledCount == 1 && scriptRunner.statistics.recompiledCount == 1
        scriptRunner.statistics.getTotalCompiledCountForClass(scriptName) == 2
    }

    void "test recompiling changed source file, with caching enabled"() {
        given:
        File sourceFile = File.createTempFile("ScriptRunnerSpec", ".groovy", tmpDir)
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
        scriptRunner.statistics.loaderInstantiationCount == 1
        scriptRunner.statistics.totalCompilationCount == 2
        // compiled once, then recompiled after the change
        scriptRunner.statistics.compiledCount == 1 && scriptRunner.statistics.recompiledCount == 1
        scriptRunner.statistics.getTotalCompiledCountForClass(scriptName) == 2
    }

    void "test recompiling changed source file via script monitor thread"() {
        given:
        File monitoredTmpDir = new File(tmpDir, "monitored")
        monitoredTmpDir.mkdir()
        File sourceFile = File.createTempFile("ScriptRunnerSpec", ".groovy", monitoredTmpDir)
        String scriptName = sourceFile.getName().replace(".groovy", "")
        ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(sourceFile.parentFile, true) // script caching
        // start the script monitor thread that will observe file changes at
        // 1s interval
        scriptRunner.launchScriptFileMonitorThread(1)
        when:
        // create a temporary source file
        writeSource(sourceFile, "\"run number 1\"")
        long firstRunLastModified = sourceFile.lastModified()
        // run the script for the first time
        assert scriptRunner.runScript(scriptName) == "run number 1"

        // modify the script
        sleep(1500) // file lastMod may only be recorded at second precision
        writeSource(sourceFile, "\"run number 2\"")
        long secondRunLastModified = sourceFile.lastModified()
        assert secondRunLastModified > firstRunLastModified

        // give the monitor thread some time to detect the change
        for (int i = 0; i < 20 && scriptRunner.classLoaderInstance != null; i++) {
            sleep(100)
        }

        // run the script again which should instantiate a new class loader
        Object result = scriptRunner.runScript(scriptName)

        // stop the monitor thread
        scriptRunner.stopScriptFileMonitorThread()
        // give it some time to actually stop
        for (int i = 0; i < 20 && scriptRunner.isScriptFileMonitorThreadAlive(); i++) {
            sleep(100)
        }
        then:
        result == "run number 2"
        // confirm the class loader has been instantiated twice
        // which confirms the monitor thread did its job
        scriptRunner.statistics.loaderInstantiationCount == 2
        // confirm the monitor thread has stopped
        scriptRunner.isScriptFileMonitorThreadAlive() == false
        scriptRunner.statistics.totalCompilationCount == 2
        // compiled once, then recompiled after the change
        scriptRunner.statistics.compiledCount == 1 && scriptRunner.statistics.recompiledCount == 1
        scriptRunner.statistics.getTotalCompiledCountForClass(scriptName) == 2
    }

    void "test detection of deleted script via script monitor thread"() {
        given:
        File monitoredTmpDir = new File(tmpDir, "monitored")
        monitoredTmpDir.mkdir()
        File sourceFile = File.createTempFile("ScriptRunnerSpec", ".groovy", monitoredTmpDir)
        String scriptName = sourceFile.getName().replace(".groovy", "")
        ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(sourceFile.parentFile, true) // script caching
        // start the script monitor thread that will observe file changes at
        // 1s interval
        scriptRunner.launchScriptFileMonitorThread(1)
        when:
        // create a temporary source file
        writeSource(sourceFile, "\"run number 1\"")
        long firstRunLastModified = sourceFile.lastModified()
        // run the script for the first time
        Object result = scriptRunner.runScript(scriptName)

        // delete the script
        assert sourceFile.delete()

        // give the monitor thread some time to detect the deletion
        for (int i = 0; i < 20 && scriptRunner.classLoaderInstance != null; i++) {
            sleep(100)
        }

        // stop the monitor thread
        scriptRunner.stopScriptFileMonitorThread()
        // give it some time to actually stop
        for (int i = 0; i < 20 && scriptRunner.isScriptFileMonitorThreadAlive(); i++) {
            sleep(100)
        }
        then:
        result == "run number 1"
        // confirm the class loader has been instantiated twice
        // which confirms the monitor thread did its job
        scriptRunner.statistics.loaderInstantiationCount == 2
        // confirm the monitor thread has stopped
        scriptRunner.isScriptFileMonitorThreadAlive() == false
    }
}
