---
type: findings
title: "Prototype: ordering engine against the timetable and session model"
status: stable
source_task: ordering-engine
---

# Does the timetable-and-session model still order work sensibly?

A throwaway prototype, built outside the repository, for
`planner-v1/tasks/prototype-ordering-engine`.

- Source: `/tmp/planner-v1-ordering/OrderingPrototype.java` (one self-contained class, no dependencies)
- Full output: `/tmp/planner-v1-ordering/run.txt` (also reproduced in full below)
- Build and run:

```
cd /tmp/planner-v1-ordering
javac -d out OrderingPrototype.java
java -cp out OrderingPrototype              # all three rules, per case
java -cp out OrderingPrototype --rule=B     # one rule only
```

Nothing in the repository was modified and nothing here has been deleted yet.

## The short answer

The ordering survives the model change in the cases where the work is
deadline-driven and self-contained: rule A reproduces the specification's EX-1
itinerary minute for minute, and it is the only rule that meets every deadline
in `chain`, the case built to make the three rules disagree. The model does *not* survive in the
one place the specification was explicitly careful: a prerequisite with no
deadline of its own. All three rules sink that prerequisite below any
deadline-bearing task, and the successor misses an 11:00 deadline by 30 minutes
in every one of them (case `ex2`). That is a gap in the *criteria*, not in the
capacity model: the timetable and the session reproduce the specification's
arithmetic exactly, and the fit signal is honest about where the session ends.

## Model decisions the brief left open, and what I picked

The brief specifies the model but not these details. Each is a deliberate,
single-sentence decision so the comparison is reproducible.

1. **Timetable values are net working minutes per weekday.** The planned break
   is subtracted only from today's session, because the brief defines the break
   as part of the session. Days with 0 minutes are skipped.
2. **A forecast day starts at 09:00.** A future bucket is `[09:00, 09:00 + minutes)`.
   Nothing in the model gives a future day a start time; 09:00 is the placeholder.
3. **The horizon is 14 days including today**: forecasts cover today plus the
   next thirteen calendar days, and work that does not fit is reported as
   unscheduled only beyond that.
4. **The session is a hard boundary.** A chunk is
   `min(unallocated effort, 60 minutes, remaining session capacity)`, so a task
   never crosses the planned leave time and may receive a fragment shorter than
   its remaining effort. This is the specification's RULE-ALLOC-1.
5. **The five minute completion slot is only for zero remaining effort**, not
   for a task that has just been fully allocated. It is placed when the task is
   next selected, and it waits for a bucket with at least five minutes left
   (RULE-ALLOC-3). A task with positive remaining effort that is fully allocated
   is simply done.
6. **Dependency pressure** (rule A's second criterion) is the least static slack
   among the deadline-bearing descendants a task unlocks:
   `slack(d) = capacity(now, d.effectiveDeadline) - staticClosureEffort(d)`,
   lower is more pressure, `Integer.MAX_VALUE` if the task unlocks nothing with a
   deadline. Slack here is *static*: computed from the case's effort, not from
   the effort left at the moment of selection.
7. **Dependency depth** (rule C's first criterion) is the number of distinct
   unfinished transitive prerequisites, so a task with no prerequisites is depth
   0 and sorts first.
8. **Effective deadline** is the earlier of the task's own deadline and the
   deadline of its project when that project is active, else null.
9. **Eligibility** is exactly the brief's list: no inbox tasks, no task without
   a positive estimate, no task in a paused/completed/archived project, no task
   whose prerequisites are unfinished. A cancelled or missing prerequisite makes
   the path blocked rather than schedulable; missing estimates and paused
   projects are never overridable.
10. The task creation order is the position in the case's task list, and the
    stable identifier is the last tie-break, so the order is total.

Adaptations made to the worked examples are printed in each case's header and
repeated below. In short: the specification's availability interval becomes a
session with `now` = the interval start and `leave` = the interval end, break 0
(except where a case tests the break), and the specification's tight/missed
slack tiers are deliberately *not* implemented, because the three rules replace
them.

## The three rules, as code

All three are the same list of criteria in different orders; `stable` is
`createdIndex` then `id`, so every rule is a total order. The decisive criterion
is recomputed against the runner-up so each printed item can say what decided it.

```java
Comparator<Task> comparator(Rule rule) {
    Comparator<Task> deadline = this::cmpDeadline;
    Comparator<Task> taskPriority = Comparator.comparingInt(Task::priority);
    Comparator<Task> pressure = Comparator.comparingInt(this::pressure);
    Comparator<Task> projectPriority = this::cmpProjectPriority;
    Comparator<Task> depth = Comparator.comparingInt(this::depth);
    Comparator<Task> stable =
            Comparator.comparingInt(Task::createdIndex).thenComparing(Task::id);
    return switch (rule) {
        case A -> deadline.thenComparing(pressure).thenComparing(taskPriority)
                .thenComparing(projectPriority).thenComparing(stable);
        case B -> taskPriority.thenComparing(deadline).thenComparing(pressure)
                .thenComparing(stable);
        case C -> depth.thenComparing(deadline).thenComparing(taskPriority)
                .thenComparing(stable);
    };
}
```

```java
static String decisive(Rule rule, World w, Task a, Task b) {
    if (b == null) return "only eligible task";
    return switch (rule) {
        case A -> {
            if (w.cmpDeadline(a, b) != 0) yield "effective deadline";
            if (w.pressure(a) != w.pressure(b)) yield "dependency pressure";
            if (a.priority() != b.priority()) yield "task priority";
            if (w.cmpProjectPriority(a, b) != 0) yield "project priority";
            yield "creation order";
        }
        case B -> {
            if (a.priority() != b.priority()) yield "task priority";
            if (w.cmpDeadline(a, b) != 0) yield "effective deadline";
            if (w.pressure(a) != w.pressure(b)) yield "dependency pressure";
            yield "creation order";
        }
        case C -> {
            if (w.depth(a) != w.depth(b)) yield "dependency depth";
            if (w.cmpDeadline(a, b) != 0) yield "effective deadline";
            if (a.priority() != b.priority()) yield "task priority";
            yield "creation order";
        }
    };
}
```

Effective deadline, the first criterion of two rules:

```java
LocalDateTime effDeadline(Task t) {
    LocalDateTime d = t.deadline();
    Project p = project(t.projectId());
    if (p != null && p.status() == ProjectStatus.active && p.deadline() != null) {
        if (d == null || p.deadline().isBefore(d)) d = p.deadline();
    }
    return d;
}
```

The session boundary and the completion slot:

```java
int unalloc = w.remaining(t) - r.allocated.getOrDefault(t.id(), 0);
int chunk;
if (w.remaining(t) > 0) {
    chunk = Math.min(Math.min(unalloc, MAX_CHUNK), cap);
} else {
    // Zero remaining effort (logged work covers the estimate): five minute slot.
    if (cap < COMPLETION_SLOT) break;
    chunk = COMPLETION_SLOT;
}
r.items.add(new Item(t.id(), cursor, chunk, b.dayIndex(), why));
cursor = cursor.plusMinutes(chunk);
cap -= chunk;
if (w.remaining(t) > 0) {
    int placed = r.allocated.merge(t.id(), chunk, Integer::sum);
    if (placed >= w.remaining(t)) r.done.add(t.id());
}
```

## The cases, verbatim

The case definitions from `cases()`. `week(mon, tue, wed, thu, fri, sat, sun)`
is the timetable; `at(dayOfJune, hour, minute)` is a June 2025 instant, so
`at(2, 9, 0)` is Monday 02 June 2025 09:00.

```java
static List<Case> cases() {
    List<Case> cases = new ArrayList<>();
    COUNTER = 0;

    // EX-1, adapted.
    Map<DayOfWeek, Integer> standardWeek = week(300, 300, 240, 240, 300, 0, 0);
    cases.add(new Case(
            "ex1",
            "EX-1 (spec 5.19) with the spec's 'Monday 09:00 to 14:00 availability' turned into a "
                    + "session: now Monday 09:00, planned leave 14:00, planned break 0, so today's "
                    + "session is 300 min; the same 300 min appears as Monday's timetable value so "
                    + "later Mondays match. The spec's deadline-pressure/tight-slack tiers are NOT "
                    + "implemented here; the three literal ordering rules replace them. The horizon "
                    + "is 14 days, so work that does not fit today finishes tomorrow.",
            at(2, 9, 0), at(2, 14, 0), 0, standardWeek,
            List.of(new Project("Atlas", "Project Atlas", at(2, 11, 0), 3, ProjectStatus.active)),
            List.of(
                    task("A", "Cut the Atlas release branch", "Atlas", 105, 0, 5, null, List.of()),
                    task("B", "Restore the broken checkout", null, 120, 0, 1, at(2, 17, 0), List.of()),
                    task("C", "Write the weekly update", null, 60, 0, 3, at(2, 17, 0), List.of()))));

    // EX-2, adapted.
    cases.add(new Case(
            "ex2",
            "EX-2 (spec 5.19) with 'Tuesday 09:00 to 12:00 availability' turned into a session: now "
                    + "Tuesday 09:00, planned leave 12:00, planned break 0, so today's session is 180 "
                    + "min. The spec schedules P first because it is a prerequisite of a "
                    + "deadline-bearing deliverable (its 'dependency-protection' tier); the settled "
                    + "model has no such tier, so watch what the three rules do with P.",
            at(3, 9, 0), at(3, 12, 0), 0, standardWeek,
            List.of(new Project("Atlas", "Project Atlas", at(3, 11, 0), 3, ProjectStatus.active)),
            List.of(
                    task("P", "Provision the Atlas staging database", null, 30, 0, 5, null, List.of()),
                    task("R", "Answer the security questionnaire", null, 60, 0, 1, at(3, 12, 0), List.of()),
                    task("S", "Load the Atlas dataset", "Atlas", 60, 0, 5, null, List.of("P")))));

    // Hand-made: dependency chain with a near deadline.
    cases.add(new Case(
            "chain",
            "hand-made: a three-step migration chain due at noon today behind a project deadline, "
                    + "competing with a critical one-off due 17:00 and a critical request due "
                    + "tomorrow noon. Today's session is 09:00 to 12:00 (180 min) minus a 30 min "
                    + "planned break = 150 min; the chain needs 120, the one-offs 75, so 45 min of "
                    + "the day's work has to spill into tomorrow.",
            at(4, 9, 0), at(4, 12, 0), 30, standardWeek,
            List.of(new Project("Launch", "Launch the new billing flow", at(4, 12, 0), 2,
                    ProjectStatus.active)),
            List.of(
                    task("L1", "Write the migration script", "Launch", 60, 0, 3, null, List.of()),
                    task("L2", "Dry-run the migration in staging", "Launch", 30, 0, 3, null,
                            List.of("L1")),
                    task("L3", "Verify row counts", "Launch", 30, 0, 3, null, List.of("L2")),
                    task("Y", "Fix the broken login", null, 30, 0, 1, at(4, 17, 0), List.of()),
                    task("X", "Answer the vendor", null, 45, 0, 1, at(5, 12, 0), List.of()))));

    // Hand-made: nothing fits today.
    cases.add(new Case(
            "nothing",
            "hand-made: the day is nearly over. Now Thursday 16:30, planned leave 17:00, planned "
                    + "break 15 min, so today's session is 15 min. Every task needs more than 15 min, "
                    + "so nothing can finish today; the question is what the rules put in the 15 min "
                    + "sliver and whether that reads as a plan. D is an inbox task and E belongs to a "
                    + "paused project, so neither may appear anywhere.",
            at(5, 16, 30), at(5, 17, 0), 15, standardWeek,
            List.of(
                    new Project("Atlas", "Project Atlas", null, 3, ProjectStatus.active),
                    new Project("Onboarding", "Onboarding rework", null, 2, ProjectStatus.paused)),
            List.of(
                    task("A", "Draft the launch email", "Atlas", 45, 0, 2, at(6, 12, 0), List.of()),
                    task("C", "Post the standup notes", null, 20, 0, 1, at(6, 17, 0), List.of()),
                    task("B", "Tidy the backlog labels", null, 90, 0, 4, null, List.of()),
                    task("D", "Buy milk", null, 30, 0, 1, null, List.of(), Status.inbox),
                    task("E", "Rewrite the welcome tour", "Onboarding", 60, 0, 1, null, List.of()))));

    // Supplementary: EX-3 part A, adapted.
    cases.add(new Case(
            "ex3a",
            "supplementary, adapted from EX-3 part A: Thursday 09:00 to 11:00 becomes a session of "
                    + "120 min. The spec says the successor gets no projected finish in the horizon "
                    + "and is missed; with the 14-day forecast horizon it now gets a real finish "
                    + "(Friday) that is still after its deadline. Same risk label, better reason.",
            at(5, 9, 0), at(5, 11, 0), 0, standardWeek,
            List.of(new Project("Atlas", "Project Atlas", at(5, 11, 0), 3, ProjectStatus.active)),
            List.of(
                    task("P", "Backfill the Atlas snapshot", null, 60, 0, 3, null, List.of()),
                    task("S", "Swap the Atlas read path", "Atlas", 120, 0, 3, null, List.of("P")))));

    // Supplementary: EX-3 part B, adapted: the five-minute completion slot.
    cases.add(new Case(
            "ex3b",
            "supplementary, adapted from EX-3 part B: now Friday 09:00, planned leave 10:00, planned "
                    + "break 0, so today's session is 60 min. T has estimate 60 and 75 minutes logged, "
                    + "so remaining effort is 0: the model must give it a five minute completion slot "
                    + "and report the 15 minute overrun without changing the estimate.",
            at(6, 9, 0), at(6, 10, 0), 0, standardWeek,
            List.of(),
            List.of(task("T", "Ship the Atlas hotfix", null, 60, 75, 3, at(6, 10, 0), List.of()))));

    return cases;
}
```

### The four required cases

| case | what it is | now / leave / break | today's session |
| --- | --- | --- | --- |
| `ex1` | adapted EX-1: a project deadline at 11:00 against two 17:00 tasks | Mon 09:00 / 14:00 / 0 | 300 min |
| `ex2` | adapted EX-2: a deadline-less prerequisite of a 11:00 deliverable | Tue 09:00 / 12:00 / 0 | 180 min |
| `chain` | hand-made: three-step chain due 12:00 today vs two critical one-offs | Wed 09:00 / 12:00 / 30 | 150 min |
| `nothing` | hand-made: nothing fits today (15 min left, every task over 15 min) | Thu 16:30 / 17:00 / 15 | 15 min |

Two supplementary cases were added because the settled model has rules the four
required cases never exercise; both come from the same section of the
specification.

| case | what it is | now / leave / break | today's session |
| --- | --- | --- | --- |
| `ex3a` | adapted EX-3 part A: a missed project deadline under a 14-day horizon | Thu 09:00 / 11:00 / 0 | 120 min |
| `ex3b` | adapted EX-3 part B: estimate 60, logged 75, so a five minute slot | Fri 09:00 / 10:00 / 0 | 60 min |

What changed in each worked example, in one line each:

- `ex1`: the spec's "Monday 09:00 to 14:00 availability" becomes a session of
  300 minutes; Monday's timetable value is also 300 so later Mondays match. The
  spec's slack tiers are replaced by the three rules, and the 14-day horizon
  means overflow finishes tomorrow instead of vanishing.
- `ex2`: "Tuesday 09:00 to 12:00" becomes a 180-minute session. The spec puts
  `P` first through its dependency-protection tier; the settled model has no
  such tier, and that is exactly what this case is here to show.
- `ex3a`: "Thursday 09:00 to 11:00" becomes a 120-minute session. The spec says
  the successor has no projected finish in the horizon; with 14 days it now gets
  a real finish (Friday 10:00) that is still after its deadline.
- `ex3b`: "Friday 09:00 to 10:00" becomes a 60-minute session; the retroactive
  overrun is baked in, so remaining effort is 0 from the start.

## Full output

```

======================================================================
CASE ex1
adaptation: EX-1 (spec 5.19) with the spec's 'Monday 09:00 to 14:00 availability' turned into a session: now Monday 09:00, planned leave 14:00, planned break 0, so today's session is 300 min; the same 300 min appears as Monday's timetable value so later Mondays match. The spec's deadline-pressure/tight-slack tiers are NOT implemented here; the three literal ordering rules replace them. The horizon is 14 days, so work that does not fit today finishes tomorrow.
now Mon 02 Jun 09:00, planned leave 14:00, break 0 min -> today's session 300 min
timetable (net minutes per weekday, workday starts 09:00): {MONDAY=300, TUESDAY=300, WEDNESDAY=240, THURSDAY=240, FRIDAY=300, SATURDAY=0, SUNDAY=0}
projects: [Project[id=Atlas, title=Project Atlas, deadline=2025-06-02T11:00, priority=3, status=active]]
tasks:
  A  Cut the Atlas release branch       est 105  logged 0   prio 5 dep []         ready project Atlas
  B  Restore the broken checkout        est 120  logged 0   prio 1 dep []         ready own deadline Mon 02 Jun 17:00
  C  Write the weekly update            est 60   logged 0   prio 3 dep []         ready own deadline Mon 02 Jun 17:00
ineligible under the normal rules:
  (none)

--- rule A: effective deadline, dependency pressure, task priority, project priority, creation order
    09:00-10:00  A  Cut the Atlas release branch        60m  [effective deadline]
    10:00-10:45  A  Cut the Atlas release branch        45m  [effective deadline]
    10:45-11:45  B  Restore the broken checkout         60m  [task priority]
    11:45-12:45  B  Restore the broken checkout         60m  [task priority]
    12:45-13:45  C  Write the weekly update             60m  [only eligible task]
    session: 285 of 300 min used; runs out at 13:45 (15 min unused, no eligible work left)
    first pick: A
    forecast:
      A  Cut the Atlas release branch       finish Mon 02 Jun 10:45  remaining   0 min  on time
      B  Restore the broken checkout        finish Mon 02 Jun 12:45  remaining   0 min  on time
      C  Write the weekly update            finish Mon 02 Jun 13:45  remaining   0 min  on time

--- rule B: task priority, effective deadline, dependency pressure
    09:00-10:00  B  Restore the broken checkout         60m  [task priority]
    10:00-11:00  B  Restore the broken checkout         60m  [task priority]
    11:00-12:00  C  Write the weekly update             60m  [task priority]
    12:00-13:00  A  Cut the Atlas release branch        60m  [only eligible task]
    13:00-13:45  A  Cut the Atlas release branch        45m  [only eligible task]
    session: 285 of 300 min used; runs out at 13:45 (15 min unused, no eligible work left)
    first pick: B
    forecast:
      A  Cut the Atlas release branch       finish Mon 02 Jun 13:45  remaining   0 min  LATE past Mon 02 Jun 11:00
      B  Restore the broken checkout        finish Mon 02 Jun 11:00  remaining   0 min  on time
      C  Write the weekly update            finish Mon 02 Jun 12:00  remaining   0 min  on time

--- rule C: dependency depth, effective deadline, task priority
    09:00-10:00  A  Cut the Atlas release branch        60m  [effective deadline]
    10:00-10:45  A  Cut the Atlas release branch        45m  [effective deadline]
    10:45-11:45  B  Restore the broken checkout         60m  [task priority]
    11:45-12:45  B  Restore the broken checkout         60m  [task priority]
    12:45-13:45  C  Write the weekly update             60m  [only eligible task]
    session: 285 of 300 min used; runs out at 13:45 (15 min unused, no eligible work left)
    first pick: A
    forecast:
      A  Cut the Atlas release branch       finish Mon 02 Jun 10:45  remaining   0 min  on time
      B  Restore the broken checkout        finish Mon 02 Jun 12:45  remaining   0 min  on time
      C  Write the weekly update            finish Mon 02 Jun 13:45  remaining   0 min  on time


======================================================================
CASE ex2
adaptation: EX-2 (spec 5.19) with 'Tuesday 09:00 to 12:00 availability' turned into a session: now Tuesday 09:00, planned leave 12:00, planned break 0, so today's session is 180 min. The spec schedules P first because it is a prerequisite of a deadline-bearing deliverable (its 'dependency-protection' tier); the settled model has no such tier, so watch what the three rules do with P.
now Tue 03 Jun 09:00, planned leave 12:00, break 0 min -> today's session 180 min
timetable (net minutes per weekday, workday starts 09:00): {MONDAY=300, TUESDAY=300, WEDNESDAY=240, THURSDAY=240, FRIDAY=300, SATURDAY=0, SUNDAY=0}
projects: [Project[id=Atlas, title=Project Atlas, deadline=2025-06-03T11:00, priority=3, status=active]]
tasks:
  P  Provision the Atlas staging database est 30   logged 0   prio 5 dep []         ready
  R  Answer the security questionnaire  est 60   logged 0   prio 1 dep []         ready own deadline Tue 03 Jun 12:00
  S  Load the Atlas dataset             est 60   logged 0   prio 5 dep [P]        ready project Atlas
ineligible under the normal rules:
  (none)

--- rule A: effective deadline, dependency pressure, task priority, project priority, creation order
    09:00-10:00  R  Answer the security questionnaire   60m  [effective deadline]
    10:00-10:30  P  Provision the Atlas staging database  30m  [only eligible task]
    10:30-11:30  S  Load the Atlas dataset              60m  [only eligible task]
    session: 150 of 180 min used; runs out at 11:30 (30 min unused, no eligible work left)
    first pick: R
    forecast:
      P  Provision the Atlas staging database finish Tue 03 Jun 10:30  remaining   0 min  on track (no deadline)
      R  Answer the security questionnaire  finish Tue 03 Jun 10:00  remaining   0 min  on time
      S  Load the Atlas dataset             finish Tue 03 Jun 11:30  remaining   0 min  LATE past Tue 03 Jun 11:00

--- rule B: task priority, effective deadline, dependency pressure
    09:00-10:00  R  Answer the security questionnaire   60m  [task priority]
    10:00-10:30  P  Provision the Atlas staging database  30m  [only eligible task]
    10:30-11:30  S  Load the Atlas dataset              60m  [only eligible task]
    session: 150 of 180 min used; runs out at 11:30 (30 min unused, no eligible work left)
    first pick: R
    forecast:
      P  Provision the Atlas staging database finish Tue 03 Jun 10:30  remaining   0 min  on track (no deadline)
      R  Answer the security questionnaire  finish Tue 03 Jun 10:00  remaining   0 min  on time
      S  Load the Atlas dataset             finish Tue 03 Jun 11:30  remaining   0 min  LATE past Tue 03 Jun 11:00

--- rule C: dependency depth, effective deadline, task priority
    09:00-10:00  R  Answer the security questionnaire   60m  [effective deadline]
    10:00-10:30  P  Provision the Atlas staging database  30m  [only eligible task]
    10:30-11:30  S  Load the Atlas dataset              60m  [only eligible task]
    session: 150 of 180 min used; runs out at 11:30 (30 min unused, no eligible work left)
    first pick: R
    forecast:
      P  Provision the Atlas staging database finish Tue 03 Jun 10:30  remaining   0 min  on track (no deadline)
      R  Answer the security questionnaire  finish Tue 03 Jun 10:00  remaining   0 min  on time
      S  Load the Atlas dataset             finish Tue 03 Jun 11:30  remaining   0 min  LATE past Tue 03 Jun 11:00


======================================================================
CASE chain
adaptation: hand-made: a three-step migration chain due at noon today behind a project deadline, competing with a critical one-off due 17:00 and a critical request due tomorrow noon. Today's session is 09:00 to 12:00 (180 min) minus a 30 min planned break = 150 min; the chain needs 120, the one-offs 75, so 45 min of the day's work has to spill into tomorrow.
now Wed 04 Jun 09:00, planned leave 12:00, break 30 min -> today's session 150 min
timetable (net minutes per weekday, workday starts 09:00): {MONDAY=300, TUESDAY=300, WEDNESDAY=240, THURSDAY=240, FRIDAY=300, SATURDAY=0, SUNDAY=0}
projects: [Project[id=Launch, title=Launch the new billing flow, deadline=2025-06-04T12:00, priority=2, status=active]]
tasks:
  L1 Write the migration script         est 60   logged 0   prio 3 dep []         ready project Launch
  L2 Dry-run the migration in staging   est 30   logged 0   prio 3 dep [L1]       ready project Launch
  L3 Verify row counts                  est 30   logged 0   prio 3 dep [L2]       ready project Launch
  Y  Fix the broken login               est 30   logged 0   prio 1 dep []         ready own deadline Wed 04 Jun 17:00
  X  Answer the vendor                  est 45   logged 0   prio 1 dep []         ready own deadline Thu 05 Jun 12:00
ineligible under the normal rules:
  (none)

--- rule A: effective deadline, dependency pressure, task priority, project priority, creation order
    09:00-10:00  L1 Write the migration script          60m  [effective deadline]
    10:00-10:30  L2 Dry-run the migration in staging    30m  [effective deadline]
    10:30-11:00  L3 Verify row counts                   30m  [effective deadline]
    11:00-11:30  Y  Fix the broken login                30m  [effective deadline]
    session: 150 of 150 min used; runs out at 11:30 (capacity exhausted)
    first pick: L1
    next carried over: X on Thu 05 Jun at 09:00
    forecast:
      L1 Write the migration script         finish Wed 04 Jun 10:00  remaining   0 min  on time
      L2 Dry-run the migration in staging   finish Wed 04 Jun 10:30  remaining   0 min  on time
      L3 Verify row counts                  finish Wed 04 Jun 11:00  remaining   0 min  on time
      Y  Fix the broken login               finish Wed 04 Jun 11:30  remaining   0 min  on time
      X  Answer the vendor                  finish Thu 05 Jun 09:45  remaining   0 min  on time

--- rule B: task priority, effective deadline, dependency pressure
    09:00-09:30  Y  Fix the broken login                30m  [effective deadline]
    09:30-10:15  X  Answer the vendor                   45m  [task priority]
    10:15-11:15  L1 Write the migration script          60m  [only eligible task]
    11:15-11:30  L2 Dry-run the migration in staging    15m  [only eligible task]
    session: 150 of 150 min used; runs out at 11:30 (capacity exhausted)
    first pick: Y
    next carried over: L2 on Thu 05 Jun at 09:00
    forecast:
      L1 Write the migration script         finish Wed 04 Jun 11:15  remaining   0 min  on time
      L2 Dry-run the migration in staging   finish Thu 05 Jun 09:15  remaining   0 min  LATE past Wed 04 Jun 12:00
      L3 Verify row counts                  finish Thu 05 Jun 09:45  remaining   0 min  LATE past Wed 04 Jun 12:00
      Y  Fix the broken login               finish Wed 04 Jun 09:30  remaining   0 min  on time
      X  Answer the vendor                  finish Wed 04 Jun 10:15  remaining   0 min  on time

--- rule C: dependency depth, effective deadline, task priority
    09:00-10:00  L1 Write the migration script          60m  [effective deadline]
    10:00-10:30  Y  Fix the broken login                30m  [effective deadline]
    10:30-11:15  X  Answer the vendor                   45m  [dependency depth]
    11:15-11:30  L2 Dry-run the migration in staging    15m  [only eligible task]
    session: 150 of 150 min used; runs out at 11:30 (capacity exhausted)
    first pick: L1
    next carried over: L2 on Thu 05 Jun at 09:00
    forecast:
      L1 Write the migration script         finish Wed 04 Jun 10:00  remaining   0 min  on time
      L2 Dry-run the migration in staging   finish Thu 05 Jun 09:15  remaining   0 min  LATE past Wed 04 Jun 12:00
      L3 Verify row counts                  finish Thu 05 Jun 09:45  remaining   0 min  LATE past Wed 04 Jun 12:00
      Y  Fix the broken login               finish Wed 04 Jun 10:30  remaining   0 min  on time
      X  Answer the vendor                  finish Wed 04 Jun 11:15  remaining   0 min  on time


======================================================================
CASE nothing
adaptation: hand-made: the day is nearly over. Now Thursday 16:30, planned leave 17:00, planned break 15 min, so today's session is 15 min. Every task needs more than 15 min, so nothing can finish today; the question is what the rules put in the 15 min sliver and whether that reads as a plan. D is an inbox task and E belongs to a paused project, so neither may appear anywhere.
now Thu 05 Jun 16:30, planned leave 17:00, break 15 min -> today's session 15 min
timetable (net minutes per weekday, workday starts 09:00): {MONDAY=300, TUESDAY=300, WEDNESDAY=240, THURSDAY=240, FRIDAY=300, SATURDAY=0, SUNDAY=0}
projects: [Project[id=Atlas, title=Project Atlas, deadline=null, priority=3, status=active], Project[id=Onboarding, title=Onboarding rework, deadline=null, priority=2, status=paused]]
tasks:
  A  Draft the launch email             est 45   logged 0   prio 2 dep []         ready project Atlas own deadline Fri 06 Jun 12:00
  C  Post the standup notes             est 20   logged 0   prio 1 dep []         ready own deadline Fri 06 Jun 17:00
  B  Tidy the backlog labels            est 90   logged 0   prio 4 dep []         ready
  D  Buy milk                           est 30   logged 0   prio 1 dep []         inbox
  E  Rewrite the welcome tour           est 60   logged 0   prio 1 dep []         ready project Onboarding
ineligible under the normal rules:
  D: inbox task
  E: project Onboarding is paused

--- rule A: effective deadline, dependency pressure, task priority, project priority, creation order
    16:30-16:45  A  Draft the launch email              15m  [effective deadline]
    session: 15 of 15 min used; runs out at 16:45 (capacity exhausted)
    first pick: A
    next carried over: A on Fri 06 Jun at 09:00
    forecast:
      A  Draft the launch email             finish Fri 06 Jun 09:30  remaining   0 min  on time
      C  Post the standup notes             finish Fri 06 Jun 09:50  remaining   0 min  on time
      B  Tidy the backlog labels            finish Fri 06 Jun 11:20  remaining   0 min  on track (no deadline)

--- rule B: task priority, effective deadline, dependency pressure
    16:30-16:45  C  Post the standup notes              15m  [task priority]
    session: 15 of 15 min used; runs out at 16:45 (capacity exhausted)
    first pick: C
    next carried over: C on Fri 06 Jun at 09:00
    forecast:
      A  Draft the launch email             finish Fri 06 Jun 09:50  remaining   0 min  on time
      C  Post the standup notes             finish Fri 06 Jun 09:05  remaining   0 min  on time
      B  Tidy the backlog labels            finish Fri 06 Jun 11:20  remaining   0 min  on track (no deadline)

--- rule C: dependency depth, effective deadline, task priority
    16:30-16:45  A  Draft the launch email              15m  [effective deadline]
    session: 15 of 15 min used; runs out at 16:45 (capacity exhausted)
    first pick: A
    next carried over: A on Fri 06 Jun at 09:00
    forecast:
      A  Draft the launch email             finish Fri 06 Jun 09:30  remaining   0 min  on time
      C  Post the standup notes             finish Fri 06 Jun 09:50  remaining   0 min  on time
      B  Tidy the backlog labels            finish Fri 06 Jun 11:20  remaining   0 min  on track (no deadline)


======================================================================
CASE ex3a
adaptation: supplementary, adapted from EX-3 part A: Thursday 09:00 to 11:00 becomes a session of 120 min. The spec says the successor gets no projected finish in the horizon and is missed; with the 14-day forecast horizon it now gets a real finish (Friday) that is still after its deadline. Same risk label, better reason.
now Thu 05 Jun 09:00, planned leave 11:00, break 0 min -> today's session 120 min
timetable (net minutes per weekday, workday starts 09:00): {MONDAY=300, TUESDAY=300, WEDNESDAY=240, THURSDAY=240, FRIDAY=300, SATURDAY=0, SUNDAY=0}
projects: [Project[id=Atlas, title=Project Atlas, deadline=2025-06-05T11:00, priority=3, status=active]]
tasks:
  P  Backfill the Atlas snapshot        est 60   logged 0   prio 3 dep []         ready
  S  Swap the Atlas read path           est 120  logged 0   prio 3 dep [P]        ready project Atlas
ineligible under the normal rules:
  (none)

--- rule A: effective deadline, dependency pressure, task priority, project priority, creation order
    09:00-10:00  P  Backfill the Atlas snapshot         60m  [only eligible task]
    10:00-11:00  S  Swap the Atlas read path            60m  [only eligible task]
    session: 120 of 120 min used; runs out at 11:00 (capacity exhausted)
    first pick: P
    next carried over: S on Fri 06 Jun at 09:00
    forecast:
      P  Backfill the Atlas snapshot        finish Thu 05 Jun 10:00  remaining   0 min  on track (no deadline)
      S  Swap the Atlas read path           finish Fri 06 Jun 10:00  remaining   0 min  LATE past Thu 05 Jun 11:00

--- rule B: task priority, effective deadline, dependency pressure
    09:00-10:00  P  Backfill the Atlas snapshot         60m  [only eligible task]
    10:00-11:00  S  Swap the Atlas read path            60m  [only eligible task]
    session: 120 of 120 min used; runs out at 11:00 (capacity exhausted)
    first pick: P
    next carried over: S on Fri 06 Jun at 09:00
    forecast:
      P  Backfill the Atlas snapshot        finish Thu 05 Jun 10:00  remaining   0 min  on track (no deadline)
      S  Swap the Atlas read path           finish Fri 06 Jun 10:00  remaining   0 min  LATE past Thu 05 Jun 11:00

--- rule C: dependency depth, effective deadline, task priority
    09:00-10:00  P  Backfill the Atlas snapshot         60m  [only eligible task]
    10:00-11:00  S  Swap the Atlas read path            60m  [only eligible task]
    session: 120 of 120 min used; runs out at 11:00 (capacity exhausted)
    first pick: P
    next carried over: S on Fri 06 Jun at 09:00
    forecast:
      P  Backfill the Atlas snapshot        finish Thu 05 Jun 10:00  remaining   0 min  on track (no deadline)
      S  Swap the Atlas read path           finish Fri 06 Jun 10:00  remaining   0 min  LATE past Thu 05 Jun 11:00


======================================================================
CASE ex3b
adaptation: supplementary, adapted from EX-3 part B: now Friday 09:00, planned leave 10:00, planned break 0, so today's session is 60 min. T has estimate 60 and 75 minutes logged, so remaining effort is 0: the model must give it a five minute completion slot and report the 15 minute overrun without changing the estimate.
now Fri 06 Jun 09:00, planned leave 10:00, break 0 min -> today's session 60 min
timetable (net minutes per weekday, workday starts 09:00): {MONDAY=300, TUESDAY=300, WEDNESDAY=240, THURSDAY=240, FRIDAY=300, SATURDAY=0, SUNDAY=0}
projects: []
tasks:
  T  Ship the Atlas hotfix              est 60   logged 75  prio 3 dep []         ready own deadline Fri 06 Jun 10:00
ineligible under the normal rules:
  (none)
estimate overrun: T logged 75 of 60 (15 min over); the estimate is kept, remaining effort is 0

--- rule A: effective deadline, dependency pressure, task priority, project priority, creation order
    09:00-09:05  T  Ship the Atlas hotfix                5m  [only eligible task]
    session: 5 of 60 min used; runs out at 09:05 (55 min unused, no eligible work left)
    first pick: T
    forecast:
      T  Ship the Atlas hotfix              finish Fri 06 Jun 09:05  remaining   0 min  on time

--- rule B: task priority, effective deadline, dependency pressure
    09:00-09:05  T  Ship the Atlas hotfix                5m  [only eligible task]
    session: 5 of 60 min used; runs out at 09:05 (55 min unused, no eligible work left)
    first pick: T
    forecast:
      T  Ship the Atlas hotfix              finish Fri 06 Jun 09:05  remaining   0 min  on time

--- rule C: dependency depth, effective deadline, task priority
    09:00-09:05  T  Ship the Atlas hotfix                5m  [only eligible task]
    session: 5 of 60 min used; runs out at 09:05 (55 min unused, no eligible work left)
    first pick: T
    forecast:
      T  Ship the Atlas hotfix              finish Fri 06 Jun 09:05  remaining   0 min  on time

======================================================================
COMPARISON TABLE

case     rule today's order              first    out at    deadlines missed
------------------------------------------------------------------------------
ex1      A   A B C                      A        13:45     none
ex1      B   B C A                      B        13:45     A:late
ex1      C   A B C                      A        13:45     none
------------------------------------------------------------------------------
ex2      A   R P S                      R        11:30     S:late
ex2      B   R P S                      R        11:30     S:late
ex2      C   R P S                      R        11:30     S:late
------------------------------------------------------------------------------
chain    A   L1 L2 L3 Y                 L1       11:30     none
chain    B   Y X L1 L2                  Y        11:30     L2:late L3:late
chain    C   L1 Y X L2                  L1       11:30     L2:late L3:late
------------------------------------------------------------------------------
nothing  A   A                          A        16:45     none
nothing  B   C                          C        16:45     none
nothing  C   A                          A        16:45     none
------------------------------------------------------------------------------
ex3a     A   P S                        P        11:00     S:late
ex3a     B   P S                        P        11:00     S:late
ex3a     C   P S                        P        11:00     S:late
------------------------------------------------------------------------------
ex3b     A   T                          T        09:05     none
ex3b     B   T                          T        09:05     none
ex3b     C   T                          T        09:05     none
------------------------------------------------------------------------------
```

## Comparison table

```
case     rule today's order              first    out at    deadlines missed
------------------------------------------------------------------------------
ex1      A   A B C                      A        13:45     none
ex1      B   B C A                      B        13:45     A:late
ex1      C   A B C                      A        13:45     none
------------------------------------------------------------------------------
ex2      A   R P S                      R        11:30     S:late
ex2      B   R P S                      R        11:30     S:late
ex2      C   R P S                      R        11:30     S:late
------------------------------------------------------------------------------
chain    A   L1 L2 L3 Y                 L1       11:30     none
chain    B   Y X L1 L2                  Y        11:30     L2:late L3:late
chain    C   L1 Y X L2                  L1       11:30     L2:late L3:late
------------------------------------------------------------------------------
nothing  A   A                          A        16:45     none
nothing  B   C                          C        16:45     none
nothing  C   A                          A        16:45     none
------------------------------------------------------------------------------
ex3a     A   P S                        P        11:00     S:late
ex3a     B   P S                        P        11:00     S:late
ex3a     C   P S                        P        11:00     S:late
------------------------------------------------------------------------------
ex3b     A   T                          T        09:05     none
ex3b     B   T                          T        09:05     none
ex3b     C   T                          T        09:05     none
------------------------------------------------------------------------------
```

`out at` is the end of the last item placed today. `deadlines missed` counts
tasks whose forecast finish is after their effective deadline.

## Findings

**F1. The three rules differ in what they ignore, not in how they score.**
Rule A ignores priority until deadlines and dependency pressure tie. Rule B
ignores the deadline until priority ties, and it never looks at dependency
pressure except as a last resort. Rule C ignores the deadline until dependency
depth ties, and its depth key is dominated by tasks with no prerequisites at
all, so it behaves like "leaves first, then deadline". That is the whole
difference: on these six cases A and C never disagree about the first pick
(most tasks sit at depth 0, so C's depth key is a no-op for them and its next
key - the effective deadline - is the key A starts with), while B differs from A on the
first pick in three of the six (`ex1`, `chain`, `nothing`).

**F2. Where the rules disagree, the disagreement costs deadlines.**
Three cases disagree. In `ex1` rule B picks `B` (priority 1) and `A` finishes at
13:45, past its 11:00 project deadline; A and C pick `A` first and nothing is
late. In `chain` rule A gets the whole migration chain done by 11:00 and the
critical login fix done by 11:30, meeting everything; B and C both put unrelated
critical work in front of a chain due at noon and the chain misses its deadline
(`L2` and `L3` finish Thursday). In `nothing` the rules disagree only about
which task gets the 15-minute sliver: A and C start the near-deadline email, B
starts the priority-1 standup notes. Nothing is late either way, so that
disagreement is cheap.

**F3. Rule C's dependency-first ordering is actively harmful, which I did not
expect.** Depth-first only protects a chain at its first step. Once `L1` is
done, `L2` is ready but its depth is 1, so any depth-0 task outranks it: in
`chain`, C puts `Y` and `X` (both depth 0, both due later) ahead of `L2` and the
noon deadline is missed. Depth is a property of position in the graph, not of
urgency, so it is the wrong axis for protecting a chain. If dependency order
must lead, it has to be "ready successors of work already started, then
everything else", not a depth count.

**F4. All three rules fail the specification's EX-2, and they fail it the same
way.** `P` has no deadline of its own and unlocks `S`, which is due at 11:00. The
specification pulls `P` forward through a dependency-protection tier keyed on the
descendant's slack. None of the three rules can: A compares effective deadlines
first and `P`'s is null, B compares priority first and `P` is priority 5, C
compares depth first and `R` and `P` are both depth 0 so the deadline decides and
`R` wins. Result in all three: `R` 09:00-10:00, `P` 10:00-10:30, `S` 10:30-11:30,
and `S` is 30 minutes late against 11:00. The capacity model is not at fault; the
criteria are.

**F5. The smallest fix for F4 is to inherit the deadline along dependency edges.**
If a prerequisite takes the earliest effective deadline among the deadline-bearing
tasks it unlocks, `P`'s effective deadline becomes 11:00. Rule A then reads: `P`
(11:00) before `R` (12:00), `S` 09:30-10:30, `R` 10:30-11:30 - the spec's EX-2
itinerary exactly, with nothing late. This is arithmetic I checked by hand
against the comparator; it is **not** implemented in the prototype, and it changes
no other case here (`ex1` has no dependencies; `chain` and `ex3a` already inherit
the project deadline). It is a change to `effDeadline`, about five lines.

**F6. The fit signal is honest about the boundary and silent about the fragment.**
Nothing crosses the planned leave time: in every case the last item ends at or
before the session's end, and the program reports both the boundary and whether
capacity was exhausted or ran dry with no eligible work. What the model cannot
say is "this does not fit". In `nothing` the user gets a single 15-minute item
for a 45-minute task: a plan item that cannot be finished, printed in the same
shape as a finished one. The task brief's two options were "mark the task that
would cross the planned leave time and stop the list there" or "keep listing
everything with a marker per item"; the settled model does neither, because chunking means nothing ever
crosses. A marker for items that leave remaining effort ("starts, does not
finish") is one line of output and would make the 15-minute sliver readable.

**F7. The 14-day horizon changes the risk vocabulary, mostly for the better.**
`ex3a` is the specification's "no projected finish, missed" case. Under this
model it has a concrete projected finish - Friday 10:00 - which is still after a
Thursday 11:00 deadline, so the label becomes "missed, finishing Friday 10:00"
instead of "no finish in horizon". That is a better explanation of the same
fact. The cost: within 14 days almost everything gets a projected finish, so
`unscheduled` stops meaning "does not fit" and starts meaning only "does not fit
in fourteen days". Whatever reads the risk label has to say which one it means.

**F8. The five-minute completion slot behaves.** `ex3b` places it 09:00-09:05,
reports the 15-minute overrun without touching the estimate, and leaves 55
minutes unused rather than filling them. The one wrinkle is wording: "the session
runs out at 09:05" is wrong when 55 minutes remain and nothing is eligible, so
the program distinguishes "capacity exhausted" from "no eligible work left".

**F9. The break interacts with a short session the way a user would fear.**
In `chain` the 30-minute break turns 180 minutes into 150, and that is the
difference between finishing the day's work and spilling 45 minutes into
tomorrow. In `nothing` a 15-minute break on a 30-minute session leaves 15
minutes, which is not enough to finish anything, so the model emits a fragment.
A break that is large relative to the remaining session is worth a warning of
its own.

## Recommendation

**Rule A, with inherited deadlines on dependency edges (F5).**

- It is the only rule that met every deadline in both cases where deadlines were
  actually at stake (`ex1`, `chain`).
- It matches the specification's own prose about itself: priority is a tie-break
  after deadline safety and dependency constraints.
- Its single failure (`ex2`) is not a failure of the ordering idea, it is a
  failure to give a prerequisite a deadline; inheriting the dependents'
  effective deadline repairs it and changes nothing else.
- Dropping rule B's ordering is safe: as a primary key, priority is exactly the
  behaviour the specification warns against, and `ex1` shows it costing a
  deadline it promised to protect.
- Do not ship rule C's depth ordering. It reads as dependency-aware but protects
  a chain only until the first step is done (F3).

If only the three rules as given are on the table, take A and accept the EX-2
regression as known and explained, rather than B, which misses deadlines that
are visible in the input.

## The question for the human

Does the `today` list in the full output look like a plan you would follow?
Concretely, from `chain` under rule A:

```
09:00-10:00  L1  Write the migration script
10:00-10:30  L2  Dry-run the migration in staging
10:30-11:00  L3  Verify row counts
11:00-11:30  Y   Fix the broken login
             (X carried to Thursday 09:00)
```

Is that the day you would plan, or would you want `X` (45 minutes, due tomorrow
noon) somewhere inside it?

## Open risks and simplifications

- Dependency pressure uses static slack, not slack recomputed per chunk. It only
  breaks the third tie in rule A, so it is unlikely to matter, but it is not the
  specification's dynamic version.
- Every forecast day starts at 09:00; a real timetable would need a start time
  per weekday or a per-day session.
- The timetable is treated as net minutes, so a planned break exists only for
  today. If the real timetable carries breaks per weekday, capacity today and
  capacity tomorrow are computed by different formulas and drift.
- Dependency depth counts prerequisites, not longest path; on a chain the two
  agree, on a diamond they do not, and the diamond was not tested.
- Weekend capacity is 0 in every case, so a plan that has to cross a weekend is
  untested.
- Chunking means a partial fragment is always possible, so the prototype never
  answers "would you rather leave the 15 minutes empty". That is F6 and it needs
  a decision, not a test.
