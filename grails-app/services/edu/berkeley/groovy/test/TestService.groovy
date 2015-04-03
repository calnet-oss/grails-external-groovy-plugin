package edu.berkeley.groovy.test

import edu.berkeley.groovy.ScriptRunner

class TestService {

    // use by testGrailsApplicationScript.groovy
    static final String GRAILS_APPLICATION_SET = "grailsApplication is set"
    static final String GRAILS_APPLICATION_NOT_SET = "grailsApplication is not set"

    ScriptRunner scriptRunner // injected

    String runScript(String scriptName) {
        return scriptRunner.runScript(scriptName)
    }
}
