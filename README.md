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

Create a script in your `external-scripts` directory, such as
`external-scripts/hello.groovy`.

hello.groovy:
```
println("hello world")
```

To run this script from your application:
```
import edu.berkeley.groovy.ScriptRunnerImpl
```
```
ScriptRunnerImpl scriptRunner = new ScriptRunnerImpl(new File("external-scripts"))
scriptRunner.runScript("hello") // remove .groovy file suffix
```

That should output "hello world" to your console.

Notice that when you run a script, you pass the script name without the
`.groovy` suffix.  You're actually passing the script's class name to
`runScript()` and the `external-scripts` directory is the classpath root for
the script's class loader.  You can put other classes in this directory too,
as you normally could with Groovy.

As an example, you put two files in your `external-scripts` directory:
`hello.groovy` and `Friend.groovy`.  `hello.groovy` will be the script you
launch, and `Friend.groovy` will contain a `Friend` class.

hello.groovy:
```
println(new Friend().toString())
```

Friend.groovy:
```
class Friend {
  @Override
  public String toString() {
    return "hello my friend"
  }
}
```

Then launch the hello script the same way as in the first example.

If you want to use packages, you can do that too.  For example, if you
wanted the `Friend` class to be placed in the `myexample` package, you'd do
the same as in the above example, but add `package myexample` at the top of
`Friend.groovy` and move it to the `external-scripts/myexample` directory.

The rest of this page you only need to read if you're running into class
unloading issues.

## Class Unloading

Class unloading can be tricky business due to the inherent nature of object
reference graphs.  The script classes will be available for garbage
collection when no references are held to any of the classes and class
instances loaded by the script's class loader.  This should be the case for
"simple scripts" that execute and exit, but if the script utilizes certain
classes and objects within the parent class loader scope, then one or more
of those classes or objects may gain and hold a reference to a class or
object with the script's class loader scope.  The same can be said if the
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
   technique described in the "Debugging Unloading Issues" section below.

## Debugging Class Unloading Issues

In Java 7 and earlier, classes are loaded into "PermGen" space.  (Java 8 got
rid of this separate "PermGen" space.  See (Where Has the Java PermGen
Gone?)[http://www.infoq.com/articles/Java-PERMGEN-Removed]).

If you are using this plugin, modify external scripts a lot, and see
OutOfMemory PermGen exceptions thrown after running for awhile, then the
script class loaders probably aren't getting garbage collected.  (In Java
8's case and above, you won't see PermGen exceptions, but will still see
some kind of OutOfMemory exception if you fill up the space with unloaded
classes.) If this happens, there are likely outside objects holding
reference to a class that the script's class loader loaded.  In object
reference graph terms, there's a path from a "Root object" to the script's
class loader.

The way to hunt this down is by using jmap and jhat to do a heap dump, run
jhat on that heap dump, and see what references are still being held to the
script's class loader.  Use the "path from rootset" link on the
ScriptClassLoader instance page on the jhat web server to trace the
reference from the root object(s) to the class loader.

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

On that page, under the *Instances* heading, click the `Include subclasses`
link.

Choose one of the `edu.berkeley.groovy.ScriptClassLoader` instances that you
know should not be hanging around anymore (if you see none, perhaps you have
a memory problem unrelated to this plugin).

You're now on the page for an instance of ScriptClassLoader.  Towards the
bottom, under the *Other Queries* heading, and under the "Reference Chains
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
class is holding a static reference.  Possibly a cache in the class's static
memory that's caching an object or class from your script because your
script used that class, directy or indirectly, which in turn did you the
disservice of invisibly caching something you didn't want cached.  In these
cases, the fix is to change your script to not utilize whatever is causing
this hidden caching to happen.
