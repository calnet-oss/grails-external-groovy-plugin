package edu.berkeley.groovy

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

    ScriptClassLoader(Statistics stats) {
        super()
        this.stats = stats
        // increment the load count
        stats.signalClassLoaderLoad()
    }

    ScriptClassLoader(ClassLoader parent, Statistics stats) {
        super(parent)
        this.stats = stats
        // increment the load count
        stats.signalClassLoaderLoad()
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
}
