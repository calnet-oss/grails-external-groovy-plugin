import edu.berkeley.groovy.ScriptRunnerImpl

class ExternalGroovyGrailsPlugin {
    def group = "edu.berkeley.calnet.plugins"

    // the plugin version
    def version = "0.2-SNAPSHOT"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.4 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/domain/**",
            "grails-app/controllers/**"
    ]

    // Any settings plugins should load before "dataSource".  By telling
    // this plugin to load after the dataSource plugin, then we are making
    // sure we load this plugin after any settings plugins.  We want
    // settings to load before this plugin so that we have certain settings,
    // like the script directory location, loaded before this plugin
    // initializes.
    def loadAfter = ["dataSource"]

    // TODO Fill in these fields
    def title = "External Groovy Plugin" // Headline display name of the plugin
    def author = "Brian Koehmstedt"
    def authorEmail = "bkoehmstedt@berkeley.edu"
    def description = '''\
Execute external Groovy scripts from Grails.
'''

    // URL to the plugin's documentation
    //def documentation = "http://grails.org/plugin/external-groovy"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
//    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
//    def organization = [ name: "My Company", url: "http://www.my-company.com/" ]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
//    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before
    }

    def doWithSpring = {
        /**
         * In config:
         *
         * If externalGroovy.defaultScriptDirectory set, use that
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
        def eg = (application.config?.externalGroovy ?: [:])
        log.debug("Instantiating scriptRunner with config: ${eg}")
        scriptRunner(
                ScriptRunnerImpl,
                new File(eg?.defaultScriptDirectory ?: "external-scripts"),
                eg?.cacheScripts != null ?: true,
                eg?.launchScriptFileMonitorThread as Boolean,
                (eg?.scriptFileMonitorThreadIntervalSeconds ?: 30) as Integer
        )
    }

    def doWithDynamicMethods = { ctx ->
        // TODO Implement registering dynamic methods to classes (optional)
    }

    def doWithApplicationContext = { ctx ->
        // TODO Implement post initialization spring config (optional)
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    def onShutdown = { event ->
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
