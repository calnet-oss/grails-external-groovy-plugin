External Groovy Script Plugin
=============================

This Grails plugin gives you the feature of running external Groovy scripts
using a class loader that can be garbage collected once the script is no
longer wanted.  An example use case is being able to run frequently-modified
scripts on the filesystem such that when a script is modified, the old
script class is available for garbage collection (with some possible gotchas
-- explained later.)

## Using the plugin

Create a directory for your external script or scripts.
Example:
```
cd <your grails application root directory>
mkdir external-scripts
```

In `grails-app/conf/Config.groovy`, set the default script directory for
your external scripts:
```
externalGroovy {
    defaultScriptDirectory = "external-scripts"
}
```

Create a script in your `external-scripts` directory, such as
`external-scripts/hello.groovy`.

`hello.groovy`:
```
println("hello world")
```

### To run this script from your application

Add an import to your application code:
```
import edu.berkeley.groovy.ScriptRunner
```

The plugin will inject a `scriptRunner` instance for you, so declare a
property:
```
ScriptRunner scriptRunner // injected by Spring
```

In your application code, run your `external-scripts/hello.groovy` script.
```
scriptRunner.runScript("hello") // remove .groovy file suffix
```

That should output "hello world" to your console.

When you run a script, you pass the script name without the `.groovy`
suffix.  You're actually passing the script's class name to `runScript()`
and the `external-scripts` directory is the classpath root for the script's
class loader.  You can put other classes in this directory too, as you
normally could with Groovy.

### Running with classes

As an example, you could put two files in your `external-scripts` directory:
`hello.groovy` and `Friend.groovy`.  `hello.groovy` will be the script you
launch, and `Friend.groovy` will contain a `Friend` class.

`hello.groovy`:
```
println(new Friend().toString())
```

`Friend.groovy`:
```
class Friend {
  @Override
  public String toString() {
    return "hello my friend"
  }
}
```

Then launch the hello script the same way as in the first example.

### Using packages

If you want to use packages, you can do that too.  For example, if you
wanted the `Friend` class to be placed in the `myexample` package, you'd do
the same as in the above example, but add `package myexample` at the top of
`Friend.groovy` and move it to the `external-scripts/myexample` directory.

You can also execute scripts that explicitly define its class.  The class
name should match the script filename (without the `.groovy` suffix).  This
class needs to implement a `run()` method.

Example:
`hello.groovy`:
```
class hello {
  void run() {
    println("hello world")
  }
}
```

### Returning values from the script

The `run()` method can also return an `Object` that
`ScriptRunner.runScript()` will return as a `String` (by calling the
`toString()` method on the result).  The reason why `runScript()` returns
`toString()` of the `Object` is because otherwise references may be held to
a class loaded by the script's class loader that prevents garbage
collection.

Example:
`hello.groovy`:
```
class hello {
  Object run() {
    return (2+2) // runScript() will return (2+2).toString(), so the string "4"
  }
}
```

For scripts where you don't explicitly define the script class, the last
statement executed is the value returned by `runScript()` (again, after
`toString()` is run on the resulting value).

Example:
`hello.groovy`:
```
(2+2) // runScript() will return (2+2).toString(), so the string "4"
```

### Property injection

The implementation of `ScriptRunner` will inject the `grailsApplication`
property into the script, assuming that you are using an injected
scriptRunner instance created by Spring.

It will also inject an instance of a Groovy `Logger` as `log`.

You can also add your own property injections by passing a map as the second
parameter to `runScript()`.

Example:
```
  scriptRunner.runScript("testScript", [myProperty: 'my test property'])
```

`testScript.groovy`:
```
  println(myProperty)
```

This example will print out `my test property` to the console.

### Caching script files but still recompiling them when they change

The caching of script files so that the class loader doesn't recompile them
every time a `runScript()` is called is the default behavior or when
`cacheUnmodifiedScripts=true` is passed to the constructor.

Example:
```
ScriptRunner scriptRunner = new ScriptRunnerImpl(scriptDirectory, true)
scriptRunner.runScript("hello") // will compile hello.groovy
scriptRunner.runScript("hello") // will use cached hello class
// go and modify hello.groovy
scriptRunner.runScript("hello") // will recompile hello.groovy because it changed
```

You can look at `scriptRunner.statistics.totalCompilationCount` to verify
that the `totalCompilationCount` is 2 for the above example.

This will be because the total compilation count increments for the first
`runScript()`, doesn't for the second (it's cached) and does for the third
(it recompiles due to modification).

To turn class caching off, instantiate a `ScriptRunnerImpl` with
`cacheUnmodifiedScripts=false`.

Example:
```
ScriptRunner scriptRunner = new ScriptRunnerImpl(scriptDirectory, false)
scriptRunner.runScript("hello") // will compile hello.groovy
scriptRunner.runScript("hello") // will recompile hello.groovy
// go and modify hello.groovy
scriptRunner.runScript("hello") // will recompile hello.groovy
```

You can look at `scriptRunner.statistics.totalCompilationCount` to verify
that the `totalCompilationCount` is 3 for the above example.  Each
runScript() results in a compilation regardless of whether the script file
has changed or not.

### Reloading the class loader (clearing the cache)

If running in cache mode, to clear the class cache, you can call
`scriptRunner.reloadClassLoader()`.  This will discard the class loader and
instantiate a new one, effectively clearing out your class cache.

### Automatically reloading the class loader when a script changes

To automatically detect script file changes and to reload the class loader
when a changed is detected, you can utilize the script monitor thread.  To
launch a script monitor thread for a `scriptRunner`:

```
  scriptRunner.launchScriptFileMonitorThread(30)
```

The parameter to `launchScriptFileMonitorThread()` is the check interval in
number of seconds.  In the above example, the thread will scan the script
directory every 30 seconds for script file changes.

To stop it when you're done with your `scriptRunner`:

```
  scriptRunner.stopScriptFileMonitorThread()
```

### ScriptLoaderImpl 

If you want more ScriptLoaders other than the default, you can add as many
ScriptLoader beans as you want via `resources.groovy`.  You'll probably also
want to add the script directories for these loaders in the `externalGroovy`
section in your `Config.groovy` file.

Example of adding two more scriptRunners:

`Config.groovy`:
```
externalGroovy {
    defaultScriptDirectory = "external-scripts/default"
    scriptDirectory2 = "external-scripts/scriptDirectory2"
    scriptDirectory3 = "external-scripts/scriptDirectory3"
}
```

Note your script directories can be absolute paths instead of relative to
your grails application directory.

`resources.groovy`:
```
import edu.berkeley.groovy.ScriptRunnerImpl
```
```
scriptRunner2(ScriptRunnerImpl, application.config?.externalGroovy?.scriptDirectory2)
scriptRunner3(ScriptRunnerImpl, application.config?.externalGroovy?.scriptDirectory3)
```

There a few other other parameters you can pass to a `ScriptRunnerImpl`
constructor.

In `resources.groovy`, you can instantiate a bean using all the options using
the map constructor:
```
scriptRunner4(ScriptRunnerImpl, [
  scriptDirectory: application.config?.externalGroovy?.scriptDirectory4,
  bootstrapScriptFile: new File("external-scripts/bootstrap/Bootstrap.groovy"),
  parentClassLoader: null
])
```

In addition to `scriptDirectory`, there is:

 * `bootstrapScriptFile` - If you want to override the default bootstrap
   code, you can provide the `File` to your own Bootstrap.groovy file.

 * `parentClassLoader` - By default, the script's class loader will have the
   Grails class loader as its parent class loader.  This means scripts can
   load any class that your Grails app can load.  You can either specify a
   different parent ClassLoader, or you can set it to null, which means the
   scripts will only be able to load classes from the JVM's system class
   loader and from classes in scripts in your scriptDirectory.

 * `cacheUnmodifiedScripts` - If true, will cache script file classes if the
   script file goes unmodified, but will still recompile script files when
   they are modified.

If you want to prevent injecting `grailsApplication` into the scripts, then
after you instantiate your `ScriptRunner`, you can do:
```
scriptRunner.grailsApplication = null
````

You can also set the `parentClassLoader` after instantiation.

---

**The rest of this page you only need to read if you're running into class
unloading issues.**

---

## Class Unloading

Class unloading can be tricky business due to the inherent nature of object
reference graphs.  The script classes will be available for garbage
collection when no references are held to any of the classes and class
instances loaded by the script's class loader.  This should be the case for
"simple scripts" that execute and exit, but if the script utilizes certain
classes and objects within the parent class loader scope, then one or more
of those classes or objects may gain and hold a reference to a class or
object within the script's class loader scope.  The same can be said if the
script launches threads that don't exit.

Advice:
 * Try to keep your scripts simple.  If you're utilizing advanced
   (non-every-day) JDK classes or Grails features, consider putting this
   code in the parent class loader's classpath so that this class code is
   shared among all scripts and not loaded/unloaded dynamically by the
   script's class loader.  All script class loaders would share this parent.
 * Avoid launching new threads within the script, especially daemon or Timer
   threads, or threads that outlast the duration of the script's execution
   (i.e., its `run()` method).  You don't want any threads from your script
   to still be running when the script exits.  If there are, references will
   still be held that will prevent garbage collection of the script class
   loader and its loaded classes.
 * Most everyday JDK classes are "class unloading friendly", meaning they
   don't cache references to objects in unexpected ways when you use them
   (in static class memory, for example).  However, some of the lesser-used
   classes may do internal caching of objects that you otherwise wouldn't
   expect that will prevent the script class loader becoming available for
   garbage collection.  Or, they launch threads.  If this starts happening,
   there's really no other way to hunt down the problem other than by the
   technique described in the "Debugging Class Unloading Issues" section
   below.
 * Consider setting the ScriptRunnerImpl's parent class loader to null if
   you're not using Grails features within your scripts, or not needing
   classes from the web application's class loader.
 * Don't create new ClassLoaders in your scripts or utilize libraries that
   do so.  If you use anything other than the ScriptClassLoader within your
   script, then you run the likely risk that these class loaders will create
   classes that end up cached by the `GroovySystem` `metaClassRegistry`.  If
   these classes are cached there, then the script's class loader won't be
   eligible for garbage collection due to cached classes in the metaClass
   registry.

## Debugging Class Unloading Issues

In Java 7 and earlier, classes are loaded into "PermGen" space.  (Java 8 got
rid of this separate "PermGen" space.  See
[Where Has the Java PermGen Gone?](http://www.infoq.com/articles/Java-PERMGEN-Removed).

If you are using this plugin, modify external scripts a lot, and see
OutOfMemory PermGen exceptions thrown after running for awhile, then the
script class loaders probably aren't getting garbage collected.  (In Java
8's case and above, you won't see PermGen exceptions, but will still see
some kind of OutOfMemory exception if you fill up the space with unloaded
classes.) If this happens, there are likely outside objects holding
reference to a class that the script's class loader loaded.  In object
reference graph terms, there's a path from a "Root object" to the script's
class loader.

The way to hunt this down is by using `jmap` and `jhat` to do a heap dump,
run `jhat` on that heap dump, and see what references are still being held
to the script's class loader.  Use the "Reference Chains from Rootset" link
on the `ScriptClassLoader` instance page on the `jhat` web server to trace
the reference from the root object(s) to the class loader.

Example:

Here's a badly behaving script that launches a thread that never exits:
```
class NeverEndingThread extends Thread {
  @Override
  public void run() {
    try
    {
      while(true) {
        sleep(1000)
      }
    }
    catch(Exception e) {
      e.printStackTrace()
    }
    println("exiting thread") // should never happen
  }
}

new NeverEndingThread().start()

"exited"
```


```
$ jps
30363 ForkedTomcatServer
```

30363 is the PID.  Pass that to `jmap`.

```
rm -f heap.dump; jmap -dump:live,format=b,file=heap.dump 30363
jhat heap.dump
```

A `jhat` web server starts listening on port 7000.

Go to http://localhost:7000/

Find the link for `class edu.berkeley.groovy.ScriptClassLoader`.

On that page, under the **Instances** heading, click the `Include
subclasses` link.

Choose one of the `edu.berkeley.groovy.ScriptClassLoader` instances that you
know should not be hanging around anymore (if you see none, perhaps you have
a memory problem unrelated to this plugin).

You're now on the page for an instance of `ScriptClassLoader`.  Towards the
bottom, under the **Other Queries** heading, and under the "Reference Chains
from Rootset" line, click on the `Exclude weak refs` link.

You'll probably see multiple references from root objects, but when I ran this test, the first reference looked like this:

```

Static reference from java.lang.UNIXProcess$ProcessReaperThreadFactory.group (from class java.lang.UNIXProcess$ProcessReaperThreadFactory) :
--> java.lang.ThreadGroup@0xf805f678 (67 bytes) (field groups:)
--> [Ljava.lang.ThreadGroup;@0xf8061008 (48 bytes) (Element 0 of [Ljava.lang.ThreadGroup;@0xf8061008:)
--> java.lang.ThreadGroup@0xf805f648 (67 bytes) (field threads:)
--> [Ljava.lang.Thread;@0xfab9d6e0 (272 bytes) (Element 14 of [Ljava.lang.Thread;@0xfab9d6e0:)
--> NeverEndingThread@0xfab6b908 (163 bytes) (??:)
--> class NeverEndingThread (112 bytes) (??:)
--> groovy.lang.GroovyClassLoader$InnerLoader@0xfab73448 (194 bytes) (field parent:)
--> edu.berkeley.groovy.ScriptClassLoader@0xfab14138 (202 bytes) 
```

This shiows me there's a thread instance, `NeverEndingThread@0xfab6b908`,
loaded by my script's class loader.

From there, I start looking at my script code to find where this
`NeverEndingThread` is being launched, and I fix the issue by either
removing the thread launch altogether, or at a minimum, ensuring the thread
will exit in a reasonable amount of time.

Threads aren't the only possible culprit.  You may find that a JDK or Grails
class is holding a static reference: possibly a cache in the class's static
memory that's caching an object or class from your script because your
script used that class, directy or indirectly, which in turn did you the
disservice of invisibly caching something you didn't want cached.  In these
cases, the fix is to change your script to not utilize whatever is causing
this hidden caching to happen.
