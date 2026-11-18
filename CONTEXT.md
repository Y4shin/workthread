# workthread

Workthread is a Java application. The work logic lives in the core. A surface
reaches it in the same process, or through the daemon's API when the work logic
runs elsewhere. The surfaces are the command line interface, the MCP server,
the client, and the tray.

## Language

**Daemon**:
The process that holds the work logic and runs without a user interface.
_Avoid_: backend, server, engine

**Client**:
The JavaFX surface the user interacts with.
_Avoid_: frontend, UI, app

**Standalone mode**:
An arrangement where a surface reaches the work logic in the same process, with
no API between them.
_Avoid_: single-process, embedded, local mode

**Split mode**:
An arrangement where a surface reaches the work logic in another process,
through the API.
_Avoid_: multi-process, remote, server mode

### Architecture

**Core**:
The module holding the work logic: the domain model, the use cases, and the
surface ports they implement. It depends on no framework, and on no module but
the one holding the port interfaces.
_Avoid_: engine, business logic, backend

**Surface**:
A driving adapter that exposes the work logic to something outside the core:
the command line interface, the MCP server, the client, the tray, and in split
mode the API listener that serves them.
_Avoid_: UI, frontend, interface

**Surface port**:
An interface on the core's surface side. The core implements it and a surface
calls it, and one surface port carries the use-case operations of one area.
This is Cockburn's driving port, which the 2005 article also calls the primary
port; neither of those names is used here.
_Avoid_: driving port, primary port, input port, inbound port, API, facade,
service interface

**Infrastructure port**:
An interface on the core's infrastructure side. The core calls it and an
adapter outside the core implements it. A built-in implementation may ship with
the product, in which case it stands behind the same port so an outside
implementation can replace it.
This is Cockburn's driven port, which the 2005 article also calls the secondary
port; neither of those names is used here.
_Avoid_: driven port, secondary port, output port, outbound port, repository,
gateway

**Adapter**:
A component outside the core that implements an infrastructure port: the SQLite
store, the system clock, the change log writer.
_Avoid_: connector, plugin, integration

**Remote proxy**:
An implementation of the surface ports that forwards each call to the daemon
over the API, so a surface can run without the core in its own process.
_Avoid_: shim, stub, client stub, thin client
