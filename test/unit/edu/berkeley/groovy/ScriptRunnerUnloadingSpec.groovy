package edu.berkeley.groovy

import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class ScriptRunnerUnloadingSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }

    void "test script unloading"() {
        given:
            ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(new File("external-scripts/unloading"), false)
        when:
            /**
             * See the comments in ScriptClassLoaderSpec.groovy as we are
             * utilizing the same garbage collection test approach there as we
             * are there.  It's described in detail there.
             */
            int i
            for (i = 0; i < 10000; i++) {
                scriptRunner.runScript("test") // run test.groovy script
                //if(i % 1000 == 0)
                //  System.out.println(scriptRunner.statistics.getLoadedCount() + ", " + scriptRunner.statistics.getUnloadedCount())
                if (scriptRunner.statistics.unloadedCount > 0)
                    break
                if (i % 1000 == 0) {
                    //System.out.println("GCing")
                    System.gc()
                }
            }
            println("took $i iterations, ${scriptRunner.statistics.loadedCount} loaded, ${scriptRunner.statistics.unloadedCount} unloaded")
        then:
            scriptRunner.statistics.unloadedCount > 0
    }
}
