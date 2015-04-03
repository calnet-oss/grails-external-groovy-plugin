package edu.berkeley.groovy

import org.springframework.beans.factory.annotation.Autowired

class ScriptRunnerImpl implements ScriptRunner {

    // mandatory location of scripts
    File scriptDirectory

    // optional location of a Bootstrap script that overrides the default
    // Bootstrap behavior
    File bootstrapScriptFile

    // This is the optional class loader that will be set as the parent for
    // the ScriptClassLoader.  The default behavior in the constructors is
    // to set this to the class loader of this class, so if no parent is
    // desired, this must be explicitly set to null.
    ClassLoader parentClassLoader = getClass().getClassLoader()

    // auto-wired if this object is instantiated from Spring within Grails
    // (not mandatory)
    @Autowired
    def grailsApplication

    // internally managed
    private Statistics statistics

    /**
     * The script will run using default Bootstrap code.
     */
    ScriptRunnerImpl(File scriptDirectory) {
        this(null, scriptDirectory)
    }

    /**
     * @param bootstrapScriptFile The File location of the bootstrap script. 
     *        (This is not the Grails BootStrap script.) If this is null, a
     *        default Bootstrap script will be used.
     * @param scriptDirectory The classpath root directory where the scripts
     *        are contained.
     */
    ScriptRunnerImpl(File bootstrapScriptFile, File scriptDirectory) {
        this.bootstrapScriptFile = bootstrapScriptFile
        this.scriptDirectory = scriptDirectory
        this.statistics = new StatisticsImpl()
        validateConstruction()
    }

    ScriptRunnerImpl(Map map) {
        if (map.containsKey("scriptDirectory"))
            this.scriptDirectory = map.scriptDirectory
        if (map.containsKey("bootstrapScriptFile"))
            this.bootstrapScriptFile = map.bootstrapScriptFile
        if (map.containsKey("parentClassLoader"))
            parentClassLoader = map.parentClassLoader
        if (map.containsKey("grailsApplication"))
            grailsApplication = map.grailsApplication
        validateConstruction()
    }

    ScriptRunnerImpl() {
        validateConstruction()
    }

    private void validateConstruction() {
        if (bootstrapScriptFile != null && (!bootstrapScriptFile.exists() || !bootstrapScriptFile.isFile()))
            throw new RuntimeException("${bootstrapScriptFile.absolutePath} does not exist or is not a file")
        if (scriptDirectory == null)
            throw new IllegalArgumentException("The scriptDirectory must be passed to the constructor")
        if (!scriptDirectory.exists() || !scriptDirectory.isDirectory())
            throw new RuntimeException("${scriptDirectory.absolutePath} does not exist or is not a directory")
    }

    protected ScriptClassLoader newClassLoaderInstance() throws ScriptRunnerException {
        // utilize the grails class loader as the parent to our
        // ScriptClassLoader
        ScriptClassLoader scl = (parentClassLoader != null ? new ScriptClassLoader(parentClassLoader, statistics) : new ScriptClassLoader(statistics))
        // add our scriptDirectory to class path of our ScriptClassLoader
        scl.addURL(scriptDirectory.toURI().toURL())
        return scl
    }

    /**
     * Run an external script in the scriptDirectory.
     *
     * @param className Is the class name of the script in the
     *        scriptDirectory.  Example: If you have
     *        scriptDirectory/myscript.groovy, then you would pass in
     *        "myscript" as the class name.
     */
    public Object runScript(String className) throws ScriptRunnerException {
        // instantiate a new ScriptClassLoader for this script
        ScriptClassLoader scl = newClassLoaderInstance()

        /**
         * Execute the Bootstrap script which should load the script class
         * and return an instance of that script class.
         */

        // A GroovyShell that will provide our script execution environment. 
        // It utilizes our ScriptClassLoader.
        GroovyShell shell = new GroovyShell(scl)

        // inject the script class name for the Bootstrap script
        shell.setProperty("scriptClassName", className)

        GroovyCodeSource bootstrapSource
        if (bootstrapScriptFile != null) {
            // load the Bootstrap source
            bootstrapSource = new GroovyCodeSource(bootstrapScriptFile)
            // don't let Groovy cache it
            bootstrapSource.setCachable(false)
        } else {
            //
            // Provide a default Bootstrap script since none was specified.
            //
            // It utilizes the ScriptClassLoader to load the class from the
            // scriptDirectory using the scriptClassName, which we injected
            // above with our shell.setProperty() call
            //
            String defaultBootstrapScriptCode = """getClass().getClassLoader().loadClass(scriptClassName).newInstance()"""
            bootstrapSource = new GroovyCodeSource(defaultBootstrapScriptCode, "Bootstrap", scriptDirectory.toURI().toString())
            bootstrapSource.setCachable(false)
        }

        // Run the Bootstrap script, which will return an instance of
        // the actual script class
        Object scriptInstance = shell.run(bootstrapSource, [] as String[])

        // Inject objects into our scriptInstance object using metaClass
        if (grailsApplication) {
            scriptInstance.metaClass.setProperty("grailsApplication", grailsApplication)
        }

        // Run the script instance.  If the script has no class defined in
        // it, then Groovy will implicitly create a Runnable class around
        // the script code.  If the script is a class, that class should
        // provide a run() method, which we execute here.
        def result = scriptInstance.run()

        // Don't want to hang on to any scl class references with the result
        // object.  That's why we turn it to a String.
        def resultString = (result != null ? result.toString() : null)

        // Unload any of our loaded classes from the Groovy meta class
        // repository.
        for (Class<?> c : scl.getLoadedClasses()) {
            GroovySystem.getMetaClassRegistry().removeMetaClass(c)
        }

        return resultString
    }
}
