package edu.berkeley.groovy.test

class TestController {

    TestService testService // injected

    def index() {
        String result = testService.runScript("testScript")
        render result
    }
}
