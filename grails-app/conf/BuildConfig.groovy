grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.server.port.http = 8095

grails.project.fork = [
        // configure settings for compilation JVM, note that if you alter the Groovy version forked compilation is required
        //  compile: [maxMemory: 256, minMemory: 64, debug: false, maxPerm: 256, daemon:true],

        // configure settings for the test-app JVM, uses the daemon by default

        // We want SMALL memory heap and permgen space here for class unloading
        // testing purposes.  We want the garbage collector to trigger early.
        test   : [maxMemory: 90, minMemory: 32, debug: false, maxPerm: 64, daemon: true],

        // configure settings for the run-app JVM
        run    : [maxMemory: 128, minMemory: 32, debug: false, maxPerm: 64, forkReserve: false],
        // configure settings for the run-war JVM
        war    : [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve: false],
        // configure settings for the Console UI JVM
        console: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256]
]

grails.project.dependency.resolver = "maven" // or ivy
grails.project.repos.default = "calnet-plugins"
grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // uncomment to disable ehcache
        // excludes 'ehcache'
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
        mavenLocal()
        mavenRepo id: "calnet-repo", url: "https://maven.calnet.berkeley.edu/artifactory/all/"
    }
    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes eg.
        // runtime 'mysql:mysql-connector-java:5.1.27'
    }

    plugins {
        build(":release:3.0.1",
                ":rest-client-builder:1.0.3") {
            export = false
        }

        // for testing
        build ":tomcat:7.0.55"
    }
}
