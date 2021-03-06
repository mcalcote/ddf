# How to Debug Integration Tests

## Remote Debugging
Use the `isDebugEnabled` property to force the integration test to pause during startup and wait for a debugger to connect to port 5005.

```
mvn clean verify -DisDebugEnabled=true
```

## Debugging Pax Exam Setup
Pax Exam uses two JVMs: the first is used to perform Pax Exam configuration, and the second is launched by Pax Exam to perform the actual integration tests. This second JVM is the one you connect to on port 5005.

As a result, if you want to debug code that is run during Pax Exam setup itself (for example, inside the `@org.ops4j.pax.exam.Configuration` configuration method), you need to startup the tests in your IDE and use a local debugger to catch Pax Exam before it spawns the second JVM process.

In short:
* Use remote debugging to debug tests themselves or anything related to production code
* Use local debugging to debug the code that sets up and configures the testing environment

## SSH Into a Running Instance
It is possible to SSH into a running test instance. This will allow you to use the shell to inspect the runtime state while the test probe is installed and running. The SSH port is dynamic and can be found in `target/exam/<GUID>/etc/org.apache.karaf.shell.cfg`.

```
ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -p 20003 admin@localhost
```

## Running Tests
The Pax Exam tests support Maven Surefire Plugin properties. One useful property is the `it.test` property to select a single test class or method to execute.

```
mvn clean verify -Dit.test=TestFederation
mvn clean verify -Dit.test=TestFederation#<testMethodName>
```

Multiple test classes can also be executed sequentially using comma separation.

```
mvn clean verify -Dit.test=TestFederation,TestSecurity
```

This can be combined with the `isDebugEnabled` property.

## Investigating the Test Container
Use the `keepRuntimeFolder` property to keep the test container for test failure investigation.

```
mvn clean verify -DkeepRuntimeFolder=true
```

The runtime folder used during the test will be available under `target/exam/<GUID>`. It is possible to rerun the instance and verify that all bundles (excluding the test probe) are installed and working properly. You can also inspect the logs under `target/exam/<GUID>/data/logs`.

## Adjusting the Log Level
By default, itests are run at a log level of `WARN` for increased performance.
If you want to change the logging level, use the flags `-DitestLogLevel=<level>` or `-DsecurityLogLevel=<level>`. Valid levels are defined by the [SLF4J API](http://www.slf4j.org/api/org/apache/commons/logging/Log.html).

## Run unstable tests
By default, all tests that include a call to `unstableTest` will not be run. To include them as part of a build, add the `-DincludeUnstableTests=true` property to the Maven command.
