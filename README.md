# Java Debug Server for Visual Studio Code

## Overview

The Java Debug Server is an implementation of Visual Studio Code (VSCode) Debug Protocol. It can be used in Visual Studio Code to debug Java programs.

## Features
- Launch/Attach
- Breakpoints
- Exceptions
- Pause & Continue
- Step In/Out/Over
- Variables
- Callstacks
- Threads
- Debug console

## Background

The Java Debug Server is the bridge between VSCode and JVM. The implementation is based on JDI ([Java Debug Interface](https://docs.oracle.com/javase/7/docs/jdk/api/jpda/jdi/)). It works with [Eclipse JDT Language Server](https://github.com/vscjavaci/eclipse.jdt.ls) as an add-on to provide debug functionalities.

## Repository Structure

- com.microsoft.java.debug.core - the core logic of the debug server
- com.microsoft.java.debug.plugin - wraps the debug server into an Eclipse plugin to work with Eclipse JDT Language Server

## Installation

### Windows:
```
mvnw.cmd clean install
```
### Linux and macOS:
```
./mvnw clean install
```


## Usage with eclipse.jdt.ls

To use `java-debug` as a [jdt.ls](https://github.com/eclipse/eclipse.jdt.ls) plugin, an [LSP client](https://langserver.org/) has to launch [jdt.ls](https://github.com/eclipse/eclipse.jdt.ls) with `initializationOptions` that contain the path to the built `java-debug` jar within a `bundles` array:


```
{
    "initializationOptions": {
        "bundles": [
            "path/to/microsoft/java-debug/com.microsoft.java.debug.plugin/target/com.microsoft.java.debug.plugin-<version>.jar"
        ]
    }
}
```

Editor extensions like [vscode-java](https://github.com/redhat-developer/vscode-java) take care of this.


Once `eclipse.jdt.ls` launched, the client can send a [Command](https://microsoft.github.io/language-server-protocol/specifications/specification-current/#command) to the server to start a debug session:

```
{
  "command": "vscode.java.startDebugSession"
}
```

The response to this request will contain a port number on which the debug adapter is listening, and to which a client implementing the debug-adapter protocol can connect to.


## Debug Configuration

### Launch settings

`java-debug` supports the following configurations for `type` `launch`:

| Property           | Type                  | Description
| ---                | ---                   | ---
| projectName        | String                | Name of the project. This is important if using a multi-module setup with Maven/Gradle.
| mainClass          | String                | Name of the main class to run
| args               | String                | Arguments to pass to the main function as string
| vmArgs             | String                | Arguments for the JVM
| classPaths         | String[]              | JVM class paths
| modulePaths        | String[]              | JVM module paths
| cwd                | String                | Working directory the application should be run under
| env                | `Map<String, String>` | environment variables
| stopOnEntry        | boolean               | If `true` the debugger stops when the application starts
| noDebug            | boolean               | If `true` the application starts without debugging enabled, ignoring any breakpoints.
| console            | CONSOLE               | One of `internalConsole`, `integratedTerminal` and `externalTerminal`
| shortenCommandLine | shortenCommandLine    | One of `none`, `jarmanifest` and `argfile`
| launcherScript     | String                |
| javaExec           | String                | Path to the java executable to use to run the application
| stepFilters        | StepFilers            | Table with `skipSynthetics`, `skipStaticInitializers` and `skipConstructors` boolean values

Some editor integrations automatically add some of the values. For example with `vscode-java` or `nvim-jdtls` it is usually not necessary to set the `classPaths`, `modulePaths` or `javaExec` manually.

### Attach configuration

`java-debug` supports the following configurations for `type` `attach`:

| Property    | Type       | Description
| ---         | ---        | ---
| projectName | String     | Name of the project. This is important if using a multi-module setup with Maven/Gradle.
| hostName    | String     | Hostname of the host to connect to
| port        | int        | Port to connect to
| timeout     | int        | Connection timeout. Defaults to 30 seconds
| stepFilters | StepFilers | Table with `skipSynthetics`, `skipStaticInitializers` and `skipConstructors` boolean values


License
-------
EPL 1.0, See [LICENSE](LICENSE.txt) file.
