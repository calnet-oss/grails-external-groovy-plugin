package edu.berkeley.groovy

import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class ScriptClassLoaderSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }

    void "test ScriptClassLoader unloading"() {
        given:
            StatisticsImpl stats = new StatisticsImpl()
        when:
            /**
             * The garbage collector is unpredictable, so our approach here is
             * to allocate a class loader, then hold no reference to it so it
             * should eventually get garbage collected, and do this in a loop up
             * to a maximum iteration count, until at least one class loader has
             * been finalized.  If at least one class loader has been finalized,
             * then we know that the class loader is unloading properly (and
             * that stats gathering is working).
             *
             * If the max iteration count is hit without any finalization
             * occuring, then something is likely broken and the class loader
             * isn't getting unloaded (or the stats gathering isn't working).
             *
             * The max iteration count is set to 10,000 which should be enough
             * to trigger at least one round of full garbage collection.
             *
             * Note that Hotspot JVM options could affect the behavior of
             * garbage collection.  Options that definitely affect this: garbage
             * collection selection and perm gen size.  A high perm gen size,
             * for example, could cause garbage collection of the perm gen area
             * to happen less frequently (although if you do this, hopefully it
             * still happens within 10,000 iterations).
             *
             * This test is meant to be run with the default JVM options as
             * provided by "grails test-app" and the JVM options that are
             * defined in BuildConfig.groovy in the grails.project.fork test
             * section.
             */
            int i
            for (i = 0; i < 10000; i++) {
                new ScriptClassLoader(stats, false)
                //if(i % 1000 == 0)
                //  System.out.println(stats.loaderInstantiationCount + ", " + stats.loaderFinalizationCount)
                if (stats.loaderFinalizationCount > 0)
                    break
                if (i % 1000 == 0) {
                    //System.out.println("GCing")
                    System.gc()
                }
            }
            println("took $i iterations, ${stats.loaderInstantiationCount} loaded, ${stats.loaderFinalizationCount} unloaded")
        then:
            stats.loaderFinalizationCount > 0
    }
}
