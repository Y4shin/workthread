# workthread

Workthread is a Java application. A JavaFX client is the interface, and the
work logic runs in a daemon that the client can reach in two ways: in one
standalone process with no API between them, or as separate processes with a
Spring Boot API carrying the calls between daemon and client.

## Language

**Daemon**:
The process that holds the work logic and runs without a user interface.
_Avoid_: backend, server, engine

**Client**:
The JavaFX process the user interacts with.
_Avoid_: frontend, UI, app

**Standalone mode**:
One process that runs the daemon and the client together, with no API between
them.
_Avoid_: single-process, embedded, local mode

**Split mode**:
The daemon and the client run as separate processes, and a Spring Boot API
carries the calls between them.
_Avoid_: multi-process, remote, server mode

_No further domain terms are resolved yet. Add a term the moment it settles and
keep the definitions above in the same shape: what the thing is, not what it
does._
