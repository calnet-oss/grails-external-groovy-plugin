package edu.berkeley.groovy

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
class ScriptClassLoader extends GroovyClassLoader {

    private Statistics stats
    boolean recompileOnScriptChanges

    ScriptClassLoader() {
        super()
        verifyConstruction()
    }

    ScriptClassLoader(Map map) {
        super()
        this.stats = map['stats']
        this.recompileOnScriptChanges = map['recompileOnScriptChanges']
        setShouldRecompile(recompileOnScriptChanges)
        verifyConstruction()
    }

    ScriptClassLoader(Statistics stats, boolean recompileOnScriptChanges) {
        super()
        this.stats = stats
        setShouldRecompile(recompileOnScriptChanges)
        verifyConstruction()
    }

    ScriptClassLoader(ClassLoader parent, Statistics stats, boolean recompileOnScriptChanges) {
        super(parent)
        this.stats = stats
        setShouldRecompile(recompileOnScriptChanges)
        verifyConstruction()
    }

    private void verifyConstruction() {
        if (stats == null) {
            throw new RuntimeException("Must pass a Statistics object into the constructor")
        }
        stats.signalClassLoaderLoad() // increment the load count
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

    /*
    @Override
    public Class parseClass(GroovyCodeSource codeSource, boolean shouldCacheSource) throws CompilationFailedException {
        //println("Running parseClass for ${codeSource.name}, isCachable=${codeSource.isCachable()}: shouldCacheSource=$shouldCacheSource")
        super.parseClass(codeSource, shouldCacheSource)
    }

    def config = org.codehaus.groovy.control.CompilerConfiguration.DEFAULT
    @Override
    protected boolean isRecompilable(Class cls) {
        boolean result = super.isRecompilable(cls)
        if(cls != null) {
          println("isRecompilable() for ${cls ? cls.name : 'null'} returning $result")
          println("  isShouldRecompile() = " + isShouldRecompile())
          println("  config.getRecompileGroovySource() = " + config.getRecompileGroovySource())
          println("  (if true then not recompilable) a: " + (cls.getClassLoader() == this))
          println("  (if true then not recompilable) b: " + (isShouldRecompile() == null && !config.getRecompileGroovySource()))
          println("  (if true then not recompilable) c: " + (isShouldRecompile() != null && !isShouldRecompile()))
          println("  (if true then not recompilable) d: " + (!GroovyObject.class.isAssignableFrom(cls)))
          println("  (if true then not recompilable) e: " + (getTimeStamp(cls) == Long.MAX_VALUE))
        }
        return result
    }

    @Override
    protected boolean isSourceNewer(URL source, Class cls) throws IOException {
        boolean result = super.isSourceNewer(source, cls)
        println("isSourceNewer() for source=$source, cls=${cls ? cls.name : 'null'} returning $result")
        return result
    }
    */

    @Override
    protected Class loadClass(final String name, boolean resolve) throws ClassNotFoundException {
        if (recompileOnScriptChanges) {
            // We want preferClassOverScript to be false so that it checks
            // the timestamp on the file for changes.
            return super.loadClass(name, true, false, resolve)
        } else {
            // preferClassOverScript false
            return super.loadClass(name, false, false, resolve)
        }
    }
}
