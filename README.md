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

License
-------
EPL 1.0, See [LICENSE](LICENSE) file.