// Requirements catalogue rebuild for effort planner-v1.
//
// Four stages, one chain:
//   1. extraction, one child per source artifact, four at a time
//   2. snapshot, one child concatenates the parts and commits
//   3. disposition, one child per slice of the chronological catalogue, then a merge
//   4. verification, four read-only reviewers
//
// Every stage writes durable files into the repository, so a failure keeps the
// work already done and a re-run can pick up from wherever it stopped.

const ROOT = "/home/patric/Projects/workthread";
const PARTS = "docs/v1/catalog/parts";
const DISP = "docs/v1/catalog/dispositions";
const CATALOG = "docs/v1/requirements-catalog.csv";
const DIRECTORY = "docs/v1/requirements-directory.csv";
const HEADER = "id,order,source_file,line,section,title,status,target,effort,spec,note";

// Chronological source order. The specification came first; the planning tasks
// follow in the order the map records them as having been worked; the map and
// the glossary are the accumulating records at the end.
const UNITS = [
  { o: 0, u: "initial-spec", s: "spec", f: "docs/tasks/planner-v1/initial_spec.md" },
  { o: 1, u: "non-negotiables", s: "task", f: "docs/tasks/planner-v1/tasks/non-negotiables/task.md" },
  { o: 2, u: "spec-additions", s: "task", f: "docs/tasks/planner-v1/tasks/spec-additions/task.md" },
  { o: 3, u: "version-order", s: "task", f: "docs/tasks/planner-v1/tasks/version-order/task.md" },
  { o: 4, u: "stack-research", s: "task", f: "docs/tasks/planner-v1/tasks/stack-research/task.md" },
  { o: 5, u: "taskwarrior-model-research", s: "task", f: "docs/tasks/planner-v1/tasks/taskwarrior-model-research/task.md" },
  { o: 6, u: "prototype-tray-packaging", s: "task", f: "docs/tasks/planner-v1/tasks/prototype-tray-packaging/task.md" },
  { o: 7, u: "prototype-javafx-spring-jvm", s: "task", f: "docs/tasks/planner-v1/tasks/prototype-javafx-spring-jvm/task.md" },
  { o: 8, u: "prototype-ordering-engine", s: "task", f: "docs/tasks/planner-v1/tasks/prototype-ordering-engine/task.md" },
  { o: 9, u: "prototype-cli-mcp-stdio", s: "task", f: "docs/tasks/planner-v1/tasks/prototype-cli-mcp-stdio/task.md" },
  { o: 10, u: "release-plan", s: "task", f: "docs/tasks/planner-v1/tasks/release-plan/task.md" },
  { o: 11, u: "architecture", s: "task", f: "docs/tasks/planner-v1/tasks/architecture/task.md" },
  { o: 12, u: "task-model-comparison", s: "task", f: "docs/tasks/planner-v1/tasks/task-model-comparison/task.md" },
  { o: 13, u: "session-and-timetable", s: "task", f: "docs/tasks/planner-v1/tasks/session-and-timetable/task.md" },
  { o: 14, u: "decisions-estimates-time", s: "task", f: "docs/tasks/planner-v1/tasks/decisions-estimates-time/task.md" },
  { o: 15, u: "decisions-forecast-warnings", s: "task", f: "docs/tasks/planner-v1/tasks/decisions-forecast-warnings/task.md" },
  { o: 16, u: "decisions-agent-interface", s: "task", f: "docs/tasks/planner-v1/tasks/decisions-agent-interface/task.md" },
  { o: 17, u: "decisions-data-lifecycle", s: "task", f: "docs/tasks/planner-v1/tasks/decisions-data-lifecycle/task.md" },
  { o: 18, u: "decisions-service-mode", s: "task", f: "docs/tasks/planner-v1/tasks/decisions-service-mode/task.md" },
  { o: 19, u: "live-api-research", s: "task", f: "docs/tasks/planner-v1/tasks/live-api-research/task.md" },
  { o: 20, u: "live-api-choice", s: "task", f: "docs/tasks/planner-v1/tasks/live-api-choice/task.md" },
  { o: 21, u: "ui-framework-research", s: "task", f: "docs/tasks/planner-v1/tasks/ui-framework-research/task.md" },
  { o: 22, u: "ui-framework-decision", s: "task", f: "docs/tasks/planner-v1/tasks/ui-framework-decision/task.md" },
  { o: 23, u: "ui-framework-prototype", s: "task", f: "docs/tasks/planner-v1/tasks/ui-framework-prototype/task.md" },
  { o: 24, u: "child-efforts", s: "task", f: "docs/tasks/planner-v1/tasks/child-efforts/task.md" },
  { o: 25, u: "cli-surface-convention", s: "task", f: "docs/tasks/planner-v1/tasks/cli-surface-convention/task.md" },
  { o: 26, u: "spec-traceability", s: "task", f: "docs/tasks/planner-v1/tasks/spec-traceability/task.md" },
  { o: 27, u: "requirements-directory", s: "task", f: "docs/tasks/planner-v1/tasks/requirements-directory/task.md" },
  { o: 28, u: "map", s: "map", f: "docs/tasks/planner-v1/map.md" },
  { o: 29, u: "context", s: "ctx", f: "CONTEXT.md" },
];

// Disposition slices. d1 is the specification on its own because it holds the
// most rows; each later slice covers whole units. Seven slices run four at a
// time. A later slice may overrule an earlier slice's verdict.
const SLICES = [
  { k: "d1", orders: [0] },
  { k: "d2", orders: [1, 2, 3, 4, 5] },
  { k: "d3", orders: [6, 7, 8, 9, 10, 11] },
  { k: "d4", orders: [12, 13, 14, 15] },
  { k: "d5", orders: [16, 17, 18, 19, 20] },
  { k: "d6", orders: [21, 22, 23, 24] },
  { k: "d7", orders: [25, 26, 27, 28, 29] },
];

const EFFORTS = [
  "scaffolding: the build, the module graph, the packaging, and the empty window that ships",
  "work-store: the local store, the record model, migrations, and the operation log",
  "cli-basics: the first surface, capture, the timer, day start, and standalone mode",
  "cli-work-model: projects, tasks, estimates, deadlines, dependencies, and states",
  "cli-planning: the timetable, time off, the session, the ordered today list, and the reasons",
  "cli-forecast: notices, slack, risk, completion projections, and overrides",
  "cli-review: review, export, backup, purge, and configuration display",
  "mcp-agent-interface: the agent tools over standard input and output",
  "daemon-api: the REST API, the event stream, the run modes, and shutdown",
  "client-shell: the client process, its window frame, and the toolkit integration",
  "client-views: the views and dialogs inside the window",
  "tray-surface: the tray icon and its menu",
  "planner-v1: the rows this planning effort discharges itself, meaning process rules, records it writes, and decisions that constrain the child efforts without being product capability",
];

function pad(n) {
  return n < 10 ? "0" + n : "" + n;
}

function partPath(u) {
  return PARTS + "/" + pad(u.o) + "-" + u.u + ".csv";
}

const ROWS_HELP =
  "Write the file with python3's csv module, in a small script under /tmp, so quoting cannot break. " +
  "Then verify: the header line is exactly the one given, python3 csv parses the file, every line number resolves " +
  "to the text you cited, ids are unique, and no title is empty.";

function specBrief() {
  return [
    "You are extracting requirement rows from the original specification of a planning effort. Read only this file.",
    "",
    "File: " + ROOT + "/docs/tasks/planner-v1/initial_spec.md",
    "Chronological order: 0",
    "Output: " + ROOT + "/" + partPath(UNITS[0]),
    "",
    "Header, exactly, as the first line:",
    HEADER,
    "",
    "Extract from these parts only, and give every row this identifier scheme:",
    "- section 2.1 capabilities: one row per capability, id SRC-2.1.<n>. Expect 25.",
    "- section 4 journeys: one row per journey, id SRC-4.<n>. Expect 28.",
    "- every rule carrying a RULE- identifier, mostly in section 5 but wherever it appears: id SRC-<the rule's own identifier>. Expect 104 in total.",
    "- section 8.8 completion signals: one row per signal, id SRC-8.8.<n>. Expect 16.",
    "- section 9 unresolved decisions: one row per decision, id DEC-<n>. Expect 16.",
    "Total expected: 189 rows. If your count differs, reconcile it against the file and report the difference; do not force the count.",
    "",
    "Columns, one per row:",
    "- id: as specified above",
    "- order: 0",
    "- source_file: docs/tasks/planner-v1/initial_spec.md",
    "- line: the line number in that file where the item starts, as an integer. Verify it with sed or the read tool before you write it.",
    "- section: the heading path, for example '4.7 Blocking' or '5.4 Eligibility'",
    "- title: the requirement as one line, in the specification's own words, trimmed to at most 200 characters with '...' if you cut it. Do not paraphrase into new wording.",
    "- status, target, effort, spec, note: leave empty",
    "",
    "Do not judge. Never mark anything superseded, duplicate, deferred, or refused; that is another pass's work. Do not assign an effort. Extract every distinct obligation; where one bullet carries two independent obligations, write two rows.",
    "",
    ROWS_HELP,
    "",
    "Report one line: '<n> rows written to <path>' plus any count that differed from the expectation.",
    "Write only your own output file. Never run git. Never edit any other file.",
  ].join("\n");
}

function taskBrief(u) {
  return [
    "You are extracting requirement rows from one planning task of a planning record. Read only this file.",
    "",
    "File: " + ROOT + "/" + u.f,
    "Chronological order: " + u.o,
    "Output: " + ROOT + "/" + partPath(u),
    "Identifier scheme: T" + pad(u.o) + "-001, T" + pad(u.o) + "-002, and so on, in file order.",
    "",
    "Header, exactly, as the first line:",
    HEADER,
    "",
    "What to extract: every settled decision, rule, or requirement the task left behind. Read the frontmatter, the question the task was asked, and every result section, usually '## Notes'.",
    "Do not extract: the open questions the task was asked to settle, options that were rejected, alternatives weighed, and measurement evidence such as timings, sizes, or quotations. A statement counts only if it now binds the product, the build, or the process.",
    "",
    "Columns, one per row:",
    "- id: T" + pad(u.o) + "-<seq>, zero-padded to three digits",
    "- order: " + u.o,
    "- source_file: " + u.f,
    "- line: the line number where the statement starts, as an integer. Verify it with sed or the read tool before you write it.",
    "- section: the heading path the statement sits under, for example 'Notes' or 'Notes > Settled answers'",
    "- title: the decision as one line, in the task's own words, at most 200 characters, with '...' if you cut it",
    "- status, target, effort, spec, note: leave empty",
    "",
    "Do not judge. Never mark anything superseded, duplicate, deferred, or refused. Do not assign an effort, and do not compare with any other file. Extract completely: one row per distinct obligation, and two rows where one paragraph carries two.",
    "",
    ROWS_HELP,
    "",
    "Report one line: '<n> rows written to <path>'.",
    "Write only your own output file. Never run git. Never edit any other file.",
  ].join("\n");
}

function mapBrief(u) {
  return [
    "You are extracting requirement rows from the decision record of a planning effort. Read only this file.",
    "",
    "File: " + ROOT + "/" + u.f,
    "Chronological order: " + u.o,
    "Output: " + ROOT + "/" + partPath(u),
    "",
    "Header, exactly, as the first line:",
    HEADER,
    "",
    "Extract from exactly two sections and nothing else:",
    "- '## Decisions so far': one row per bullet, in order, id MAP-001 through MAP-069. Expect 69.",
    "- '## Constraints': one row per bullet, in order, id MAP-C01 through MAP-C14. Expect 14.",
    "Do not extract from Destination, Non-goals, Non-negotiable facts, Fog, Child efforts, the stage sections, Releases, or Requirements traceability.",
    "",
    "Columns, one per row:",
    "- id: MAP-<nnn> or MAP-C<nn>, as above",
    "- order: " + u.o,
    "- source_file: " + u.f,
    "- line: the line number where the bullet starts, as an integer",
    "- section: 'Decisions so far' or 'Constraints'",
    "- title: the decision as one line, at most 200 characters, in the map's own words with '...' if you cut it",
    "- status, target, effort, spec, note: leave empty",
    "",
    "Do not judge. Never mark anything superseded, duplicate, deferred, or refused. Do not assign an effort.",
    "",
    ROWS_HELP,
    "",
    "Report one line: '<n> rows written to <path>'.",
    "Write only your own output file. Never run git. Never edit any other file.",
  ].join("\n");
}

function ctxBrief(u) {
  return [
    "You are extracting the product vocabulary of a planning effort. Read only this file.",
    "",
    "File: " + ROOT + "/" + u.f,
    "Chronological order: " + u.o,
    "Output: " + ROOT + "/" + partPath(u),
    "",
    "Header, exactly, as the first line:",
    HEADER,
    "",
    "Extract one row per defined term in the glossary, in the order the file defines them. Expect 38.",
    "Identifier: CTX-<the term, lowercased, spaces turned into hyphens>.",
    "",
    "Columns:",
    "- id: CTX-<term>",
    "- order: " + u.o,
    "- source_file: " + u.f,
    "- line: the line number where the term is defined",
    "- section: the heading it sits under",
    "- title: the term and its definition's first sentence on one line, at most 200 characters",
    "- status, target, effort, spec, note: leave empty",
    "",
    "Do not judge. Never mark anything superseded, duplicate, deferred, or refused. Do not assign an effort.",
    "",
    ROWS_HELP,
    "",
    "Report one line: '<n> rows written to <path>'.",
    "Write only your own output file. Never run git. Never edit any other file.",
  ].join("\n");
}

function extractionBrief(u) {
  if (u.s === "spec") return specBrief();
  if (u.s === "map") return mapBrief(u);
  if (u.s === "ctx") return ctxBrief(u);
  return taskBrief(u);
}

function concatBrief() {
  return [
    "You are freezing the extracted requirements catalogue as one file and committing it as a snapshot.",
    "",
    "Inputs: the CSV parts in " + ROOT + "/" + PARTS + "/, one per source, named <order>-<slug>.csv.",
    "Outputs:",
    "1. " + ROOT + "/" + CATALOG + " with the header",
    "   " + HEADER,
    "   and then every part's rows concatenated in ascending order of the file's leading two digits. Copy rows verbatim; do not renumber ids, do not deduplicate, do not reorder within a part, do not edit any title.",
    "2. " + ROOT + "/docs/v1/catalog/manifest.md, a markdown table with one line per part: order, unit slug, source file, row count, and the part path. Add the total row count.",
    "",
    "Write both with a python3 script under /tmp using the csv module.",
    "",
    "Checks before you commit: every part file parses; the concatenation's row count equals the sum of the part row counts; ids are unique across the whole catalogue; the order column is non-decreasing down the file; no part was skipped, including the last one.",
    "",
    "Then commit exactly these two files and nothing else, on the current branch (main):",
    "  git add " + CATALOG + " docs/v1/catalog/manifest.md " + PARTS,
    "  git commit -m \"Add the extracted requirements catalogue snapshot\"",
    "Do not amend, rebase, push, or touch any other file. If the commit fails, report the exact git error and stop.",
    "",
    "Report one line: '<n> rows in <m> parts, committed as <short sha>'.",
  ].join("\n");
}

function dispositionBrief(slice) {
  return [
    "You are resolving the disposition of a slice of a chronological requirements catalogue.",
    "",
    "Catalogue: " + ROOT + "/" + CATALOG + ". Read all of it. It is ordered by when each statement was written: order 0 is the original specification, orders 1 to 27 are the planning tasks in the order they were worked, 28 is the effort map, and 29 is the glossary. Staleness flows one way: something written later can replace, narrow, or contradict something written earlier, never the reverse.",
    "",
    "Your slice: orders " + slice.orders.join(", ") + ". You own every row carrying one of those orders.",
    "You may also rewrite a row of an EARLIER order when a row in your slice replaces it; for example the map restating and replacing a specification decision. You may never decide a row of a later order: a later slice decides that itself and its verdict wins.",
    "",
    "Output: " + ROOT + "/" + DISP + "/" + slice.k + ".csv",
    "Header: id,status,target,effort,note,deciding_order",
    "One line per row you decide: every row of your slice, plus every earlier row you flip.",
    "deciding_order is the order of your slice.",
    "",
    "status is one of:",
    "- kept: still binding on the product. effort is then mandatory.",
    "- superseded: a row in this catalogue states the same subject later and wins. target is that row's id, and that row's order must be the same as or later than the row's own order. Say in note what the later row changed.",
    "- duplicate: another row states the same requirement. target is the row that states it best, normally the earlier canonical statement. Say why in note.",
    "- refused: consciously not adopted. Say what replaces it, or why it was rejected, in note.",
    "- deferred: post-v1. Say which release or which later effort takes it, in note.",
    "note is required for every status except kept, one sentence, naming the deciding row's id where one exists.",
    "",
    "effort, when the row is kept, is the FIRST child effort that must satisfy the row, not every surface that later shows it. Use exactly one of these slugs:",
    EFFORTS.map(function (e) {
      return "  - " + e;
    }).join("\n"),
    "",
    "Judgement rules:",
    "- Look backwards only. Decide each row by comparing it with rows at lower or equal order, plus the other rows of your own slice. Never cite a target you have not read.",
    "- A row is kept unless a later row contradicts it, replaces it, subsumes it, or narrows it into something different. Silence is not supersession, and restating the same thing in different words while both can hold at once is not supersession either.",
    "- Prefer one disposition per row. When a specification rule survives only in part, mark it superseded and say in note which part survives.",
    "- Where a title is ambiguous or truncated, open source_file at line and read the surrounding lines. That is the primary evidence; the title is a pointer to it.",
    "- Do not invent new requirements, and do not merge two rows into one.",
    "",
    "Verify before you finish: your file parses with python3 csv; every id you wrote exists in the catalogue; every target exists; every kept row has an effort from the list; every superseded or duplicate row's target order respects the direction rules; every non-kept row has a non-empty note. Write the file with python3's csv module.",
    "",
    "Report one line: '<n> rows decided for orders <list>, <k> earlier rows flipped, <m> kept'.",
    "Write only your own output file. Never run git. Never edit any other file.",
  ].join("\n");
}

function mergeBrief() {
  return [
    "You are applying disposition deltas onto the catalogue to produce the final requirements directory.",
    "",
    "Inputs:",
    "- " + ROOT + "/" + CATALOG + ", the snapshot, in chronological order.",
    "- " + ROOT + "/" + DISP + "/d1.csv through d7.csv, in that slice order.",
    "",
    "Rule: for any row id, the delta from the highest slice number wins. A slice may decide a row of its own orders or flip a row of an earlier order; the result is the same, the later slice decides.",
    "",
    "Output: " + ROOT + "/" + DIRECTORY + ", with the full header",
    "  " + HEADER,
    "and every catalogue row, in catalogue order, unchanged in id, order, source_file, line, section and title, with status, target, effort and note taken from the winning delta and spec left empty.",
    "",
    "Write it with a python3 script under /tmp using the csv module. Then report, without fixing anything:",
    "- total rows",
    "- ids in the catalogue that no delta decided, with their count and the list",
    "- delta ids that are not in the catalogue",
    "- rows decided by a flip rather than by their own slice, with the count",
    "- counts per status",
    "- kept rows without an effort, superseded or duplicate rows whose target is missing, non-kept rows without a note",
    "",
    "Do not commit. Do not edit the catalogue, the parts, or any delta file.",
    "",
    "Report the numbers above as a short block.",
  ].join("\n");
}

function integrityBrief() {
  return [
    "Read-only review of a generated requirements directory. Do not edit anything.",
    "",
    "File: " + ROOT + "/" + DIRECTORY + ". It has the header: " + HEADER,
    "",
    "Report defects only, each with the row id and the CSV line number. Check:",
    "1. The header is exactly as above, with no stray whitespace.",
    "2. Ids are unique.",
    "3. The order column is non-decreasing down the file and uses only the thirty unit orders 0 to 29.",
    "4. Every title is non-empty and fits on one line.",
    "5. Every line value is an integer that falls inside its source_file; open a sample to confirm the citation resolves.",
    "6. Every status is one of kept, superseded, duplicate, refused, deferred.",
    "7. Every kept row has an effort from this list: scaffolding, work-store, cli-basics, cli-work-model, cli-planning, cli-forecast, cli-review, mcp-agent-interface, daemon-api, client-shell, client-views, tray-surface, planner-v1.",
    "8. Every superseded and duplicate row has a target that exists in the file.",
    "9. Every superseded, duplicate, refused and deferred row has a non-empty note.",
    "10. The spec column is empty everywhere.",
    "",
    "End with one line: 'integrity: <n> defects'.",
  ].join("\n");
}

function chronologyBrief() {
  return [
    "Read-only review of the replacement graph in a requirements directory. Do not edit anything.",
    "",
    "File: " + ROOT + "/" + DIRECTORY + ", header: " + HEADER + ". Rows are ordered by when they were written: order 0 is the original specification, 1 to 27 the planning tasks, 28 the map, 29 the glossary.",
    "",
    "Check and report violations with ids and orders:",
    "1. Every superseded row points at a target whose order is equal to or greater than its own. A replacement never points backwards in time.",
    "2. Every duplicate row points at a target whose order is equal to or less than its own.",
    "3. No id points at itself.",
    "4. Follow every superseded chain to its end: no chain may cycle, and every chain must terminate at a row that is kept, refused, or deferred, not at another superseded row.",
    "5. List every superseded row whose target sits in the same order (same source file) so a human can check those by hand, and every duplicate pair where both rows are kept.",
    "6. Report any row whose note names a row id that does not exist.",
    "",
    "End with one line: 'chronology: <n> defects'.",
  ].join("\n");
}

function fidelityBrief() {
  return [
    "Read-only source-fidelity review of a requirements directory. Do not edit anything.",
    "",
    "File: " + ROOT + "/" + DIRECTORY + ", header: " + HEADER + ". Each row cites source_file and line.",
    "",
    "Sample forty rows spread across all orders, at least one per order. For each: open source_file, read the cited line and two lines either side, and judge whether the title states what the source states there, without adding an obligation or dropping one.",
    "",
    "Report per mismatch: the id, order, file, line, the source text, and what the title claims instead. Count a mismatch only when the source at that place does not state the row's subject; a paraphrase, a shortened quote, or a trimmed example is acceptable.",
    "",
    "Also report any row whose line number does not exist in the file.",
    "",
    "End with one line: 'fidelity: <n> mismatches in <m> sampled rows'.",
  ].join("\n");
}

function coverageBrief() {
  return [
    "Read-only coverage review: did the concatenation drop or renumber anything? Do not edit anything.",
    "",
    "Parts: " + ROOT + "/" + PARTS + "/, one CSV per source unit, named <order>-<slug>.csv.",
    "Catalogue: " + ROOT + "/" + CATALOG + ".",
    "Directory: " + ROOT + "/" + DIRECTORY + ".",
    "",
    "Check and report:",
    "1. Every part file's rows appear in the catalogue exactly once, with the same order, line and title.",
    "2. The catalogue's row count equals the sum of the part counts, and the counts per order match the part files.",
    "3. No catalogue row carries an order with no part file, and no part file is missing from the catalogue.",
    "4. The directory has exactly the catalogue's rows, with the same ids in the same order.",
    "5. List every part with its row count, as a table with order, unit, path, and rows.",
    "",
    "End with one line: 'coverage: <n> defects across <m> parts'.",
  ].join("\n");
}

const summary = { extract: [], disposition: [], merge: "", verify: [] };

// Stage 1: extraction, four children at a time.
for (let start = 0; start < UNITS.length; start += 4) {
  const batch = UNITS.slice(start, start + 4);
  const launches = [];
  for (const u of batch) {
    launches.push({
      key: "extract-t" + pad(u.o),
      label: "Extract order " + u.o + " " + u.u,
      agent: "worker",
      task: extractionBrief(u),
    });
  }
  const done = await runs.all(launches);
  for (let i = 0; i < done.length; i++) {
    summary.extract.push({
      order: batch[i].o,
      unit: batch[i].u,
      ok: done[i].ok,
      report: done[i].output,
    });
  }
  emit("extracted orders " + batch.map(function (u) { return u.o; }).join(","));
}

// Stage 2: snapshot and commit.
const frozen = await runs.run("snapshot-concat", {
  label: "Concatenate parts and commit the snapshot",
  agent: "worker",
  task: concatBrief(),
});
summary.snapshot = { ok: frozen.ok, report: frozen.output };
emit("snapshot " + (frozen.ok ? "done" : "failed"));

// Stage 3: dispositions, four at a time, then one merge.
if (frozen.ok) {
  for (let start = 0; start < SLICES.length; start += 4) {
    const batch = SLICES.slice(start, start + 4);
    const launches = [];
    for (const s of batch) {
      launches.push({
        key: "dispose-" + s.k,
        label: "Dispose slice " + s.k + " orders " + s.orders.join(","),
        agent: "worker",
        task: dispositionBrief(s),
      });
    }
    const done = await runs.all(launches);
    for (let i = 0; i < done.length; i++) {
      summary.disposition.push({
        slice: batch[i].k,
        ok: done[i].ok,
        report: done[i].output,
      });
    }
    emit("disposed slices " + batch.map(function (s) { return s.k; }).join(","));
  }

  const merged = await runs.run("dispose-merge", {
    label: "Apply deltas into the final directory",
    agent: "worker",
    task: mergeBrief(),
  });
  summary.merge = { ok: merged.ok, report: merged.output };

  // Stage 4: four read-only reviewers.
  const reviews = await runs.all([
    { key: "verify-integrity", label: "Verify file integrity", agent: "reviewer", task: integrityBrief() },
    { key: "verify-chronology", label: "Verify replacement chronology", agent: "reviewer", task: chronologyBrief() },
    { key: "verify-fidelity", label: "Verify source fidelity sample", agent: "reviewer", task: fidelityBrief() },
    { key: "verify-coverage", label: "Verify coverage against the parts", agent: "reviewer", task: coverageBrief() },
  ]);
  for (const r of reviews) {
    summary.verify.push({ ok: r.ok, findings: r.output });
  }
}

emit("chain finished");
return summary;
