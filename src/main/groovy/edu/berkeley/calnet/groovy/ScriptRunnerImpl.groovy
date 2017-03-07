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

import grails.core.GrailsApplication
import groovy.transform.Synchronized
import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
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

    // If false, then we will use a new ScriptClassLoader for each
    // invocation of runScript().  No caching of scripts will occur between
    // invocations of runScript().  If true, we will reuse the same
    // ScriptClassLoader for each invocation of runScript() (for this
    // instance of the ScriptRunner), and the ScriptClassLoader will not
    // recompile scripts that have not been changed.  Howeverm the
    // ScriptClassLoader will still recompile a script if has changed on the
    // filesystem.
    boolean cacheUnmodifiedScripts

    // auto-wired if this object is instantiated from Spring within Grails
    // (not mandatory)
    @Autowired
    GrailsApplication grailsApplication

    // internally managed
    private Statistics statistics
    private boolean isDebugEnabled
    private volatile ScriptClassLoader classLoaderInstance
    private ScriptFileMonitorThread scriptFileMonitorThread

    /**
     * The script will run using default Bootstrap code and with caching
     * of script classes without the script monitor thread running.
     */
    ScriptRunnerImpl(File scriptDirectory) {
        this(null, scriptDirectory, true, false)
    }

    /**
     * The script will run using default Bootstrap code and with caching
     * of script classes without the script monitor thread running.
     */
    ScriptRunnerImpl(String scriptDirectory) {
        this(null, new File(scriptDirectory), true, false)
    }

    /**
     * The script will run using default Bootstrap code and, if
     * cacheUnmodifiedScripts is true, will cache classes from script files
     * that have not been modified.  If launchMonitorThread is true, the
     * script file monitor thread will be launched with the
     * checkIntervalSeconds.
     */
    ScriptRunnerImpl(File scriptDirectory, boolean cacheUnmodifiedScripts, boolean launchMonitorThread = false, Integer checkIntervalSeconds = null) {
        this(null, scriptDirectory, cacheUnmodifiedScripts, launchMonitorThread, checkIntervalSeconds)
    }

    /**
     * The script will run using default Bootstrap code and, if
     * cacheUnmodifiedScripts is true, will cache classes from script files
     * that have not been modified.  If launchMonitorThread is true, the
     * script file monitor thread will be launched with the
     * checkIntervalSeconds.
     */
    ScriptRunnerImpl(String scriptDirectory, boolean cacheUnmodifiedScripts, boolean launchMonitorThread = false, Integer checkIntervalSeconds = null) {
        this(null, new File(scriptDirectory), cacheUnmodifiedScripts, launchMonitorThread, checkIntervalSeconds)
    }

    /**
     * @param bootstrapScriptFile The File location of the bootstrap script. 
     *        (This is not the Grails BootStrap script.) If this is null, a
     *        default Bootstrap script will be used.
     * @param scriptDirectory The classpath root directory where the scripts
     *        are contained.
     * @param cacheUnmodifiedScripts Cache the classes of scripts that have
     *        not been modified.
     * @param launchMonitorThread If true, launch the script file monitor
     *        thread.
     * @param checkIntervalSeconds Check interval for the script file
     *        monitor thread.
     */
    ScriptRunnerImpl(File bootstrapScriptFile, File scriptDirectory, boolean cacheUnmodifiedScripts, boolean launchMonitorThread, Integer checkIntervalSeconds = null) {
        this.bootstrapScriptFile = bootstrapScriptFile
        this.scriptDirectory = scriptDirectory
        this.statistics = new StatisticsImpl()
        this.cacheUnmodifiedScripts = cacheUnmodifiedScripts
        validateConstruction()
        if (launchMonitorThread) {
            launchScriptFileMonitorThread((checkIntervalSeconds != null ? checkIntervalSeconds : 30))
        }
    }

    ScriptRunnerImpl(Map map) {
        if (map.containsKey("scriptDirectory"))
            this.scriptDirectory = (map.scriptDirectory instanceof File ? map.scriptDirectory : new File(map.scriptDirectory))
        if (map.containsKey("bootstrapScriptFile"))
            this.bootstrapScriptFile = map.bootstrapScriptFile
        if (map.containsKey("parentClassLoader"))
            this.parentClassLoader = map.parentClassLoader
        if (map.containsKey("grailsApplication"))
            this.grailsApplication = map.grailsApplication
        if (map.containsKey("cacheUnmodifiedScripts"))
            this.cacheUnmodifiedScripts = map.cacheUnmodifiedScripts
        else
            this.cacheUnmodifiedScripts = true // default behavior is to cache
        validateConstruction()
        if (map.containsKey("launchMonitorThread") && map.launchMonitorThread) {
            Integer checkIntervalSeconds
            if (map.containsKey("checkIntervalSeconds"))
                checkIntervalSeconds = map.checkIntervalSeconds
            else
                checkIntervalSeconds = 30 // 30 second default
            launchScriptFileMonitorThread(checkIntervalSeconds)
        }
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
        checkDebuggingEnabled()
    }

    @Synchronized
    protected ScriptClassLoader getClassLoaderInstance() throws ScriptRunnerException {
        if (!cacheUnmodifiedScripts) {
            // non-caching mode - don't reuse ScriptClassLoaders

            // use a parentClassLoader if it's set
            ScriptClassLoader scl = (parentClassLoader != null ? new ScriptClassLoader(parentClassLoader, statistics, false) : new ScriptClassLoader(statistics, false))
            // add our scriptDirectory to class path of our ScriptClassLoader
            scl.addURL(scriptDirectory.toURI().toURL())
            return scl
        } else {
            // caching mode -- reuse ScriptClassLoader

            if (classLoaderInstance == null) {
                // use a parentClassLoader if it's set
                classLoaderInstance = (parentClassLoader != null ? new ScriptClassLoader(parentClassLoader, statistics, true) : new ScriptClassLoader(statistics, true))
                // add our scriptDirectory to class path of our ScriptClassLoader
                classLoaderInstance.addURL(scriptDirectory.toURI().toURL())
            }
            return classLoaderInstance
        }
    }

    /**
     * "Reload" the class loader by instantiating a new one and discarding
     * the old one.  This has the effect of reloading all the scripts.  It
     * is analagous to how Tomcat might reload a web application.  Only
     * relevant in caching mode.
     */
    @Synchronized
    void reloadClassLoader() {
        classLoaderInstance = null
    }

    /**
     * Run an external script in the scriptDirectory.
     *
     * @param className Is the class name of the script in the
     *        scriptDirectory.  Example: If you have
     *        scriptDirectory/myscript.groovy, then you would pass in
     *        "myscript" as the class name.
     */
    Object runScript(String className, Map<String, Object> propertyInjections = null) throws ScriptRunnerException {
        // instantiate a new ScriptClassLoader for this script
        ScriptClassLoader scl = getClassLoaderInstance()

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
            String defaultBootstrapScriptCode = """
              try {
                if(!getClass().classLoader.parent.parent.isScriptClassLoader())
                  throw new RuntimeException("Not a ScriptClassLoader")
              } catch(Exception e) {
                throw new RuntimeException("Not a ScriptClassLoader")
              }
              getClass().classLoader.parent.parent.loadClass(scriptClassName).newInstance()
            """
            bootstrapSource = new GroovyCodeSource(defaultBootstrapScriptCode, "Bootstrap", scriptDirectory.toURI().toString())
            bootstrapSource.setCachable(false)
        }

        // Run the Bootstrap script, which will return an instance of
        // the actual script class
        GroovyObject scriptInstance
        try {
            scriptInstance = (GroovyObject) shell.run(bootstrapSource, [] as String[])
        } catch (Exception e) {
            throw new ScriptRunnerException(e)
        }

        // Inject objects into our scriptInstance object
        if (grailsApplication && grailsApplication.hasProperty("grailsApplication")) {
            scriptInstance.grailsApplication = grailsApplication
        }

        // Inject a log instance for the script
        if (scriptInstance.hasProperty("log")) {
            scriptInstance.log = LoggerFactory.getLogger(scriptInstance.getClass())
        }

        // Inject passed-in properties
        propertyInjections?.each { Map.Entry<String, Object> entry ->
            if (scriptInstance.hasProperty(entry.key)) {
                scriptInstance."${entry.key}" = entry.value
            } else {
                log.warn("An injection was requested for property ${entry.key} but external script class ${scriptInstance.getClass().name} does not have this property")
            }
        }

        // Run the script instance.  If the script has no class defined in
        // it, then Groovy will implicitly create a Runnable class around
        // the script code.  If the script is a class, that class should
        // provide a run() method, which we execute here.
        def result
        try {
            result = scriptInstance.run()
        } catch (Exception e) {
            throw new ScriptRunnerException(e)
        }

        // Don't want to hang on to any scl class references with the result
        // object.  That's why we turn it to a String.
        def resultString = (result != null ? result.toString() : null)

        // Null out the injected properties.
        if (grailsApplication && scriptInstance.hasProperty("grailsApplication")) {
            scriptInstance.setProperty("grailsApplication", null)
        }
        if (scriptInstance.hasProperty("log")) {
            scriptInstance.setProperty("log", null)
        }
        propertyInjections?.each { Map.Entry<String, Object> entry ->
            if (scriptInstance.hasProperty(entry.key)) {
                scriptInstance.setProperty(entry.key, null)
            }
        }

        // Unload any of our loaded classes from the Groovy meta class
        // repository.
        for (Class<?> c : scl.getLoadedClasses()) {
            GroovySystem.getMetaClassRegistry().removeMetaClass(c)
        }

        return resultString
    }

    Statistics getStatistics() {
        return statistics
    }

    private void checkDebuggingEnabled() {
        try {
            isDebugEnabled = log.isDebugEnabled()
        }
        catch (Exception e) {
            isDebugEnabled = false
        }
    }

    /**
     * Launches a daemon thread that monitors the script directory for
     * script file changes and if there are any changes will invoke
     * reloadClassLoader() to clear the class cache.  The thread will look
     * for a changes at a set interval.
     *
     * @param checkIntervalSeconds The number of seconds to wait before
     *        checking again.  Set this too high and you won't detect
     *        changes in the time you'd like.  Set this too low, and you'll
     *        hurt performance, as the thread will be constantly scanning
     *        the filesystem for changes.
     */
    void launchScriptFileMonitorThread(int checkIntervalSeconds) throws ScriptRunnerException {
        if (isScriptFileMonitorThreadAlive()) {
            throw new ScriptRunnerException("scriptFileMonitorThread is already running")
        } else {
            scriptFileMonitorThread = new ScriptFileMonitorThread(this, checkIntervalSeconds)
            scriptFileMonitorThread.start()
        }
    }

    /**
     * Stop the script monitor thread.
     */
    void stopScriptFileMonitorThread() {
        if (isScriptFileMonitorThreadAlive()) {
            scriptFileMonitorThread.doStop = true
            scriptFileMonitorThread.interrupt()
        }
    }

    /**
     * @return true if script monitor thread is running
     */
    boolean isScriptFileMonitorThreadAlive() {
        return scriptFileMonitorThread != null && scriptFileMonitorThread.isAlive()
    }

    /**
     * A thread that monitors for script file modifications in the script
     * directory.  It scans at a specified interval.
     */
    @Slf4j
    private static class ScriptFileMonitorThread extends Thread {
        ScriptRunnerImpl scriptRunner
        int checkIntervalSeconds
        volatile boolean doStop

        ScriptFileMonitorThread(ScriptRunnerImpl scriptRunner, int checkIntervalSeconds) {
            setDaemon(true)
            this.scriptRunner = scriptRunner
            this.checkIntervalSeconds = checkIntervalSeconds
        }

        // Stores the last modified time for script files
        Map<File, Long> lastModifiedMap = [:]

        @Override
        public void run() {
            try {
                log.info("Launched ScriptFileMonitorThread with checkIntervalSeconds=${checkIntervalSeconds} monitoring directory ${scriptRunner.scriptDirectory.canonicalPath}")
                while (!doStop) {
                    Map<File, Boolean> visitMap = [:]
                    checkDirectory(visitMap, scriptRunner.scriptDirectory)

                    // check to see if any scripts were deleted
                    def toRemove = []
                    for (File file in lastModifiedMap.keySet()) {
                        if (!visitMap.containsKey(file)) {
                            log.info("Detected deletion of file ${file}, instantiating new class loader")
                            toRemove.add(file)
                        }
                    }

                    if (toRemove.size() > 0) {
                        for (File remove in toRemove) {
                            if (lastModifiedMap.remove(remove) == null)
                                log.warn("Unable to remove ${remove} from lastModifiedMap")
                        }
                        scriptRunner.reloadClassLoader()
                    }

                    sleep(checkIntervalSeconds * 1000)
                }
                log.info("ScriptFileMonitor thread is exiting")
            }
            catch (
                    InterruptedException e
                    ) {
                log.error("ScriptFileMonitor has been interrupted and is exiting")
            }
        }

        /**
         * Checks the directory for changed script files and reloads the
         * class loader if there are any.
         */
        void checkDirectory(Map<File, Boolean> visitMap, File directory) {
            for (File file in directory.listFiles()) {
                if (file.isDirectory()) {
                    checkDirectory(visitMap, file)
                } else if (file.isFile() && file.getName().endsWith(".groovy")) {
                    visitMap[file] = file
                    Long previousModification = lastModifiedMap[file]
                    if (previousModification == null) {
                        lastModifiedMap[file] = file.lastModified()
                    } else if (previousModification != null && previousModification != file.lastModified()) {
                        log.info("Detected changed for file ${file}, instantiating new class loader")
                        scriptRunner.reloadClassLoader()
                        lastModifiedMap[file] = file.lastModified()
                    }
                }
            }
        }
    }
}
