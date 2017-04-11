package edu.berkeley.calnet.groovy
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
import grails.plugins.Plugin
import groovy.util.logging.Slf4j

@Slf4j('LOG')
class ExternalGroovyGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.0 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/init/**"
    ]

    // Any settings plugins should load before "dataSource".  By telling
    // this plugin to load after the dataSource plugin, then we are making
    // sure we load this plugin after any settings plugins.  We want
    // settings to load before this plugin so that we have certain settings,
    // like the script directory location, loaded before this plugin
    // initializes.
    def loadAfter = ["dataSource"]

    def title = "External Groovy Plugin"
    def author = "Brian Koehmstedt"
    def authorEmail = "bkoehmstedt@berkeley.edu"
    def description = '''\
Execute external Groovy scripts from Grails.
'''

    // URL to the plugin's documentation
    def documentation = "https://github.com/calnet-oss/grails-external-groovy-plugin/blob/master/README.md"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
    def license = "BSD"

    def organization = [name: "University of California, Berkeley", url: "http://www.berkeley.edu/"]

    // Any additional developers beyond the author specified above.
    def developers = [ [ name: "Soeren Glasius", email: "sbglasius@berkeley.edu" ]]

    // Location of the plugin's issue tracker.
    def issueManagement = [system: "GitHub", url: "https://github.com/calnet-oss/grails-external-groovy-plugin/issues"]

    // Online location of the plugin's browseable source code.
    def scm = [url: "https://github.com/calnet-oss/grails-external-groovy-plugin"]

    def doWithWebDescriptor = { xml ->
    }

    /**
     * In config:
     *
     * If system property externalGroovy.defaultScriptDirectory set, use that
     *    else If externalGroovy.defaultScriptDirectory set, use that
     *    otherwise default to ./external-scripts
     *
     * If externalGroovy.cacheScripts set, use that
     *    otherwise default to true
     *
     * If externalGroovy.launchScriptFileMonitorThread set, use that
     *    otherwise default to false
     *
     * If externalGroovy.scriptFileMonitorThreadIntervalSeconds, use that
     *    otherwise default to 30 seconds
     */
    Closure doWithSpring() {
        { ->
            def eg = (application.config?.externalGroovy ?: [:])
            def defaultScriptDirectory = (System.getProperty("externalGroovy.defaultScriptDirectory") ?: eg?.defaultScriptDirectory)
            LOG.debug("Instantiating scriptRunner with config: ${eg} and defaultScriptDirectory=${defaultScriptDirectory}")
            scriptRunner(
                    ScriptRunnerImpl,
                    new File(defaultScriptDirectory ?: "external-scripts"),
                    eg?.cacheScripts != null ?: true,
                    (eg?.launchScriptFileMonitorThread ? (boolean) eg?.launchScriptFileMonitorThread : false),
                    (eg?.scriptFileMonitorThreadIntervalSeconds ?: 30) as Integer
            )
        }
    }
}
