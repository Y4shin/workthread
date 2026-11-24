---
type: task
subtype: research
title: "How a Java port carries a partial edit: whole record, per-kind diff, or both"
status: stable
workflow_state: done
blocked_by: []
---

How does a Java port carry a change to one record: the whole record, a
per-field edit diff, or a sealed input that accepts either, and which of those
is convention?

The operator raised this shape himself while answering the `store-contract`
grilling, and asked for the Java convention and a pro/contra before the port
adopts it. `store-contract` cannot close its edit-shape item until this lands.

### The shape the operator proposed, to test rather than adopt

For a kind with a title and a description, a `Note` record, plus a `NoteDiff`
built either from an existing note or from nothing, with methods
`withTitle(String)` and `withoutTitle()`. The update operation takes a sealed
input accepting either `Note` or `NoteDiff`. The intent is that a caller
changes only the fields it means to change, while the same operation can still
take a whole record.

### Sub-questions, each with a primary-source citation

1. What do established Java data layers do for a partial update? Cover at
   least JDBC and JPA dirty checking, jOOQ's update DSL and its `Record`/
   `UpdatableRecord` shape, Jdbi's `@BindBean` and dynamic SQL, Spring Data's
   `save` semantics and its `Patch`/partial-update story if one exists, and
   JSON Merge Patch (RFC 7386) as the API-side convention that later surfaces
   will mirror.
2. Is a per-kind `XDiff` type with wither methods a recognised Java pattern or
   an invention? Find the nearest named patterns (builder, `with`-style copy
   methods on records, `Optional`-carrying field choices, patch types generated
   from schemas) and say how each differs from the proposed shape.
3. What does a `Diff` type cost in the type system when it must express "leave
   this field alone", "set it to null", and "set it to a value" for an optional
   field? Which encodings make the third state impossible to forget?
4. What does each shape cost at the change log? A diff yields a row per changed
   field or a row per record with a field list; a whole record yields a full
   before-and-after if history is wanted.
5. What does each shape cost at the port's integrity checking and at the fake
   the core's tests use? A diff that omits a required field cannot be checked
   alone, which meets the deferred-checking question that
   `transaction-modeling` owns. Say where the two questions touch.
6. Lost updates: which shapes need a row version or an expected-state check to
   avoid one caller overwriting another's field, and what is conventional in
   Java for that.

### Trusted sources

Jakarta Persistence specification; jOOQ reference manual and source; Jdbi
documentation and source; Spring Data reference documentation; RFC 7386 and
RFC 6902; the `java.lang.Record` and `java.util.Optional` javadoc. Blog posts
are leads, not evidence.

### Evidence required

A findings note at `docs/research/edit-shape.md`, one citation per material
claim, a confidence per item, and a recommendation that names which shape the
port adopts, with the pros and cons of the proposed `XDiff` shape stated
plainly. Unresolved points are named, not smoothed over.

### Likely dependents

`store-contract` item 8, the specification's port and record sections, every
later effort's edit commands, and the API surface that will mirror the same
shape as a patch request body.

## Notes

All six sub-questions answered with primary-source citations and a confidence
per item. The findings are at `docs/research/edit-shape.md`.

The verdict on the operator's proposal: a per-kind `XDiff` with wither methods is
not a pattern any trusted Java data layer uses as an update parameter. Whole-
record write is the convention; partial edit appears as dirty tracking on a
record the layer loaded itself, a narrow hand-written statement, or an HTTP patch
applied at the edge. The proposed pieces are each recognised (a wither is a
copy-with, a mutable diff is a builder, and the closest named pattern is an
update mask), but the composition is an invention.

The real problem the proposal meets and the note settles: three states per field
("leave alone", "set a value", "set nothing"), which a nullable field cannot
express. The recommendation carries them in two generic sealed interfaces,
`Change<T>` with Keep and Set, and `NullableChange<T>` with Keep, Set, and Clear,
so an unhandled Clear is a compile error rather than a silent null.

Recommendation: adopt the per-kind patch record as the only edit value;
`NotePatch.replacing(note)` serves a caller that holds a whole record; drop the
sealed either-input, because its Replace case still needs the read the patch
needs anyway and it costs a sealed interface plus two case records per kind. The
merge is a pure function in `contract`, so the fake and the adapter cannot
disagree about what a patch means. No version column until a named use case needs
a conditional transition.

Left for `store-contract` to settle: whether a patch that changes nothing writes
nothing or is refused, and whether the identity is a parameter beside the patch
or a component of it.

Risk carried to a later effort, not settled here: a JSON request body cannot
distinguish an absent member from an explicit null with a plain record, so the
API effort needs a custom deserializer or a presence-aware mapper before it
designs a patch request body.

The findings were written from the researcher's reply rather than by the
researcher itself; the run replied with the document inline and left no file, so
the parent wrote it verbatim to the agreed path.
