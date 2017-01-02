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
import groovy.util.logging.Slf4j
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Specification

import java.nio.ByteBuffer

@TestMixin(GrailsUnitTestMixin)
@Slf4j(value = "LOG")
class ScriptRunnerUnloadingSpec extends Specification {

    void "test script unloading"() {
        given:
        ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(new File("external-scripts/unloading"), false)
        when:
        /**
         * See the comments in ScriptClassLoaderSpec.groovy as we are
         * utilizing the same garbage collection test approach there as we
         * are there.  It's described in detail there.
         */
        def allocations = []
        int i
        for (i = 0; i < 1000; i++) {
            scriptRunner.runScript("test") // run test.groovy script
            try {
                // speed GC along by creating big allocations
                allocations.add(ByteBuffer.allocate(1000000 * 100))
            }
            catch (OutOfMemoryError e) {
                allocations = null
                System.gc()
                LOG.info("Cleaned up after an expected and wanted out of memory error.")
                break
            }
            //if(i % 100 == 0)
            //  System.out.println(scriptRunner.statistics.loaderInstantiationCount + ", " + scriptRunner.statistics.loaderFinalizationCount)
            if (scriptRunner.statistics.loaderFinalizationCount > 0)
                break
        }
        LOG.info("took $i iterations, ${scriptRunner.statistics.loaderInstantiationCount} loaded, ${scriptRunner.statistics.loaderFinalizationCount} unloaded")
        then:
        scriptRunner.statistics.loaderFinalizationCount > 0
    }
}
