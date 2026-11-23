# Releasing

The version rule and the release ritual. The build enforces the parts it can;
this document holds the part a person has to do.

## One version source

`gradle.properties` holds `version=0.1.0`, and nothing else holds the number:

- Every module inherits it, so each jar is named after it (`contract-0.1.0.jar`
  and so on).
- Every jar reports it. Its manifest carries `Implementation-Title` (the module)
  and `Implementation-Version` (the number), so a consumer reads the version from
  the artifact rather than from a build script.
- The client's window label reads it. Gradle generates
  `workthread-version.properties` for the client from the property, and
  `WorkthreadApplication` renders its label as `workthread <version>`.
- `./gradlew verifyVersion` refuses a jar that reports another number or no
  number, and refuses any file under a module's `src` that restates the number.
  It runs inside `./gradlew build`.

Bumping the version is one edit: the `version` property. The jar names, the
manifests, and the window label all follow.

## Who tags, and when

The maintainer tags. That is the repository owner, Patric Plattner, or whoever
the owner has handed release duty to. No automated job tags, and the pipeline
never creates a tag.

A release is tagged when all of these hold:

1. Its scope is closed: the effort whose work the release ships has landed.
2. `./gradlew build` is green on all three pipeline legs.
3. The contract API snapshot is committed, and `./gradlew
   verifyContractApiSnapshot` is green, so the tag's exported surface matches
   what a consumer builds against.
4. The `version` property holds the number the release carries.

The scaffolding effort does not tag 0.1.0. It ships the build, the modules, and
the image proof, not product behaviour; 0.1 closes after the command line
effort, and that close is when its tag is cut. No task in this repository
creates a tag.

## The tag convention

An annotated tag, never a lightweight one: the annotation records who tagged and
when, which is what makes "which commit is 0.1.0?" answerable from the tag alone.

The tag name is `v` followed by the version in `gradle.properties`, exactly:
version `0.1.0` is tag `v0.1.0`. The tag is the number's only other spelling, and
the two never differ.

```sh
git tag -a v0.1.0 -m "workthread 0.1.0"
git push origin v0.1.0
```

## The contract API snapshot

`contract/api-snapshot.txt` holds the exported surface of `contract`: every
exported package, and every public and protected type and member in it, one
element per line.

- `./gradlew check` compares the built jar against that file, and fails when an
  element disappears or changes shape, naming what changed.
- Adding to the surface passes the check, because a `0.x` surface may still grow.
  Run `./gradlew generateContractApiSnapshot` and commit the file, so the next
  removal of that element fails.
- Removing from the surface drops something a consumer had. That is allowed at
  `0.x`, but the snapshot's diff is the record of it: regenerate the file rather
  than delete lines by hand, and say so in the release note.

The file carries no version. It describes one surface, and a version bump does
not touch it.
