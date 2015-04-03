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
}
