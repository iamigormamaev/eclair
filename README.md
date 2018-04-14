# Eclair
> Java Spring library for AOP logging

Provides annotations for declarative logging of annotated method execution.
Includes abstractions for annotations processing, simple implementation and Spring Boot starter with auto-configuration.

## Features

* events logging detected by Spring AOP: *beginning*, *ending* or *emergency ending* of method execution
* flexible filtering `Throwable` types for logging
* configurable verbosity based on the enabled log level
* pre-defined printers to log arguments or method return value in different formats:
    * `JSON` (by Jackson)
    * `XML` (by JAXB)
* declarative defining (with *SpEL*) and erasing of *Mapped Diagnostic Context* (MDC) based on scopes
* multiple logger definition
* annotations validation during application context start
* ability to use meta-annotations (applied to other annotations) and annotated method overriding
* manual logging with invoker class detection is also available

## Getting started

Add this to your POM.

```xml
<dependency>
    <groupId>ru.tinkoff</groupId>
    <artifactId>eclair-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage examples

The examples assume that you are using a standard `SimpleLogger` and that you have the following configuration property:

```yaml
logging.pattern.console: '%-5level [%X] %logger{80} %msg%n'
```

#### Basic usage

```java
class Example {

    /**
     * DEBUG [] ru.tinkoff.eclair.example.Example.simple > s="a", i=0, d=0.0
     * DEBUG [] ru.tinkoff.eclair.example.Example.simple < false
     */
    @Log
    public Boolean simple(String s, Integer i, Double d) {
        return false;
    }

    /**
     * if logger level <= DEBUG
     *
     * DEBUG [] ru.tinkoff.eclair.example.OuterClass.method >
     * ..
     * DEBUG [key=value, sum=2] ru.tinkoff.eclair.example.Example.mdcByMethod > Dto{i=0, s='null'}
     * ..
     * DEBUG [key=value, sum=2] ru.tinkoff.eclair.example.InnerClass.method >
     * DEBUG [key=value, sum=2] ru.tinkoff.eclair.example.InnerClass.method <
     * ..
     * DEBUG [key=value, sum=2] ru.tinkoff.eclair.example.Example.mdcByMethod <
     * ..
     * DEBUG [sum=2] ru.tinkoff.eclair.example.OuterClass.method <
     */
    @Mdc(key = "key", value = "value")
    @Mdc(key = "sum", value = "1 + 1", global = true)
    @Log
    public void mdcByMethod(Dto dto) {
    }

    @Autowired
    private ManualLogger logger;

    /**
     * if logger level <= DEBUG
     *
     * DEBUG [] ru.tinkoff.eclair.example.Example.manualLogging >
     * DEBUG [key=value] ru.tinkoff.eclair.example.Example.manualLogging - Manual logging: 0.123
     * INFO  [key=value] ru.tinkoff.eclair.example.Example.manualLogging - Lazy manual logging: 0.456
     * DEBUG [key=value] ru.tinkoff.eclair.example.Example.manualLogging <
     */
    @Log
    public void manualLogging() {
        MDC.put("key", "value");
        logger.debug("Manual logging: {}", new Random().nextDouble());
        logger.info("Lazy manual logging: {}", (Supplier) () -> new Random().nextDouble());
    }
}
```

#### Advanced usage

```java
class Advanced {

    /**
     * DEBUG [] ru.tinkoff.eclair.example.Advanced.verboseDtoToXml > dto=<dto><i>0</i></dto>
     */
    @Log.in(printer = "xml")
    public void verboseDtoToXml(Dto dto, Integer i) {
    }

    /**
     * if logger level = DEBUG
     * WARN  [] r.t.e.example.Advanced.levelIfEnabledErrorEvent ! java.lang.RuntimeException: message
     * java.lang.RuntimeException: message
     *     at r.t.e.example.Advanced.levelIfEnabledErrorEvent(Example.java:167)
     *
     * if logger level = INFO
     * (nothing to log)
     */
    @Log.error(ifEnabled = DEBUG)
    public void levelIfEnabledErrorEvent() {
        throw new RuntimeException("message");
    }

    /**
     * DEBUG [] r.t.e.example.Advanced.verboseToVariousPrinters > xmlDto=<dto><i>0</i></dto>, jsonDto={"i":0,"s":null}
     */
    public void verboseToVariousPrinters(@Log(printer = "xml") Dto xmlDto,
                                         @Log(printer = "json") Dto jsonDto,
                                         Integer i) {
    }

    /**
     * if logger level <= DEBUG
     *
     * DEBUG [] ru.tinkoff.eclair.example.OuterClass.method >
     * ..
     * DEBUG [length=3, staticString=some string] ru.tinkoff.eclair.example.Advanced.mdcByArg > Dto{i=0, s='null'}
     * DEBUG [length=3, staticString=some string] ru.tinkoff.eclair.example.Advanced.mdcByArg <
     * ..
     * DEBUG [staticString=some string] ru.tinkoff.eclair.example.OuterClass.method <
     */
    @Log
    public void mdcByArg(@Mdc(key = "length", value = "s.length()")
                         @Mdc(key = "staticString", value = "some string", global = true) Dto dto) {
    }

    @Autowired
    private ManualLogger logger;

    /**
     * if logger level = DEBUG
     */
    public void manualLevelLogging() {
        // WARN  [] r.t.eclair.example.Advanced.manualLevelLogging - warn
        // WARN  [] r.t.eclair.example.Advanced.manualLevelLogging - warnIfInfoEnabled
        // WARN  [] r.t.eclair.example.Advanced.manualLevelLogging - warnIfDebugEnabled
        // nothing to log
        logger.warn("warn");
        logger.warnIfInfoEnabled("warnIfInfoEnabled");
        logger.warnIfDebugEnabled("warnIfDebugEnabled");
        logger.warnIfTraceEnabled("warnIfTraceEnabled");
    }

    @Log
    @Log(logger = "audit")
    public void logMultiLogger() {
    }
}
```

## Release History

04.2018 - 1.0.0 Basic features

## License

```
Copyright 2018 Tinkoff Bank
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## All examples
The left column shows the configured available logging level for the current method.<br>
The right column shows an example log for the specified level.

All available log levels in order from the most common `TRACE` to the rarest `FATAL` (`OFF` deactivates logging completely):<br>
`TRACE` > `DEBUG` > `INFO` > `WARN` > `ERROR` > `FATAL` > `OFF`

#### Simplest
`DEBUG` level by default.
```java
@Log
void simple() {
}
```
 Enabled level      | Log sample
--------------------|------------
 `TRACE` `DEBUG`    | `DEBUG [] r.t.eclair.example.Example.simple >`<br>`DEBUG [] r.t.eclair.example.Example.simple <`
 `INFO` .. `OFF`    | -

#### With thrown exception
```java
@Log
void simpleError() {
    throw new RuntimeException();
}
```
 Enabled level      | Log sample
--------------------|------------
 `TRACE` `DEBUG`    | `DEBUG [] r.t.e.example.Example.simpleError >`<br>`DEBUG [] r.t.e.example.Example.simpleError !`
 `INFO` .. `OFF`    | -

#### Explicit `INFO` level
```java
@Log(INFO)
void level() {
}
```
 Enabled level      | Log sample
--------------------|------------
 `TRACE` .. `INFO`  | `INFO  [] r.t.eclair.example.Example.level >`<br>`INFO  [] r.t.eclair.example.Example.level <`
 `WARN` .. `OFF`    | -

#### Log as `INFO` if enabled `DEBUG` level
```java
@Log(level = INFO, ifEnabled = DEBUG)
void ifEnabled() {
}
```
 Enabled level      | Log sample
--------------------|------------
 `TRACE` `DEBUG`    | `INFO  [] r.t.e.example.Example.ifEnabled >`<br>`INFO  [] r.t.e.example.Example.ifEnabled <`
 `INFO` .. `OFF`    | -

#### Influence of configured level to verbosity
```java
@Log(INFO)
boolean verboseLevel(String s, Integer i, Double d) {
    return false;
}
```
 Enabled level      | Log sample
--------------------|------------
 `TRACE` `DEBUG`    | `INFO  [] r.t.e.example.Example.verboseLevel > s="s", i=4, d=5.6`<br>`INFO  [] r.t.e.example.Example.verboseLevel < false`
 `INFO`             | `INFO  [] r.t.e.example.Example.verboseLevel >`<br>`INFO  [] r.t.e.example.Example.verboseLevel <`
 `WARN` .. `OFF`    | -

#### Verbosity disabled
Arguments and return value not printed for any level.
```java
@Log(verbose = OFF)
boolean verboseDisabled(String s, Integer i, Double d) {
    return false;
}
```
 Enabled level      | Log sample
--------------------|------------
 `TRACE` `DEBUG`    | `DEBUG [] r.t.e.e.Example.verboseDisabled >`<br>`DEBUG [] r.t.e.e.Example.verboseDisabled <`
 `INFO` .. `OFF`    | -

#### Try to print arguments by `JacksonPrinter` as `JSON`
You can specify printer's bean name or alias. Arguments and return values will be serialized with `#toString()` invocation by default. 
```java
@Log(printer = "jacksonPrinter")
void verboseJson(Dto dto, Integer i) {
}
```
 Enabled level      | Log sample
--------------------|------------
 `TRACE` `DEBUG`    | `DEBUG [] r.t.e.example.Example.verboseJson > dto={"i":0,"s":null}, i=8`<br>`DEBUG [] r.t.e.example.Example.verboseJson <`
 `INFO` .. `OFF`    | -

#### Try to print arguments by `Jaxb2Printer` as `XML`
You can specify printer's bean name or alias.
```java
@Log(printer = "jaxb2Printer")
void verboseXml(Dto dto, Integer i) {
}
```
 Enabled level      | Log sample
--------------------|------------
 `TRACE` `DEBUG`    | `DEBUG [] r.t.e.example.Example.verboseXml > dto=<dto><i>0</i></dto>, i=7`<br>`DEBUG [] r.t.e.example.Example.verboseXml <`
 `INFO` .. `OFF`    | -

#### Separate `in` and `out` events 
Logging of `in` and `out` events could be declared separately with own settings.
```java
@Log.in(level = INFO)
@Log.out
void inOut(Dto dto, String s, Integer i) {
}
```
 Enabled level      | Log sample
--------------------|------------
 `TRACE`            | `INFO  [] r.t.eclair.example.Example.inOut > dto=Dto{i=0, s='null'}, s="s", i=3`<br>`TRACE [] r.t.eclair.example.Example.inOut <`
 `DEBUG`            | `INFO  [] r.t.eclair.example.Example.inOut > dto=Dto{i=0, s='null'}, s="s", i=3`
 `INFO`             | `INFO  [] r.t.eclair.example.Example.inOut >`
 `WARN` .. `OFF`    | -

#### Error 
```java
@Log.error
void error() {
    throw new RuntimeException("Something strange happened");
}
```
 Enabled level      | Log sample
--------------------|------------
 `TRACE` .. `FATAL` | `ERROR [] r.t.eclair.example.Example.error ! java.lang.RuntimeException: Something strange happened`<br>`java.lang.RuntimeException: Something strange happened`<br>`	at ru.tinkoff.eclair.example.Example.error(Example.java:74)`<br>`..`
 `OFF`              | -

#### Configured parameter levels
```java
@Log.in(INFO)
void parameterLevels(@Log(INFO) Double d,
                     @Log(DEBUG) String s,
                     @Log(TRACE) Integer i) {
}
```
 Enabled level      | Log sample
--------------------|------------
 `TRACE`            | `INFO  [] r.t.e.e.Example.parameterLevels > d=0.0, s="s", i=0`
 `DEBUG`            | `INFO  [] r.t.e.e.Example.parameterLevels > d=0.0, s="s"`
 `INFO`             | `INFO  [] r.t.e.e.Example.parameterLevels > 0.0`
 `WARN` .. `OFF`    | -