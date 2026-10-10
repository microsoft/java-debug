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


## IssueLens team-memory maintenance

[The source workflow](.github/workflows/team-memory-post-merge.yml) queues eligible
default-branch pushes through the `team-memory-coordinator.yml` workflow on `main`
in `microsoft/vscode-java-pack`. The coordinator owns source validation, the
shared-wiki queue, and maintenance; issue triage is unchanged.

Before merging this migration with the existing
`ISSUELENS_TEAM_MEMORY_ENABLED` variable set to `true`, configure both independent
authentication paths. An enabled source switches immediately from direct
maintenance to queue dispatch:

- **Source dispatch:** set the repository variable `ISSUELENS_DISPATCH_APP_CLIENT_ID`
  and secret `ISSUELENS_DISPATCH_APP_PRIVATE_KEY` for a dedicated GitHub App
  installed only on `microsoft/vscode-java-pack`, with Contents read and Actions
  write permissions. The pinned token action accepts the client ID through its
  `client-id` input and narrows the token to that repository and those permissions.
  The source `GITHUB_TOKEN` cannot dispatch across repositories. Do not reuse the
  hosted IssueLens App key or the central source-read App credentials.
- **Central source reads:** Java Pack must separately configure the secrets
  `ISSUELENS_SOURCE_READ_APP_CLIENT_ID` and `ISSUELENS_SOURCE_READ_APP_PRIVATE_KEY`
  for its source-read App, with Actions, Contents, and Pull requests read access
  to selected source repositories including `microsoft/java-debug`. A successful
  coordinator run for Java Pack itself does not verify this external access.

Only ordinary, non-created, non-deleted, non-forced pushes from the actual source
repository/default branch with matching workflow and head SHAs are queued.
The five string inputs identify the source run/attempt and the requested commit
range. `push_after` is verified against the run; `push_before` authorizes ancestor
reconciliation, not attested original-event provenance.

For manual merged-PR maintenance, use **Run workflow** on the central coordinator
with `source_repository: microsoft/java-debug` and `pull_request_number`; there
is no local manual invocation path. Dispatch acceptance is not maintenance
completion. Inspect central runs before retrying an ambiguous dispatch failure.


License
-------
EPL 1.0, See [LICENSE](LICENSE.txt) file.
