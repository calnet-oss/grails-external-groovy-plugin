package edu.berkeley.groovy

import groovy.util.logging.Log4j
import org.codehaus.groovy.control.CompilationFailedException

/**
 * A Groovy class loader that extends GroovyClassLoader to record in a
 * shared Statistics object when a ScriptClassLoader is loaded and
 * finalized.  The idea is each script gets its own class loader, and when
 * that script is modified, it will get a new class loader and the old one
 * should be garbage collected.  The purpose of recording the loads and
 * unloads is to detect class loader garbage collection problems (to detect
 * a "class loader leak.")
 */
@Log4j
class ScriptClassLoader extends GroovyClassLoader {

    private Statistics stats
    boolean recompileOnScriptChanges
    boolean isDebugEnabled

    // used for debugging
    private def config = org.codehaus.groovy.control.CompilerConfiguration.DEFAULT

    ScriptClassLoader() {
        super()
        validateConstruction()
    }

    ScriptClassLoader(Map map) {
        super()
        this.stats = map['stats']
        this.recompileOnScriptChanges = map['recompileOnScriptChanges']
        setShouldRecompile(recompileOnScriptChanges)
        validateConstruction()
    }

    /**
     * @param stats Statistics object that tracks loading, unloading and
     *        compilation counts.
     * @param recompileOnScriptChanges If true, recompile a script if the
     *        file changes.
     */
    ScriptClassLoader(Statistics stats, boolean recompileOnScriptChanges) {
        super()
        this.stats = stats
        this.recompileOnScriptChanges = recompileOnScriptChanges
        setShouldRecompile(recompileOnScriptChanges)
        validateConstruction()
    }

    ScriptClassLoader(ClassLoader parent, Statistics stats, boolean recompileOnScriptChanges) {
        super(parent)
        this.stats = stats
        this.recompileOnScriptChanges = recompileOnScriptChanges
        setShouldRecompile(recompileOnScriptChanges)
        validateConstruction()
    }

    private void validateConstruction() {
        if (stats == null) {
            throw new ScriptClassLoaderException("Must pass a Statistics object into the constructor")
        }
        stats.signalClassLoaderLoad() // increment the load count
        checkDebuggingEnabled()
    }

    public boolean isScriptClassLoader() {
        return true
    }

    @Override
    public void finalize() {
        // Log exceptions.  The finalizer runs in a separate thread.
        try {
            // increment the unload count
            stats.signalClassLoaderUnload()
        }
        catch (Exception e) {
            try {
                log.error("Exception during finalization", e)
            }
            catch (Exception logException) {
                e.printStackTrace()
            }
        }
    }

    @Override
    public Class parseClass(GroovyCodeSource codeSource, boolean shouldCacheSource) throws CompilationFailedException {
        if (isDebugEnabled)
            log.debug("parseClass() for ${codeSource.name}, isCachable=${codeSource.isCachable()}: shouldCacheSource=$shouldCacheSource")
        Class cls = super.parseClass(codeSource, shouldCacheSource)
        // verify this script file is in the right directory for the package
        // (a runtime exception will be thrown if it isn't)
        checkPackageName(codeSource, cls)
        // increment the compile count
        stats.signalCompiled(cls.name)
        return cls
    }

    @Override
    protected boolean isRecompilable(Class cls) {
        boolean result = super.isRecompilable(cls)
        if (isDebugEnabled && cls != null) {
            // show the elements that go into the decision from the
            // GroovyClassLoader source
            log.debug("isRecompilable() for ${cls ? cls.name : 'null'} returning $result")
            log.debug("  isShouldRecompile() = " + isShouldRecompile())
            log.debug("  config.getRecompileGroovySource() = " + config.getRecompileGroovySource())
            log.debug("  (if true then not recompilable) a: " + (cls.getClassLoader() == this))
            log.debug("  (if true then not recompilable) b: " + (isShouldRecompile() == null && !config.getRecompileGroovySource()))
            log.debug("  (if true then not recompilable) c: " + (isShouldRecompile() != null && !isShouldRecompile()))
            log.debug("  (if true then not recompilable) d: " + (!GroovyObject.class.isAssignableFrom(cls)))
            log.debug("  (if true then not recompilable) e: " + (getTimeStamp(cls) == Long.MAX_VALUE))
        }
        return result
    }

    @Override
    protected boolean isSourceNewer(URL source, Class cls) throws IOException {
        boolean result = super.isSourceNewer(source, cls)
        if (isDebugEnabled) {
            // show the elements that go into the decision from the
            // GroovyClassLoader source
            log.debug("isSourceNewer() for source=$source, cls=${cls ? cls.name : 'null'} returning $result")
            log.debug("  isFile(source) = " + isFile(source))
            long fileLastModified = new File(source.getPath().replace("/", new String(File.separatorChar)).replace("|", ":")).lastModified()
            log.debug("  file last modified = " + fileLastModified)
            long classTimestamp = getTimeStamp(cls)
            log.debug("  existing class timestamp = " + classTimestamp)
            long difference = fileLastModified - classTimestamp
            log.debug("  time difference = " + difference)
            int minimumInterval = config.getMinimumRecompilationInterval()
            log.debug("  minimumRecompilationInterval = " + minimumInterval)
            log.debug("  existing class timestamp + minimumRecompilationInterval < file last modified? : " + (classTimestamp + minimumInterval < fileLastModified))
        }
        return result
    }

    @Override
    protected Class loadClass(final String name, boolean resolve) throws ClassNotFoundException {
        if (isDebugEnabled)
            log.debug("loadClass(): name=$name, resolve=$resolve, recompileOnScriptChanges=$recompileOnScriptChanges")
        if (recompileOnScriptChanges) {
            // We want preferClassOverScript to be false so that it checks
            // the timestamp on the file for changes.
            return loadClass(name, true, false, resolve)
        } else {
            // preferClassOverScript true
            return loadClass(name, true, true, resolve)
        }
    }

    @Override
    public Class loadClass(final String name, boolean lookupScriptFiles, boolean preferClassOverScript, boolean resolve) {
        if (isDebugEnabled) {
            log.debug("loadClass(): name=$name, lookupScriptFiles=$lookupScriptFiles, preferClassOverScript=$preferClassOverScript, resolve=$resolve")
            log.debug("  cacheEntry=" + getClassCacheEntry(name) + ", classCache=" + System.identityHashCode(classCache) + ", this=" + this)
        }
        Class result = super.loadClass(name, lookupScriptFiles, preferClassOverScript, resolve)
        if (isDebugEnabled) {
            log.debug("  After Load")
            log.debug("    cacheEntry=" + getClassCacheEntry(name) + ", classCache=" + System.identityHashCode(classCache) + ", this=" + this)
        }
        return result
    }

    public void enableDebugging() {
        log.setLevel(org.apache.log4j.Level.DEBUG)
        checkDebuggingEnabled()
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
     * Verify the class package name matches up with the directory of the script.
     */
    protected void checkPackageName(GroovyCodeSource codeSource, Class cls) {
        // can only check if this class came from a script file
        if (codeSource.file != null && codeSource.file.parentFile != null) {
            // get the classpath directory that contains this source fole
            File classpathLocation = getClasspathLocation(codeSource.file)
            if (classpathLocation == null)
                throw new RuntimeException("Couldn't get classpath location for ${codeSource.file}")

            // The expected directory is the classpath locaion plus the
            // package name with dots converted to a File.separator
            // character.
            File expectedDirectory = (cls.package != null ? new File(classpathLocation, cls.package.name.replace('.' as char, '/' as char)) : classpathLocation)

            // If the script file is in a location that doesn't match its
            // package, then throw an exception.
            if (codeSource.file.parentFile.canonicalPath != expectedDirectory.canonicalPath)
                throw new ScriptClassLoaderException("The package name in ${codeSource.file.canonicalPath}, which is \"${(cls.package != null ? cls.package.name : '')}\", needs to be placed in a directory matching the package name: i.e., ${expectedDirectory.canonicalPath}")
        }
    }

    /**
     * Get the class loader URL for the source file.
     */
    protected File getClasspathLocation(File file) {
        for (URL url in getURLs()) {
            URI uri = url.toURI()
            if (uri.getScheme() == "file") {
                File classpathLocation = new File(uri)
                if (file.canonicalPath.startsWith(classpathLocation.canonicalPath)) {
                    return classpathLocation
                }
            }
        }
        return null
    }
}
