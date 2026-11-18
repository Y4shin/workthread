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

**Note**:
A timestamped piece of text appended to a task. A task has zero or more, they
are read in the order they were written, and a note is removed rather than
edited, with the removal kept in the history.
_Avoid_: annotation, comment, description, memo

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

### Capacity and the working month

**Timetable**:
The net work minutes held by each weekday, one window per weekday. Zero minutes
means a non-working day.
_Avoid_: working hours, availability, weekly schedule

**Planned start**:
The configured clock time at which a day is expected to begin. The observed
start replaces it once the day is actually started, so it is a default and not
a commitment.
_Avoid_: usual start, default start

**Session**:
The record of one working day: when it was started, its leave time, and its
break reserve. A day with planned work that has not been started is not a
session.
_Avoid_: workday, shift, day

**Leave time**:
The instant a working day is planned to end, derived from the start and the
day's minutes and moved at any moment during the day.
_Avoid_: stop time, end time, quitting time

**Stop time**:
The instant a working day actually ended, entered by the user or inferred when
the day was left open. Distinct from the leave time, which is the plan.
_Avoid_: leave time, end time

**Break reserve**:
Minutes set aside for breaks within a day. They are subtracted from the day's
window both for planning and for the month's account, and they are never
tracked: a long break slips the plan instead of being recorded.
_Avoid_: pause budget, break budget, allowance

**Target**:
The minutes a month owes: the sum of its weekdays' timetable minutes, minus the
minutes of vacation recorded on those dates.
_Avoid_: quota, requirement, nominal hours

**Balance**:
Minutes of presence worked in a month minus that month's target, running inside
the month and followed by the plan.
_Avoid_: overtime, flextime account, saldo

**Presence**:
The minutes between a session's start and its stop time, minus the break
reserve. Presence is what the month's account measures; task time is not.
_Avoid_: working time, attendance, logged time

**Vacation**:
A per-date entry that reduces both that day's working minutes and the month's
target, so the hours are not owed.
_Avoid_: leave, time off, holiday

**Flex**:
A per-date entry that sets that day's working minutes in either direction and
leaves the month's target alone, so the difference is compensated on other days
of the month. Zero minutes means a day away with the hours still owed.
_Avoid_: time off, out-of-office, adjustment

**Reconciliation**:
The plan's adjustment of the days after today, in fixed increments, to bring
the month's balance inside its target band.
_Avoid_: catch-up, compensation, balancing

**Notice**:
Something the system reports. A persistent notice lasts while its condition
holds, a one-off notice until it is dismissed, and each notice carries a level
of info or warning.
_Avoid_: warning (that is the level), notification (that is delivery to the
operating system), alert, message
