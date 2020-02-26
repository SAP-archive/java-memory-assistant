# Java Memory Assistant

A Java agent (as in [`-javaagent`](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/Instrumentation.html)) that automates the generation of heap dumps when thresholds in terms of memory usage of the entire `heap` or single memory pools (e.g., `eden`, `old gen`, etc.) are met.

Unlike other Java agents, the Java Memory Assistant does *not* do bytecode manipulation via the [Instrumentation API](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/Instrumentation.html). It is shipped as a Java agent only for convenience, so that its functionality can be added to any Java application without having to modify the latter's content or logic.

## Usage

The Java Memory Assistant jar file must be passed to the startup parameters of the JVM using the `-javaagent` option, e.g.:

```
java -javaagent:jma.jar -jar yourApp.jar
```

By default, the Java Memory Assistant does nothing. To activate it, one needs to pass at least two System properties as options:

- `jma.check_interval`: every how often will the agent poll the JVM runtime to check the usage state of the memory pools
- `jma.thresholds.[memory pool or 'heap']`: a usage threshold for the specified [memory pool](https://docs.oracle.com/javase/7/docs/api/java/lang/management/MemoryPoolMXBean.html) or for the entire heap

Thresholds of multiple memory pools are supported. The heap dump is then triggered when a check is executed and the usage of at least one memory pool is higher or equal to the threshold specified. For example:

```
java -javaagent:jma.jar -Djma.check_interval=5000ms -Djma.thresholds.heap=60 -jar yourApp.jar
```

These startup parameters would make the Java Memory Assistant check every 5 seconds how much of the heap is in use and, if 60% or more of the heap is allocated, it will trigger a heap dump using the [HotSpotDiagnosticMXBean#dumpHeap(String, boolean)](http://docs.oracle.com/javase/6/docs/jre/api/management/extension/com/sun/management/HotSpotDiagnosticMXBean.html) facility.

## <a href="config_properties"></a>Supported configurations

<table>
<thead>
<tr>
<td>System Property</td>
<td>Supported values</td>
<td>Description</td>
<td>Default value</td>
</tr>
</thead>
<tbody>
<tr>
<td>jma.enabled</td>
<td><code>true</code>, <code>false</code></td>
<td>Whether the agent has to activate itself or not, irrespective from any thresholds or other settings specified</td>
<td><code>true</code></td>
</tr>
<tr>
<td>jma.check_interval</td>
<td><code>(0, 2147483647]ms|s|m|h</code></td>
<td>How often will the agent check for the memory usage conditions. The numeric value must be and <code>int</code> between 1 and 2147483647 (extremes included). The time unit is one of <code>ms</code> (milliseconds), <code>s</code> (seconds), <code>m</code> (minutes) or <code>h</code> (hours).</td>
<td><code>-1</code> (disabled)</td>
</tr>
<tr>
<td>jma.max_frequency</td>
<td><code>(1, 2147483647]/(1, 2147483647]ms|s|m|h</code></td>
<td>How often can agent create heap dumps in a given time-span. Both numeric values must be <code>int</code> between 1 and 2147483647 (extremes included). The time unit is one of <code>ms</code> (milliseconds), <code>s</code> (seconds), <code>m</code> (minutes) or <code>h</code> (hours).</td>
<td>no maximum frequency specified</td>
</tr>
<tr>
<td>jma.log_level</td>
<td><code>DEBUG</code>, <code>WARN</code>, <code>INFO</code>, <code>ERROR</code>, <code>OFF</code></td>
<td>The minimum severity of the logs to be printed; all logs from severity <code>INFO</code> and lower are printed to <code>System.out</code>; <code>ERROR</code> is printed to <code>System.err</code>; if <code>OFF</code> is specified, no logs are written anywhere</td>
<td><code>ERROR</code></td>
</tr>
<tr>
<td>jma.heap_dump_folder</td>
<td>Any valid local of absolute filesystem path in which the JVM process can create files</td>
<td>Filesystem location where heap dumps will be created</td>
<td><code>System.getProperty("user.dir")</code></td>
</tr>
<tr>
<td>jma.heap_dump_name</td>
<td>The pattern to use to generate file names for heap dumps, e.g., <code>heapdump_%host_name%_%ts%.hprof</code></td>
<td>Name of the file under the <code>jma.heap_dump_folder</code> where the heap dump will be stored</td>
<td><code>heapdump_%host_name%_%ts:yyyyMMddHHmmss%.hprof</code></td>
</tr>
<tr>
<td>jma.thresholds.heap</td>
<td>The thresholds can be specified as one of the following:
<ul>
<li>absolute percentage threshold, e.g., more than 200 MB (`&gt;200MB`) or less than 40KB (`&lt;40k`); the first token is a comparison operator out of `&lt;` (strictly lesser than), `&lt;=` (strictly lesser than or equal to), `==` (equal to, exact to the byte), `=&gt;` (equal to or greater than) and `&gt;` (strictly greater than); supported memory units are `GB`, `MB` and `KB`</li>
<li>usage percentage threshold, i.e., any number between 0 and 99.99 followed by the '%' sign (precise to the second decimal digit, e.g., 42.42)</li>
<li>percentage-based increase-over-time-frame specification, e.g., <code>+5%/4s</code> (5% increase over 4 seconds); time unit is one of <code>ms</code> (milliseconds), <code>s</code> (seconds), <code>m</code> (minutes) or <code>h</code> (hours)</li>
</ul>
</td>
<td>The usage threshold of the overall heap that, when reached or surpassed, triggers a heap dump</td>
<td><code>null</code> (disabled)</td>
</tr>
<tr>
<td>jma.thresholds.[memory_pool_name]</td>
<td>Either a usage percentage threshold, i.e., any number between 0 and 99.99 followed by the '%' sign (precise to the second decimal digit, e.g., 42.42), or a percentage-based increase-over-time-frame specification, e.g., <code>+5%/4s</code> (5% increase over 4 seconds); time unit is one of <code>ms</code> (milliseconds), <code>s</code> (seconds), <code>m</code> (minutes) or <code>h</code> (hours). Supported memory pools <a href="#supported_jvms">depend on the JVM</a></td>
<td>The usage threshold of the particular memory pool that, when reached or surpassed, triggers a heap dump</td>
<td><code>null</code> (disabled)</td>
</tr>
<tr>
<td>jma.command.interpreter</td>
<td>Any string</td>
<td>A OS-specific interpreter (e.g., shell, cmd) that will be executed via the JDK <a href="https://docs.oracle.com/javase/7/docs/api/java/lang/ProcessBuilder.html">java.lang.ProcessBuilder</a> API. If set to <code>""</code>, the command interpreter is ignored.</td>
<td><code>cmd</code> on Windows, <code>sh</code> otherwise</td>
</tr>
<tr>
<td>jma.execute.before</td>
<td>Any string</td>
<td>A script that will be executed by the command interpreter before a heap dump is created; it receives as first (and only) input the filename of the heap dump that will be created</a> API</td>
<td><code>null</code> (disabled)</td>
</tr>
<tr>
<td>jma.execute.after</td>
<td>Any string</td>
<td>A script that will be executed by the command interpreter after a heap dump has been created; it receives as first (and only) input the filename of the heap dump that has been created</a> API</td>
<td><code>null</code> (disabled)</td>
</tr>
<tr>
<td>jma.execute.on_shutdown</td>
<td>Any string</td>
<td>A script that will be executed by the command interpreter as a shutdown hook (as in <code>java.lang.Runtime.addShutdownHook(java.lang.Thread)</code>) upon a soft shutdown of the JVM, e.g., when <code>java.lang.System.exit(int)</code> is invoked; the command invoked receives no input.</td>
<td><code>null</code> (disabled)</td>
</tr>
</tbody>
</table>

## File names

File names for heap dumps can be generated using a combination of fixed characters and tokens that can replaced with values.
Tokens are delimited by the `%` character.
For example, `my_heapdump_%host_name%_%uuid%.hprof` is a pattern with two tokens: `%host_name%` and `%uuid%`.
When generating a heap dump, the name of the file where it will be stored is calculated by replacing `%host_name%` with the hostname of the machine running the Java process (e.g., `localhost`) and a random [UUID](https://www.ietf.org/rfc/rfc4122.txt).
That is, the heap dump name may look something like `my_heapdump_localhost_19866c22-ce15-41de-807b-4805d0387d76.hprof`.

The Java Memory Assistant supports the following tokens:

<table>
<thead>
<tr>
<td>Token</td>
<td>Supports configuration?</td>
<td>Description</td>
</tr>
</thead>
<tbody>
<tr>
<td>%host_name%</td>
<td>forbidden</td>
<td>The network name of the host of the Java Virtual Machine, calculated as: <code>java.net.InetAddress.getLocalHost().getHostName()</code>.</td>
</tr>
<tr>
<td>%uuid%</td>
<td>forbidden</td>
<td>A random <a href="https://www.ietf.org/rfc/rfc4122.txt">UUID</a> of 128 bits, calculated as: <code>java.util.UUID.randomUUID().toString()</code>.</td>
</tr>
<tr>
<td>%ts% or %ts:[date format]%</td>
<td>optionally: a valid <code>java.text.SimpleDateFormat</code> pattern</td>
<td>The number of millis since the Unix epoch, calculated as <code>java.lang.System.currentTimeMillis()</code>, or its value formatted with the pattern provided as configuration; the formatter uses the <code>java.util.TimeZone.getDefault()</code> time zone.</td>
</tr>
<tr>
<td>%env:<property name>['['i, j']']%</td>
<td>required: the name of an environment property</td>
<td>The name of an environment property, the value of which replaces the token.
The value is looked up via the <code>java.lang.System.getenv([property name])</code> call.
If the environment property is not present, the token will generate no characters.
The value of the environment property can be truncated using the `[a, b]` notation appended to the environment variable name to select a substring of the value.</td>
</tr>
</tbody>
</table>

The timestamp `%ts%` token supports configuration that specifies the pattern to use to format the timestamp.
Configuration is passed after the `:` character that follows the token name, e.g., `%ts:[config]%`.
Configuration is optional.
If no configuration is passed to the `%ts%` token, the number of millis since the epoch is printed out.
For example, assume we were to generate a heap dump exactly at Unix epoch, we would have the following file names:

* `heap_dump_%ts%.hprof` generates `heap_dump_0.hprof`
* `heap_dump_%ts:yyyyMMddmmssSS.hprof` generates `heap_dump_19700101000000.hprof`

The environment `%env:...%` token allows you to insert in the heap dump names the value of an environment property.
The environment property is evaluated once at agent's startup.
If the environment property is not set, the token does not generate any character in the heap dump name.

The `%` character also serves as escape character for itself.
Escaping needs to be performed both in token as well as in the `fixed` part of a pattern:

* `heap_%%_dump.hprof` generates `heap_%_dump.hprof`
* `heap_dump_%ts:yyyyMMdd'%%'mmssSS.hprof` generates `heap_dump_19700101%000000.hprof`

## Integration tests

End-to-end integration tests are run automatically in the JVM running Gradle.

Additionally, the same tests are repeated automatically for each of the [supported JVMs](supported_jvms), provided that the binaries of the JVM are available in the right folder (more on this later).

This repository does *not* contain any JVM binaries, as that would count as distribution and violate most EULAs out there. (Plus, polluting GIT with tons of binaries is bad form.)

JVM for the integration tests have to be placed in folders under the `test-e2e/src/test/resources/jdks` directory.
The `java` executable must be under the `test-e2e/src/test/resources/jdks/[jvm_directory]/bin/java` path.
The name of the `[jvm_directory]` will given the name to the Gradle `itest` task that will run the integration tests with that JVM.

## <a href="supported_jvms"></a>Supported JVMs

All JVMs support thresholds for the entire heap ([specified via the `jma.thresholds.heap` system property](config_properties)). The specific memory pools, however, depend on the particular JVM.
The Java Memory Assistant currently supports the following JVMs and settings thresholds for the specific memory areas.
Trying to run the Java Memory Assistant on an unsupported JVM will lead to the agent disabling itself, but won't impact the rest of the JVM or the application running inside it.

### OpenJDK 7.x

Supported memory pools:
- `eden`
- `survivor`
- `old_gen`
- `perm_gen`
- `code_cache`

### OpenJDK 8.x

Supported memory pools:
- `eden`
- `survivor`
- `old_gen`
- `metaspace`
- `code_cache`
- `compressed_class`

### Oracle JVM 7.x

Supported memory pools:
- `eden`
- `survivor`
- `old_gen`
- `perm_gen`
- `code_cache`

### Oracle JVM 8.x

Supported memory pools:
- `eden`
- `survivor`
- `old_gen`
- `metaspace`
- `code_cache`
- `compressed_class`

### SAP JVM 7.x

Supported memory pools:
- `eden`
- `survivor`
- `old_gen`
- `metaspace`
- `code_cache`
- `compressed_class`

### SAP JVM 8.x

Supported memory pools:
- `eden`
- `survivor`
- `old_gen`
- `metaspace`
- `code_cache`
- `compressed_class`

## Build
This project should be built with a Java 1.7 JDK.
Building it with a Java 1.8 JDK will also work, but there will be warnings reporting that the `bootstrap classpath [is] not set in conjunction with -source 1.7`.
 
To trigger a build, run the following command from the root of this repository:
 ```
 ./gradlew clean build
 ```
 
For building behind a proxy, consider setting the proxy-variables as follows:
 ```
./gradlew -Dhttps.proxyHost=[proxy_hostname] -Dhttps.proxyPort=[proxy_port] -Dhttp.proxyHost=[proxy_hostname] -Dhttp.proxyPort=[proxy_port] clean build
 ```
where, for example `[proxy_hostname]` is `proxy.wdf.sap.corp` and `[proxy_port]` is `8080`.

## License

This project is licensed under the Apache Software License, v. 2 except as noted otherwise in the LICENSE file.
