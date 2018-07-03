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
package edu.berkeley.calnet.groovy

import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification

import java.nio.ByteBuffer

@TestMixin(GrailsUnitTestMixin)
class ScriptClassLoaderSpec extends Specification {
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
        def allocations = []
        int i
        for (i = 0; i < 1000; i++) {
            new ScriptClassLoader(stats, false)
            try {
                // speed GC along by creating big allocations
                allocations.add(ByteBuffer.allocate(1000000 * 100))
            }
            catch (OutOfMemoryError ignored) {
                System.gc()
                break
            }
            //if(i % 100 == 0)
            //  System.out.println(stats.loaderInstantiationCount + ", " + stats.loaderFinalizationCount)
            if (stats.loaderFinalizationCount > 0)
                break
        }
        println("took $i iterations, ${stats.loaderInstantiationCount} loaded, ${stats.loaderFinalizationCount} unloaded")
        then:
        stats.loaderFinalizationCount > 0
    }
}
