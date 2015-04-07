package edu.berkeley.groovy.test

import grails.test.spock.IntegrationSpec

class TestServiceIntegrationSpec extends IntegrationSpec {

    def testService

    def setup() {
    }

    def cleanup() {
    }

    void "test runScript service and grailsApplication injection"() {
        when:
            String result = testService.runScript("testGrailsApplicationScript")
        then:
            result == TestService.GRAILS_APPLICATION_SET
    }
}
