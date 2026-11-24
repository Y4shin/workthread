---
type: findings
title: "How a Java port carries a partial edit: whole record, per-kind diff, or both"
status: stable
source_task: edit-shape
---

# Edit shape: whole record, per-kind diff, or a sealed input for both

Question: how does a Java port carry a change to one record, and which of the
shapes is convention? The operator proposed a per-kind `NoteDiff`, built from an
existing note or from nothing, with `withTitle(String)` and `withoutTitle()`,
and an update operation taking a sealed input that accepts either `Note` or
`NoteDiff`. That shape is tested here, not assumed.

**Answer in one paragraph.** Whole-record update is the convention in every Java
data layer checked. Partial update exists in those layers as one of three
things: dirty tracking on a mutable record the layer loaded itself (jOOQ,
Hibernate's flush), a narrow statement the caller writes by hand (JPA bulk
update, jOOQ `update().set(field, value)`, any Jdbi statement), or an HTTP patch
document applied at the edge before a whole-record save (Spring Data REST). No
layer in the trusted set takes a per-kind hand-written diff object as its update
parameter. The proposed shape is therefore a composition of recognised pieces,
not a recognised pattern. Its three-state problem ("leave alone", "set to null",
"set to a value") is real, is named in RFC 7396 and in protobuf's field presence
documentation, and is not solvable by a nullable field. The recommendation at
the end keeps the operator's intent (a caller changes only what it means to
change) but drops the sealed either-input and fixes the three-state carrier.

---

## The shape under test, and what it leaves open

`NoteDiff` built "from an existing note or from nothing", with `withTitle` and
`withoutTitle`, and an update taking a sealed input that accepts `Note` or
`NoteDiff`.

Two things the description leaves open, both decision-relevant:

1. **Mutability.** `withTitle` returning `NoteDiff` is a wither (immutable copy).
   `withTitle` returning `void` and mutating is a builder. The same two method
   names cover both, and only one of them is safe to pass across the port
   (item 2.2).
2. **What "built from an existing note" buys.** If the diff keeps the base
   state, it can carry an expected version. If it can also be built "from
   nothing", the base is optional, so the same type sometimes anchors and
   sometimes does not. Item 6 treats that split.

---

## 1. What established Java data layers do for a partial update

### 1.1 JDBC: no notion of an edit at all

**Claim.** JDBC has no partial-update concept and no whole-record concept. A
write is a SQL string the caller wrote, and the result is a row count. The port's
choice of shape is invisible to JDBC.

**Sources.**
[java.sql.Statement (JDK 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Statement.html).

**Support.** Direct evidence: "`executeUpdate` Executes the given SQL statement,
which may be an `INSERT`, `UPDATE`, or `DELETE` statement or an SQL statement
that returns nothing". The interface's method list has no operation taking an
aggregate or a record, and no operation reporting which columns changed.

**Confidence.** High.

**Consequence for this effort.** "What does JDBC do" is not a constraint on the
shape. It only says the adapter must build the statement from whatever the port
receives, so a diff costs the adapter a statement builder and a whole record
costs it a fixed column list.

### 1.2 JPA: dirty checking is real, but the portable write is whole-entity

**Claim.** A JPA write of an existing entity copies the whole state of the
detached instance onto the managed one. The spec's only portable narrow write is
a bulk update statement, which bypasses the persistence context and optimistic
locking. Per-column SQL at flush is provider behaviour, not spec behaviour.

**Sources.**
[Jakarta Persistence 3.2, 3.3.7.1 Merging Detached Entity State](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html#a1983),
[4.11 Bulk Update and Delete Operations](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html#a5636),
[6.3.15 same, criteria API](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html),
[Hibernate `@DynamicUpdate` javadoc](https://docs.jboss.org/hibernate/orm/6.6/javadocs/org/hibernate/annotations/DynamicUpdate.html).

**Support.** Direct evidence:

- "If X is a detached entity, the state of X is copied onto a pre-existing
  managed entity instance X' of the same identity or a new managed copy X' of X is
  created." (3.3.7.1)
- Bulk update "bypass[es] any optimistic locking checks", and "Portable
  applications using bulk update operations must manually update the value of
  the version column" (6.3.15). "The persistence context is not synchronized with
  the result of the bulk update or delete." (4.11)
- `@DynamicUpdate`: "Specifies that SQL `update` statements for the annotated
  entity are generated dynamically, and only include columns which are actually
  being updated."

Interpretation: the JPA spec never promises a column-level UPDATE. A provider
that writes all columns is conformant, which is why the changed-columns behaviour
needs an annotation.

**Confidence.** High for the merge and bulk statements. Medium for the negative
claim about column-level SQL, which rests on the spec's silence plus the
existence of `@DynamicUpdate`.

**The shape JPA offers.** `merge(wholeRecord)` or `bulkUpdate(namedFields)`. It
never offers a diff value. Dirty checking is a property of the managed instance
inside the persistence context, not something a caller constructs.

### 1.3 jOOQ: a generated full record with per-field change flags

**Claim.** jOOQ's partial update is a mutable `UpdatableRecord` that remembers
its original values and writes only the touched fields. The generated record is
schema-derived and complete; the "diff" is runtime state on that record, not a
type.

**Sources.**
[jOOQ manual: Record internal flags and dirty tracking](https://www.jooq.org/doc/latest/manual/sql-execution/crud-with-updatablerecords/internal-flags/),
[jOOQ manual: CRUD Store](https://www.jooq.org/doc/latest/manual/sql-execution/crud-with-updatablerecords/simple-crud/crud-store/),
[jOOQ manual: storing unchanged records](https://www.jooq.org/doc/latest/manual/sql-building/dsl-context/custom-settings/settings-unchanged-records/),
[`UpdatableRecord` javadoc](https://www.jooq.org/javadoc/latest/org.jooq/org/jooq/UpdatableRecord.html).

**Support.** Direct evidence:

- "In either statement type, only those fields are inserted/updated, which had
  been explicitly set by client code, in order to allow for `DEFAULT` values to
  be applied by the underlying RDBMS." (`UpdatableRecord` javadoc)
- "`Record.original(Field)`: The 'original' value, i.e. the value as it was
  originally fetched from the database"; "`Record.touched(Field)`: The 'touched'
  flag, indicating if the value was ever touched (or modified) through the `Record`
  API. This is used for dirty tracking." (internal flags page)
- "When storing records, jOOQ only includes values in an `INSERT` or `UPDATE`
  statement that have been changed by client code." (storing unchanged records)

**Confidence.** High.

**Two lessons that transfer.** First, the mechanism that makes a partial update
workable in a Java data layer is *(original value, current value, touched flag)*
per field, held by the layer. Second, jOOQ distinguishes *touched* from
*modified*: a value can be set to what it already was. That distinction is worth
keeping in a change log (item 4).

### 1.4 Jdbi: you write the SQL; binding is by bean property name

**Claim.** Jdbi offers no partial update, no dirty tracking, and no diff type. It
binds bean properties to the statement the caller wrote, and by default it
refuses a binding the statement does not use. Optimistic locking is not a Jdbi
feature at all.

**Sources.**
[Jdbi 3 Developer Guide](https://jdbi.org/releases/3.54.0/),
[`@BindBean` javadoc](https://jdbi.org/apidocs/org/jdbi/v3/sqlobject/customizer/BindBean.html),
[Jdbi settings: `unusedBindingsAllowed`](https://jdbi.org/releases/3.54.0/).

**Support.** Direct evidence:

- `@BindBean`: "Binds the properties of a JavaBean to a SQL statement."
- The guide's settings table: `unusedBindingsAllowed` defaults to `false`, "All
  bindings to a SQL operation must be used or an exception is thrown. If this
  setting is `true`, then unused bindings in a SQL operation are ignored."
- The word "optimistic" does not occur anywhere in the developer guide (searched
  the single-page guide). **Support:** direct evidence of absence, search only.

Interpretation, flagged: "bind the whole bean and let the template pick three
properties" is a trap in Jdbi unless the setting is relaxed, which is the trap
this effort avoids by not building the SQL from an object graph.

**Confidence.** High for the quotes, high for the absence of optimistic locking
in the guide.

### 1.5 Spring Data: `save` is persist-or-merge, and PATCH lives at the HTTP edge

**Claim.** `CrudRepository.save` is a whole-entity operation. The partial-update
story is in Spring Data REST, where `PATCH` is a separate HTTP method documented
as a partial update, is applied to the entity, and is then backed by the same
`save(...)`. The version-annotation plus `ETag`/`If-Match` mechanism is the
documented answer to one client overwriting another.

**Sources.**
[Spring Data JPA reference: Saving Entities](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html),
[Spring Data REST reference: item resource methods](https://docs.spring.io/spring-data/rest/docs/3.5.x/reference/html/#repository-resources.item-resource.supported-methods),
[Spring Data REST reference: ETag, `@Version`, `If-Match`](https://docs.spring.io/spring-data/rest/docs/3.5.x/reference/html/).

**Support.** Direct evidence:

- "It persists or merges the given entity by using the underlying JPA
  `EntityManager` ... Otherwise, it calls the `entityManager.merge(...)` method."
- "The `PATCH` method is similar to the `PUT` method but partially updates the
  resources state", and the method used for invocation is `save(...)`.
- "The `@Version` annotation (the JPA one in case you're using Spring Data JPA,
  the Spring Data `org.springframework.data.annotation.Version` one for all other
  modules) flags this field as a version marker ... We can conditionally `PUT`,
  `PATCH`, or `DELETE` that resource if we supply a `If-Match` header ... This
  safeguard prevents clients from stomping on each other."

**Confidence.** High.

**What this means for the port.** The framework that most explicitly owns the
word "partial update" keeps the partiality in the transport and converts it into
a whole-record save before it reaches the data layer. That is evidence for item 6
and against assuming the port must take a diff because the API will.

### 1.6 The wire conventions: RFC 7396 and RFC 6902

**Claim.** Merge patch is a sparse document whose absent members mean "leave
alone" and whose `null` members mean "remove". It cannot express "set to null".
JSON Patch is a list of operations at a path, with `add`, `remove`, and `replace`
as distinct operations, so it can express all three states but no longer carries
a schema. Merge patch also has an explicit whole-replacement case: a non-object
patch replaces the entire target.

**Sources.**
[RFC 7396 JSON Merge Patch](https://www.rfc-editor.org/rfc/rfc7396.txt),
[RFC 6902 JSON Patch](https://www.rfc-editor.org/rfc/rfc6902.txt).

**Support.** Direct evidence:

- "Null values in the merge patch are given special meaning to indicate the
  removal of existing values in the target." (RFC 7396 §1)
- "This design means that merge patch documents are suitable for describing
  modifications to JSON documents that primarily use objects for their structure
  and do not make use of explicit null values. The merge patch format is not
  appropriate for all JSON syntaxes." (RFC 7396 §1)
- "If the patch is anything other than an object, the result will always be to
  replace the entire target with the entire patch." (RFC 7396 §2)
- "Operation objects MUST have exactly one `op` member, whose value indicates the
  operation to perform. Its value MUST be one of `add`, `remove`, `replace`,
  `move`, `copy`, or `test`"; "The `remove` operation removes the value at the
  target location"; "The `replace` operation replaces the value at the target
  location with a new value". (RFC 6902 §4, §4.2, §4.3)

Interpretation, flagged: RFC 6902's `replace` with `"value": null` and RFC 6902's
`remove` are the two distinct operations that merge patch collapses into one, so
RFC 6902 is the wire format that matches a three-state Java carrier. RFC 6902
never spells this out, hence the label.

**Confidence.** High for the quotes, medium for the interpretation sentence.

Note the citation defect in the task body: the task cites RFC 7386 for JSON Merge
Patch. RFC 7396 is the current one and its header reads "Obsoletes: 7386". The
two texts describe the same format. See Contradictions.

### 1.7 Summary table

| Layer | Whole record | Partial | Diff value taken as an argument |
| --- | --- | --- | --- |
| JDBC | a SQL statement you wrote | a narrower SQL statement you wrote | none |
| JPA | `merge(detached)` copies all state | bulk update statement, bypasses the context | none |
| jOOQ | `UpdatableRecord` with all fields set | same record with touched flags; or `update().set(field, value)` | none |
| Jdbi | a SQL statement you wrote | a narrower SQL statement you wrote | none |
| Spring Data | `save` = persist or merge | HTTP `PATCH`, then `save` | none in the repository |
| HTTP conventions | RFC 7396 non-object patch replaces the target | RFC 7396 object patch; RFC 6902 op list | the patch document |

**Researcher inference, flagged.** Six APIs do not prove a universal negative.
The claim this note supports is narrower: none of the layers in the trusted set
takes a per-kind, hand-written diff object as its update parameter, and the
partial-update facility in each is either dirty tracking over a layer-owned
record or a statement the caller writes.

---

## 2. Is a per-kind `XDiff` with wither methods a recognised pattern?

**Verdict, stated plainly.** The composition is an invention. Every part of it is
recognised, and no Java data layer or specification in the trusted set names the
whole. The nearest named patterns are below, with how each differs from the
proposal.

### 2.1 Withers ("copy-with"): recognised, and it is the right name for the methods

**Claim.** `withX(value)` returning a new instance is an established Java idiom
with an established name, and Java records are a natural carrier for it.

**Sources.**
[`java.time.LocalDate` (JDK 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/LocalDate.html),
[`java.lang.Record` (JDK 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Record.html),
[Lombok `@With`](https://projectlombok.org/features/With),
[Spring Data Commons `PersistentProperty` (`getWither`), source](https://github.com/spring-projects/spring-data-commons/commit/a7463df79130e99d3de9172e72b8ded3fecbf98f).

**Support.** Direct evidence:

- `LocalDate.withYear(int)`: "Returns a copy of this `LocalDate` with the year
  altered. ... This instance is immutable and unaffected by this method call."
- `Record`: "A record class is a shallowly immutable, transparent carrier for a
  fixed set of values", with mandated members limited to the canonical
  constructor, one private final field per component, one accessor per component,
  and `equals`/`hashCode`/`toString`. The javadoc states the copy invariant, that
  `new R(r.c1(), ..., r.cn())` must equal `r`, and mandates no `with`/`copy`
  method, so every wither on a record is hand-written.
- Lombok `@With`: "Immutable 'setters' - methods that create a clone but with one
  changed field."
- Spring Data Commons `PersistentProperty.getWither()`: "`with` methods are
  property-bound instance methods that accept a single argument of the property
  type creating a new object instance."

**How it differs from the proposal.** A wither on `Note` yields a `Note`. A
wither on `NoteDiff` yields a `NoteDiff`, which is a different thing: the idiom is
recognised, but applying it to a delta type is the composition, not the idiom.

**Confidence.** High.

Note on the Spring Data citation: `getWither` is internal mapping machinery, used
to read a property from an immutable instance. It is evidence that the name and
the contract are established, not evidence that Spring Data patches entities with
withers. The same diff shows Spring Data supporting records with `withX` methods
directly (a test entity `record WithStaticWith(String)` with `withString`).

### 2.2 Builder: recognised, and it is what a mutable `NoteDiff` actually is

**Claim.** If `withTitle` returns `void` and mutates, the type is a builder, not
a diff. A builder's product is a complete object; its unset fields are defaults,
not a statement about which fields the caller meant to change.

**Sources.** The shape itself, against the `java.lang.Record` javadoc's
immutability statement (above) and RFC 6902's separation of path and value
(item 1.6). No specification defines "builder"; this is common Java practice, and
the distinction drawn here is researcher analysis.

**How it differs.** A builder accumulates toward `build()`, and passing a
half-built builder across a boundary is an aliasing hazard: the callee can keep
mutating it. A diff must be a value at the port boundary.

**Confidence.** High for the aliasing hazard (it is a property of shared mutable
state, not a disputed claim). Medium for calling the mutable variant "a builder"
rather than "a bug", which is a naming judgement.

### 2.3 Patch document: recognised, and it is the closest named pattern

**Claim.** "A sparse set of field changes addressed by name" is a named wire
concept (RFC 7396, RFC 6902), and Spring Data REST implements both. That is the
nearest named pattern to `NoteDiff`. The difference is that both RFCs define the
patch as data with no compile-time schema, and both are document-shaped (a map or
an op array), not type-shaped.

**Sources.** RFC 7396 and RFC 6902 (item 1.6),
[Spring Data REST `PATCH`](https://docs.spring.io/spring-data/rest/docs/3.5.x/reference/html/#repository-resources.item-resource.supported-methods).

**How it differs.** A per-kind Java patch type is the typed, schema-bound version
of a merge patch. That is a real improvement in type safety and it is also why no
such type is generated for you: the typing is the work.

**Confidence.** High.

### 2.4 Update mask plus explicit presence: recognised, generated from a schema, and it answers the three-state question

**Claim.** The schema-generated answer to partial update is not a generated patch
class. It is a generated full message plus an explicit presence marker per field,
optionally with a generic field mask naming the paths to write. This is the only
pattern in the set that encodes "unset" separately from "set to the default
value", which is the same problem as "leave alone" versus "set to null".

**Sources.**
[Protobuf application note: Field Presence](https://protobuf.dev/programming-guides/field_presence/),
[`field_mask.proto`](https://github.com/protocolbuffers/protobuf/blob/main/src/google/protobuf/field_mask.proto).

**Support.** Direct evidence:

- Implicit presence: "To 'clear' a field, it is set to its default value. The
  default value may mean: the field was explicitly set to its default value,
  which is valid in the application-specific domain of values; the field was
  notionally 'cleared' by setting its default; or the field was never set."
- "Updating to set a default value in this case requires some external mechanism,
  such as `FieldMask`."
- Explicit presence: "A generated `has_foo` method indicates whether or not the
  field `foo` has been set (and not cleared)."
- `field_mask.proto`: "Field masks are used to specify a subset of fields that
  should be returned by a get operation or modified by an update operation."

**How it differs from the proposal.** Protobuf generates the full message, not a
per-type delta, and the "which fields" information is either a presence bit on
the message or a separate generic mask. The proposal puts the "which fields"
information in the type's own method set.

**Confidence.** High.

### 2.5 Dirty-tracking record: recognised, and it is what jOOQ actually ships

Covered in item 1.3. It differs from the proposal in that the record is complete
and mutable and knows its own originals; the caller mutates fields rather than
composing a delta.

### 2.6 Sealed either-input: recognised as a sum type, invented as a data-layer parameter

**Claim.** A sealed interface with one permitted record per case is idiomatic Java
21, and the compiler enforces exhaustive handling. Using one as the update
parameter of a data layer is not a thing any layer in the trusted set does.

**Sources.**
[Pattern Matching for switch (Java 21)](https://docs.oracle.com/en/java/javase/21/language/pattern-matching-switch.html),
[RFC 7396 §2](https://www.rfc-editor.org/rfc/rfc7396.txt).

**Support.** Direct evidence:

- "the `switch` blocks of `switch` expressions and `switch` statements, which use
  pattern or `null` labels, must be exhaustive. This means that for all possible
  values, there must be a matching `switch` label."
- The nearest wire analogue is RFC 7396's own two-case algorithm: an object patch
  merges, "If the patch is anything other than an object, the result will always
  be to replace the entire target with the entire patch".

Interpretation, flagged: the sealed either-input is the type-level version of
"PUT or PATCH", which is a recognised HTTP pairing (RFC 7396 and RFC 6902 both
target the PATCH method, and Spring Data REST exposes `PUT` and `PATCH` as
siblings). That analogy supports the shape at the **surface** layer. It does not
show that the port needs the union, because a surface can dispatch on the HTTP
method and call two different port operations.

**Confidence.** High for exhaustiveness, high for "no layer does this", medium
for the PUT/PATCH analogy being the right way to read the proposal.

---

## 3. What a `Diff` costs when a field has three states

The three states are: leave the field alone, set it to null, set it to a value.
Call them Keep, Clear, Set.

### 3.1 The proposed method pair names all three states at the call site

**Claim.** `withTitle(String)` plus `withoutTitle()`, with absence meaning Keep,
is a complete three-state *interface* for a nullable field. The problem is not the
method set. The problem is the state the object actually carries.

**Support.** Researcher analysis of the proposal. A caller can express all three
intents. Nothing in the method set leaks the carrier.

**Confidence.** High, it is a reading of the proposal.

### 3.2 The obvious carrier collapses two states, silently

**Claim.** If the carrier is "a nullable field where null means Keep", then Clear
is unrepresentable: `withoutTitle()` either throws or silently means Keep. This is
the same failure protobuf documents for implicit presence, and the same limit
RFC 7396 accepts for merge patch, where `null` has to mean remove.

**Sources.** Protobuf field presence, RFC 7396 §1 (both quoted in item 2.4 and
item 1.6).

**Support.** Direct evidence for the general phenomenon; the application to
`NoteDiff` is inference.

**Confidence.** High.

### 3.3 Encodings that make the third state impossible to forget

Ranked by how hard the compiler makes it to drop a case.

1. **Two sealed carriers, split by whether the field may be cleared.**
   `Change<T> permits Keep, Set` for required fields; `NullableChange<T> permits
   Keep, Set, Clear` for nullable ones. The compiler forces a `switch` over the
   nullable carrier to cover Clear, and makes "clear the title" a compile error
   because `Change<T>` has no Clear case. **Support:** direct evidence that a
   switch over a sealed type must be exhaustive (Java 21 pattern matching page).
   **Confidence:** high.
2. **One three-case carrier for every field**, `Change<T> permits Keep, Set,
   Clear`, with the adapter refusing Clear on a NOT NULL column at runtime.
   Exhaustive, but "clear a required field" survives compilation. **Confidence:**
   high.
3. **A presence bit plus a value**, protobuf's `has_foo()` shape. Works, needs a
   written convention that every field is read through its bit, and nothing
   enforces it. **Support:** protobuf's explicit presence documentation.
   **Confidence:** high.
4. **A nullable field with null meaning Keep.** The third state is lost.
   **Confidence:** high.
5. **Nested `Optional<Optional<T>>`** (outer empty = Keep, inner empty = Clear).
   Expresses all three and reads badly. The `Optional` javadoc does not forbid it,
   but says "Optional is primarily intended for use as a method return type where
   there is a clear need to represent 'no result,' and where using `null` is
   likely to cause errors", so a nested optional used as a field is against its
   stated purpose. **Confidence:** medium, because the javadoc's API note is about
   intent, not a prohibition.

### 3.4 Cost per kind

One carrier type (generic, shared by all kinds) plus one field per record
component. Per kind of eleven, the recurring cost is a `switch` per field in the
adapter's UPDATE builder. That is the same order of work as writing the
`INSERT`/`UPDATE` column list the adapter needs anyway, so the marginal cost is
the merge function, not the SQL.

**Confidence.** Medium, this is an estimate, not a measurement.

---

## 4. What each shape costs at the change log

### 4.1 A diff yields the field list for free

**Claim.** A per-field patch, carried to the port, already names the changed
fields. The change log row can carry that list with no comparison step, and can
distinguish "touched and set to the same value" from "not touched", which is
jOOQ's touched-versus-modified distinction.

**Sources.** jOOQ internal flags page (item 1.3) for the distinction existing and
being useful; the change-log requirement is the map's, not a source's.

**Support.** Interpretation, flagged.

**Confidence.** High that the list is available; medium that the distinction is
worth a column in the log.

### 4.2 A whole record needs a read to produce the same list, and needs it anyway

**Claim.** A whole-record update can also produce per-field rows, because the
adapter must read the current row before writing (to check integrity, and to
build a log row at all). The comparison is then a diff of two records. jOOQ's
`get`/`original` pair is exactly this. Hibernate states the requirement
explicitly: a detached entity reattached with `Session.update` needs
`@SelectBeforeUpdate` before changed-columns SQL can be produced.

**Sources.** [Hibernate `@DynamicUpdate`
javadoc](https://docs.jboss.org/hibernate/orm/6.6/javadocs/org/hibernate/annotations/DynamicUpdate.html),
[jOOQ internal
flags](https://www.jooq.org/doc/latest/manual/sql-execution/crud-with-updatablerecords/internal-flags/).

**Support.** Direct evidence: "When detached entities are reattached using
`Session.update(Object)`, the entity must also be annotated `@SelectBeforeUpdate`
for this annotation to have any effect."

**Confidence.** High.

**Consequence.** The saving that looks like the diff's main advantage, "the store
does not have to read first", does not exist in this port, because the port checks
integrity and writes a change-log row. Both shapes pay for the read.

### 4.3 Row per field versus row per change

**Claim.** Row-per-field multiplies log volume by the number of changed fields and
makes the cursor's meaning subtler (one port call produces N change-log rows, all
in one transaction). Row-per-change with a field list keeps one row per port call,
which matches the map's rule that the change log row for a change is written in
that call's transaction.

**Sources.** `docs/tasks/02-work-store/map.md` (in-repo requirement). No external
source addresses the log's shape; this is design reasoning.

**Support.** Interpretation, flagged.

**Confidence.** Medium. This is the item most in need of the operator's choice,
and it is outside this task's question except in one respect: the diff shape makes
row-per-field cheap and the whole-record shape makes row-per-change natural.

---

## 5. What each shape costs at integrity checking and at the fake

### 5.1 A whole record can be checked alone; a diff cannot

**Claim.** A whole record carries every required field, so the port's check is a
predicate on one value. A diff omits fields by design, so the port cannot check it
alone: it must read the current row inside the same transaction, merge, and check
the merged record. That merge is a mandatory part of the diff shape, not an
optimisation.

**Sources.** `docs/tasks/02-work-store/tasks/store-contract/task.md` item 6
(in-repo decision that integrity is checked at the port, with deferred checking
owned by `transaction-modeling`).

**Support.** Interpretation, flagged, over the shape of the proposal.

**Confidence.** High.

### 5.2 Where this touches `transaction-modeling`

Stated plainly, because the two questions meet here and neither owns the other:

- `transaction-modeling` asks whether an atomic operation may defer validation
  until the combined execution ends, so that two writes that are each invalid
  alone and valid together can be expressed.
- A diff is a value that is **invalid alone by construction**. So the deferred
  checking question and the edit-shape question are the same question seen from
  two ends: the diff shape makes "check at the end" the normal case rather than
  an exception, and the deferred-checking rule decides whether the port may check
  the merged row immediately inside its own transaction or must wait.

Consequence for the recommendation: with one port call = one transaction,
merge-then-check inside the call is sound, so a single-record patch does **not**
need deferred checking. The two questions only collide when a compound operation
spans two port calls, where the first call's patch cannot be checked until the
second call's write lands. That is `transaction-modeling`'s case, and the diff
shape is what makes it common.

**Confidence.** High on the logic, medium on how often the collision occurs,
which depends on use cases not yet named.

### 5.3 The fake the core's tests use

**Claim.** If the merge lives in `contract` as a pure function on (current record,
patch), the fake and the real adapter cannot disagree about what a patch means:
the fake applies the function to its in-memory map, the adapter applies it to the
row it read. If each implements its own merge, they can disagree, and the tests
prove nothing about the adapter. With a whole-record update there is no merge to
disagree about, which is a genuine and underrated advantage of whole records.

**Sources.** `docs/tasks/02-work-store/tasks/store-contract/task.md`
(per-kind methods, port-side integrity, and the fake that will replace the store
in core tests).

**Support.** Interpretation, flagged.

**Confidence.** High.

### 5.4 The sealed either-input adds one switch per implementation

With `NoteEdit permits NoteEdit.Replace, NoteEdit.Patch`, the adapter and the fake
each carry a two-case switch per kind. The `Replace` case still needs the read for
the change log (item 4.2), so the case buys no work, only symmetry. Eleven kinds
means eleven sealed interfaces and twenty-two case records beyond the eleven
patches.

**Confidence.** Medium: a count, not a measurement of pain.

---

## 6. Lost updates

### 6.1 The whole-record shape loses updates on every field

**Claim.** A caller that reads a record, edits one field, and later writes the
whole record overwrites every field with the values it read, including fields
another caller changed in between. This is the classic lost update, and it is
worse than one field wide.

**Support.** Researcher inference over the shape. The mechanism is not disputed in
any source here.

**Confidence.** High.

### 6.2 The diff shape loses updates only on the fields it names

**Claim.** A patch that sets only `title` cannot overwrite `body`. Applying it to
the row the store reads inside its transaction is a narrow write, so untouched
fields cannot be lost, and no version column is needed for that guarantee. A lost
update remains possible on the fields the patch does name: last writer wins, which
is the correct meaning of "set the title to this value".

**Support.** Interpretation, flagged.

**Confidence.** High on the mechanism. The conclusion that this store needs no
version column is item 6.4.

### 6.3 What Java does about it

**Claim.** The Java convention is a version value that is read with the record and
checked at write time, and the shapes that need it are the ones that write state
the caller did not read.

**Sources.**
[Jakarta Persistence 2.5 and 3.5.2](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html),
[Jakarta Persistence 3.5.5 `OptimisticLockException`](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html),
[jOOQ optimistic locking](https://www.jooq.org/doc/latest/manual/sql-execution/crud-with-updatablerecords/optimistic-locking/),
[Spring Data REST `@Version` / `If-Match`](https://docs.spring.io/spring-data/rest/docs/3.5.x/reference/html/),
[Jdbi guide](https://jdbi.org/releases/3.54.0/) (absence).

**Support.** Direct evidence:

- JPA: "An entity might have a *version*, a persistent field or property used by
  the persistence provider to perform optimistic locking" (2.5); "The entity
  version must be updated by the persistence provider each time the state of an
  entity instance is written to the database" (3.5.2); "When an optimistic lock
  failure is detected, the persistence provider must: throw an
  `OptimisticLockException` and mark the current transaction for rollback."
- jOOQ: with `executeWithOptimisticLocking`, "Prior to UPDATE or DELETE
  statements, jOOQ will run a SELECT .. FOR UPDATE statement" and "The data
  fetched with the previous SELECT will be compared against the data in the
  record being stored", throwing `DataChangedException`; with a version or
  timestamp column, "jOOQ adds a WHERE-clause to the UPDATE or DELETE statement".
- Spring Data REST: `@Version` becomes the `ETag`, and `If-Match` makes the
  request conditional: "This safeguard prevents clients from stomping on each
  other."
- Jdbi: nothing. No mention in the guide.

**Confidence.** High.

### 6.4 Which shapes need it, in this repository

**Claim.** Neither shape needs a version column to satisfy the map's success
test. The map's constraints give exactly one writer process (an exclusive
`FileChannel.tryLock` on `owner.lock`, taken before the database opens), one
writer thread, and one port call = one transaction with `BEGIN IMMEDIATE`. So
there is no second writer to race, and a patch applied inside the transaction is
serialised by construction.

What remains is stale *client* state: a form open since before another change.
The diff shape handles it correctly for untouched fields and last-writer-wins for
named fields. Whole records do not.

**Sources.** `docs/tasks/02-work-store/map.md` constraints, and
`docs/research/sqlite-mechanics.md` for the transaction and lock facts.

**Support.** Interpretation, flagged, over in-repo decisions.

**Confidence.** High on the constraint reading, medium on "no version is ever
needed", because a later use case may want a conditional transition ("complete
this task if it is still in progress"), which is an expected-state check rather
than a row version.

### 6.5 The expected-state check is the thing to keep

**Claim.** The state-machine transitions this product needs (status changes, an
owner refusing a second owner) are conditional, and a conditional write wants an
expected state, not a version counter. Adding a version column later is a schema
change and a migration; adding a precondition to one operation is not.

**Support.** Researcher inference, flagged.

**Confidence.** Medium.

---

## Recommendation

**Adopt a per-kind patch record with explicit presence, and drop the sealed
either-input.** The patch is the only edit value the port accepts. A caller that
holds a whole record gets one from a factory, `NotePatch.replacing(note)`, which
sets every field, so the operator's intent survives: a caller changes only what it
means to change, and a caller with a whole record can still pass one.

Rationale in five lines:

1. It is the only shape whose untouched fields cannot be lost, and the port reads
   the row anyway for integrity and for the change log, so the read is not new
   cost.
2. It reduces the three-state problem to one generic carrier the compiler can
   check exhaustively, instead of a convention each of eleven kinds re-invents.
3. It keeps the port's integrity check meaningful: the store merges then checks
   the merged record inside its transaction, which is the only way a partial edit
   can be checked at all.
4. It drops the case that buys nothing: the `Replace` case still needs the read,
   and it costs a sealed interface plus two case records per kind.
5. The merge lives in `contract` as a pure function, so the fake and the adapter
   cannot disagree about what a patch means.

What the recommendation gives up, stated plainly: a blind whole-record write with
no read is no longer expressible, which matters only if an import or a restore
lands. If such a use case is named, the sealed input returns with one added case,
and item 5.4 says what it costs.

### Exact method signatures

In `contract`:

```java
/** A field on a kind where the value may not be cleared. */
public sealed interface Change<T> permits Change.Keep, Change.Set {

    record Keep<T>() implements Change<T> {}

    record Set<T>(T value) implements Change<T> {
        public Set {
            Objects.requireNonNull(value, "value");
        }
    }
}

/** A field on a kind where the value may also be cleared. */
public sealed interface NullableChange<T>
        permits NullableChange.Keep, NullableChange.Set, NullableChange.Clear {

    record Keep<T>() implements NullableChange<T> {}

    record Set<T>(T value) implements NullableChange<T> {
        public Set {
            Objects.requireNonNull(value, "value");
        }
    }

    record Clear<T>() implements NullableChange<T> {}
}

/** The edit value for one note. Never carries an identity. */
public record NotePatch(Change<String> title, NullableChange<String> body) {

    public static NotePatch empty();

    /** Sets every field from a whole record, for a caller that holds one. */
    public static NotePatch replacing(Note note);

    public NotePatch withTitle(String title);
    public NotePatch withBody(String body);
    public NotePatch withoutBody();

    public boolean changesNothing();

    /** Pure merge. Validation stays the port's job, on the merged record. */
    public Note applyTo(Note current);
}
```

In `contract`, the port (edit operations only; reads and removal are
`store-contract`'s):

```java
public interface ForStoringWork {

    @CheckReturnValue
    Result<Note> storeNote(Note note);

    @CheckReturnValue
    Result<Note> updateNote(NoteId id, NotePatch patch);
}
```

Rules the signatures imply, each of which the specification must state:

1. `updateNote` reads the row inside its own transaction, merges with
   `patch.applyTo(current)`, checks integrity on the merged `Note`, writes only
   the non-`Keep` components as columns, writes one change-log row, and returns
   the stored record.
2. A patch whose `changesNothing()` is true writes no columns and no change-log
   row and returns the current record. Whether it should instead be refused is
   left to `store-contract`, because it is a policy choice, not a shape choice.
3. The identity travels as a separate parameter, so a patch is a reusable value
   that can be built and tested without a row. The alternative, an id inside the
   patch, is defensible; it prevents "which note" and "what change" from being
   passed out of step.
4. No version column, and no expected-state parameter, until a named use case
   needs a conditional transition. When one does, it is one extra parameter on
   that operation, not a column on every table.
5. Removal is untouched by this note. A note is removed rather than edited
   (`store-contract` item 5), and a patch never expresses deletion.

---

## Contradictions and disputes

1. **RFC 7386 versus RFC 7396.** The task body cites RFC 7386 for JSON Merge
   Patch. RFC 7396 is the current document, and its header reads "Obsoletes:
   7386". Both are titled "JSON Merge Patch" and describe the same format. The
   specification should cite RFC 7396 and say so, because a later reader will
   otherwise wonder which text was quoted. [RFC
   7396](https://www.rfc-editor.org/rfc/rfc7396.txt),
   [RFC 7386](https://www.rfc-editor.org/info/rfc7386/).
2. **"Partial update" at the Spring Data REST boundary versus `save(...)`
   underneath.** The reference calls `PATCH` a partial update and names
   `save(...)` as the method it invokes, which is a whole-entity merge. Both
   statements are true; the partiality is resolved into a full entity before it
   reaches the repository. Worth recording because it is the exact temptation
   this effort faces: build the diff at the surface and the whole record at the
   port.
3. **jOOQ's "changed" versus "modified".** The manual keeps two flags and offers
   both as dirty tracking, and its blog argues that updating only modified values
   can be wrong. The manual does not take a position. For this effort the flags
   settle the narrower point that a value can be set to what it already was; the
   blog is a lead, not evidence, and is not cited as authority here.
4. **Protobuf presence tables differ by syntax version** (proto2, proto3 with and
   without `optional`, editions). The consequence quoted in item 2.4 is stable
   across them: explicit presence needs a marker, implicit presence cannot
   express "cleared" separately from "never set".

## Missing evidence

- No trusted source covers change-log or audit design. Item 4 is repository
  reasoning plus the in-repo requirement, not sourced. jOOQ's `RecordListener`
  (a central hook that receives the whole `UpdatableRecord`) is the closest
  first-party shape and it audit-logs whole records, not field lists; it is
  suggestive, not decisive, and is not quoted above.
- The claim "no Java data layer takes a per-kind diff as its update parameter" is
  a reading of six APIs, not a survey. Code generators were checked only in the
  jOOQ and protobuf cases. OpenAPI generator, MapStruct, and Lombok's
  `@Builder(toBuilder = true)` were not checked for patch-type generation.
- Nothing was compiled or executed. The signatures in the recommendation are a
  proposal, not a build. In particular, `Objects.requireNonNull` in a compact
  canonical constructor and `@CheckReturnValue` on an interface method were not
  run through this project's errorprone configuration.
- The interaction between a tri-state Java patch and Jackson was not researched,
  and it is a real future risk for the API effort: with a plain record, a JSON
  member that is absent and a member that is explicitly `null` both arrive as
  `null`, so deserializing an RFC 7396 body into `NotePatch` needs a custom
  deserializer or a `JsonNode`/`Optional`-aware mapper. Flagged, not answered.
- Whether a `NotePatch` should carry a base record (the proposal's "built from an
  existing note") was not settled against a source. It is only useful if the port
  wants an expected-state check, which item 6.5 leaves open.
- The cost estimates in item 3.4 and 5.4 are estimates, not measurements.

## Next steps

1. Hand the recommendation to `store-contract` as the answer to its item 8, and
   settle the two policy questions it now owns: what a no-op patch does, and
   whether the identity is a parameter or a patch component.
2. Settle with `transaction-modeling` the one case where the two questions
   collide: a compound operation whose first call carries a patch that is invalid
   until the second call's write lands. Decide whether the port may check inside
   its own transaction for single-record operations only.
3. When the API effort starts, research the Jackson side of the tri-state carrier
   before the patch request body is designed. That is the one place this shape
   does not survive contact with the wire for free.
4. Compile the two carrier interfaces and one patch against Java 21 with the
   project's errorprone setup, and assert that a `switch` over `NullableChange`
   without a `Clear` case fails to compile. That test documents why the split
   exists.
