# Plan: update annotation features for the modernized Annotation API (issue #42)

- **Ticket**: [osprey-dcs/dp-desktop-app#42](https://github.com/osprey-dcs/dp-desktop-app/issues/42)
- **Proto change**: [dp-grpc#132](https://github.com/osprey-dcs/dp-grpc/issues/132), merged as
  [dp-grpc#145](https://github.com/osprey-dcs/dp-grpc/pull/145). Companion
  [dp-grpc#143](https://github.com/osprey-dcs/dp-grpc/issues/143) removed `DataValue.ValueStatus`
  in the same release.
- **Service change**: [dp-service#248](https://github.com/osprey-dcs/dp-service/issues/248), all
  four phases merged (PRs #256, #261, #263, #264).
- **Upstream breaking-change inventory**:
  [`dp-grpc plan/tickets/132/release-notes.md`](https://github.com/osprey-dcs/dp-grpc/blob/main/plan/tickets/132/release-notes.md)
  — the authoritative upgrade checklist. This plan does not restate it; it says what *this app* must do.
- **Status**: scoped 2026-09-11 against dp-desktop-app `9cfecf4`, dp-grpc `6dfff3f`, dp-service
  `fddf692` (`main`). Findings below were verified by compiling this app against those siblings,
  not read off the protos.
- **First plan document in this repo** — establishes the `plan/tickets/<n>/plan.md` convention
  already used by dp-grpc and dp-service.

## Overview

dp-grpc #132 modernized the DataSet / Annotation / Calculations APIs — the oldest generation in
`DpAnnotationService` — to the CRUD conventions the PV metadata, machine configuration, and sample
status APIs already follow. dp-service #248 implemented the service side and retargeted the
`AnnotationClient` API client. This ticket updates the GUI.

**`main` does not currently compile against the current siblings.** This is not a latent risk to
plan around; it is the present state, and it makes the repair work (Phase 1) non-optional and
non-deferrable. Everything else in this plan is elective and can be staged.

## How the breakage was measured

`mvn clean compile` against dp-grpc `6dfff3f` + dp-service `fddf692` reports **9 errors in 2
files**, every one of them the same unresolvable *type*. That count is an artifact of javac, not a
measure of the work: when a type reference cannot be resolved, attribution stops for the dependent
code, so method-resolution errors behind it are never reported. dp-service's own plan hit exactly
this and warned about it.

Resolving the type reference alone (a mechanical rename, applied to a scratch copy) exposes the
real second layer: **7 more errors in 3 files**, in two further patterns. Fixing those three
patterns compiles `src/main` clean; `src/test` then adds the same three patterns across two more
files.

So the true compile surface is **three mechanical patterns across six files** (four main, two
test) — larger than the reported 9 errors, but far smaller than the ticket's framing suggests, and
entirely mechanical. This was verified empirically: a scratch copy with only those substitutions
reaches `BUILD SUCCESS` on both `compile` and `test-compile`.

The dangerous part of this migration is not the compile errors. It is the two changes that compile
silently and change behavior, in [Phase 2](#phase-2--silent-behavior-repairs).

## Prerequisite: reinstall the siblings

The locally installed `dp-service` jar predates the #132 proto change (installed Aug 23; #132
merged Aug 27, #248's phases later still), while the installed `dp-grpc` jar is current. That
mismatched pair is what CLAUDE.md's "Dependency Updates" section warns about, and it means a
developer who skips this step sees compile errors naming classes that demonstrably exist.

```bash
cd ~/dp/dp-java/dp-service && mvn clean install -DskipTests
cd ~/dp/dp-java/dp-desktop-app && mvn clean compile
```

No version bump is involved. All three repos are already at 1.16.0 and are bumped in lockstep —
**1.16.0 *is* the modernized API**. The migration is source-only, which is precisely why a stale
jar is indistinguishable by version from a current one.

## Phase 1 — restore compilation ✅ COMPLETE (2026-09-11)

Three patterns. Mechanical, no design decisions, no behavior change. This is the whole of the
compile fix.

### P1.1 — `Annotation` is now a top-level message (19 sites, 5 files)

`QueryAnnotationsResponse.AnnotationsResult.Annotation` → `com.ospreydcs.dp.grpc.v1.annotation.Annotation`.

| File | Sites |
|---|---|
| `model/AnnotationInfoTableRow.java` | 15, 28, 47, 54, 61, 68, 78, 121 |
| `AnnotationExploreViewModel.java` | 85, 86, 89, 135, 139 |
| `DataExploreController.java` | 2172, 2173, 2176, 2200 |
| `AnnotationBuilderViewModel.java` | 182 |
| `test/model/AnnotationInfoTableRowTest.java` | 4 |

Every site writes the fully-qualified nested name inline; not one file imports it. Add a real
import per file rather than reproducing the long FQN — these lines are unreadable as they stand,
and the import is what makes the next rename a one-line change.

### P1.2 — `Annotation.comment` → `description` (3 protobuf sites)

`model/AnnotationInfoTableRow.java:35`, `AnnotationBuilderViewModel.java:191`,
`test/model/AnnotationInfoTableRowTest.java:41`.

Only these three touch the protobuf accessor. **Do not blanket-rename `comment` in this phase** —
the identifier also names view-model properties, FXML fx:ids, and a reflective JavaFX binding, none
of which the compiler checks. That cleanup is P3.1, deliberately separated.

### P1.3 — `CalculationsDataFrame` now wraps a `common.DataFrame` (8 sites)

`CalculationsDataFrame` is now `name` + `common.DataFrame frame`, replacing the
`DataTimestamps` + `repeated DataColumn` pair. One level of indirection, not a storage rewrite:
`frame.getX()` becomes `frame.getFrame().getX()`, and the write side nests a `DataFrame.newBuilder()`.

- Write: `DpApplication.java:336, 337` (in `buildCalculations`)
- Read: `model/AnnotationInfoTableRow.java:162, 163` and `AnnotationBuilderViewModel.java:228, 229`
  — near-identical duplicates; consolidate them into one conversion helper rather than fixing twice
- Tests: `DpApplicationParamsTest.java:171, 173, 174, 175, 176`;
  `AnnotationInfoTableRowTest.java:30, 32`

Two invariants to preserve through this edit, both already covered by existing tests:

- **`buildCalculations(null)` and `buildCalculations(List.of())` must keep returning `null`**, not
  an empty `Calculations`. This is a documented NPE regression guard in `DpApplicationParamsTest`.
  The fix must not start unconditionally constructing a `DataFrame`.
- **`SampleStatusFrame.getDataTimestamps()` is a different message and must not be touched.**
  `DpApplication.java:309` and `DpApplicationParamsTest.java:217` match the same grep but belong to
  the sample status API, which #132 did not change. A careless global substitution breaks the
  feature added in #37.

`DataFrameDetails` stays flat (`name`, `timestamps`, `dataColumns`). It is constructed from three
places, one of which is the Excel import path feeding `DataImportResult.DataFrameResult` — a
dp-service type that #248 did **not** change. Keeping `DataFrameDetails` flat keeps that path and
`CalculationFrameDetailsDialogController` insulated from the proto change.

### Phase 1 exit criteria — met

`mvn clean test` green: **138 tests, 0 failures, 0 errors**, `BUILD SUCCESS`. Both guarded
invariants verified after the edit — `buildCalculations`'s null/empty early return is untouched and
no `DataFrame` is constructed on that path, and `git diff` contains no sample-status or
`SamplingClock` lines, so `DpApplication:309` / `DpApplicationParamsTest:217` were not caught by the
`DataTimestamps` rename.

One deviation from the written scope, all within P1.3: the two duplicate read sites were
consolidated into `DataFrameDetails.fromCalculationsDataFrame(frame)` — a static factory on the
target type — as the plan directed, rather than fixed twice in place. `DataFrameDetails` itself
stays flat, so the Excel import path and `CalculationFrameDetailsDialogController` are untouched.

Original criteria: `mvn clean test` green. No behavior change intended; the existing tests
(`AnnotationInfoTableRowTest`, `DpApplicationParamsTest`, `DatasetInfoTableRowTest`) are the guard,
and `ViewLoadSmokeTest` catches FXML fallout.

## Phase 2 — silent behavior repairs ✅ COMPLETE (2026-09-11)

These compile clean and are wrong at runtime. **This phase is the actual point of the ticket**;
Phase 1 only makes the app build again.

### P2.1 — `queryAnnotations` no longer returns Calculations content

`Annotation.calculations` is now populated by `getAnnotation()` **only**. `queryAnnotations()`
deliberately leaves it empty and returns `calculationsId` alone, so listing annotations does not
drag their full column sets along. `calculationsId` doubles as the presence indicator: empty means
no calculations; non-empty with empty content means "not fetched by this method".

The app reads the embedded content in two places, both fed exclusively by `queryAnnotations`:

- `model/AnnotationInfoTableRow.java:80-85` — builds the "Calculations Data Frames" column
- `model/AnnotationInfoTableRow.java:151-157` — `getCalculationDataFrameByName()`, which backs the
  frame-detail dialog

**Consequence if unaddressed:** the annotation-explore Calculations column renders permanently
blank and its hyperlinks vanish — a working feature silently degrades to an empty column, with no
error anywhere. This is the single highest-value fix in the ticket.

**Repair:** fetch content on demand via the new `AnnotationClient.getCalculations(calculationsId)`
(or `getAnnotation(id)`), triggered when the user opens a frame, rather than expecting it in the
list result. The column itself can no longer list frame *names* without a fetch per row — which is
the N+1 the API change exists to remove — so it should show a calculations indicator driven by
`calculationsId`, with names resolved on open — a real UX change, not a mechanical repair. Settled
in [D1](#d1--calculations-column-shows-presence-names-resolve-on-open), which also lists the
`AnnotationInfoTableRow` and `AnnotationExploreController` sites this moves.

Note `AnnotationInfoTableRow` already reads `getDataSetIdsList()` rather than embedded DataSet
content, so the removal of the denormalized `dataSets` field costs this app nothing — the dataset
column and its hyperlinks survive untouched. Only calculations regress.

### P2.2 — queries are now paged; the app assumes complete results

`queryDataSets` / `queryAnnotations` previously returned every match in one message. They are now
paged, and **an unset `limit` means a server-configured default page size, not unbounded**.

Both list views consume the result whole and never read `nextPageToken`:

- `AnnotationExploreViewModel.java:130` — then reports `count + " results"` and "Search completed
  successfully"
- `DatasetExploreViewModel.java:130` — then reports `"Found %d dataset(s)"`

**Consequence if unaddressed:** both views silently under-report once a result set exceeds the
server default, while stating a total that is wrong. Wrong answers presented confidently, with no
error — the worst failure shape available.

The client already exposes the mechanism (`setLimit` / `setPageToken`; results carry
`nextPageToken`), and `DpApplication.queryDataSets` (1056-1070) / `queryAnnotations` (1103-1125)
simply do not plumb it. There is **no page-accumulating helper anywhere in the client** — every
caller pages manually. Settled in [D2](#d2--paging-is-transparent-but-capped): page transparently
inside `DpApplication` up to an explicit cap, and say so in the status message when the cap is hit.

The two `.get(0)` single-record lookups (`DataExploreController.java:2125, 2195`) are *not*
affected by paging — an id lookup returns one record — but they are now the wrong RPC entirely. The
annotation one is P2.3 below; the dataset one is P3.2.

### P2.3 — editing an annotation silently destroys its calculations

**Promoted from Phase 3 (see [D3](#d3--the-annotation-load-path-is-a-phase-2-correctness-fix)).**
This is a data-loss defect, not an improvement.

`saveAnnotation` is a full-replace upsert: the proto states that an update omitting calculations
CLEARS them, and names it the most costly instance of that rule. Independently,
`DataExploreController.loadAnnotationIntoBuilder` (`:2179-2195`) loads an annotation into the
Annotation Builder via `queryAnnotations` + `.get(0)`.

Post-#132 those two combine into destruction of stored data:

1. The user opens an annotation that has calculations in the Annotation Builder.
2. `queryAnnotations` returns it with `calculations` empty — by design — so the builder's
   calculations list is empty.
3. The user edits an unrelated field and saves.
4. `saveAnnotation` sends the builder's complete state, which contains no calculations.
5. **The stored Calculations are destroyed**, with no error and no warning.

The user did nothing wrong, and nothing in the UI indicates a loss occurred. Before #132 this path
was safe *because* the query result carried the content that repopulated the builder; the API
change removed the thing that made it safe.

**Repair:** load through `getAnnotation(id)`, which returns Calculations content inline, so the
builder holds complete state and re-saves it faithfully. This needs a `DpApplication.getAnnotation()`
wrapper (none exists yet); the client wrapper is already present.

This is not additional work — `getAnnotation` was already required by P3.2. Only its scheduling
changes. It also resolves P2.1 for the builder path in the same edit.

Note the same full-replace hazard applies to any field the builder cannot repopulate. `getAnnotation`
fixes calculations, which is the destructive case; a broader audit of builder-vs-record field
coverage is worth doing but is not a prerequisite.

### Phase 2 exit criteria — met

`mvn clean test` green: **151 tests, 0 failures, 0 errors**, `BUILD SUCCESS` (138 before, plus 12
new paging tests and a net +1 from reworking the row-model calculations tests).

All three items landed as scoped:

- **P2.1** — the Calculations column is presence-driven per D1. `AnnotationInfoTableRow` exposes
  `getCalculationsId()` / `hasCalculations()` and no longer holds frame content;
  `getCalculationsDataFrameNames()` and `getCalculationDataFrameByName()` are gone, verified with a
  repo-wide grep. The cell factory renders one "View calculations" link per row, and
  `AnnotationExploreController.openCalculations()` fetches via `getCalculations()` on a background
  task. The column header changed from "Calculations Data Frames" to "Calculations".
- **P2.2** — `DpApplication.accumulatePages()` plus `QUERY_RESULT_CAP = 5000` and
  `PagedResult<T>`; both query wrappers return `PagedResult` and both explore view models consume
  it.
- **P2.3** — `loadAnnotationIntoBuilder` now calls the new `DpApplication.getAnnotation()` wrapper
  and branches on `isReject()` for not-found, so the builder holds complete state and re-saving no
  longer destroys stored Calculations.

Three things worth recording that the written scope did not anticipate:

1. **The multi-frame case needed a decision D1 did not settle.** D1 said names resolve on open, but
   with one link per row there is nothing to name *which* frame to open. A single-frame fetch opens
   its dialog directly; a multi-frame fetch prompts with a `ChoiceDialog` of the names the fetch
   just resolved. This keeps every frame reachable, which is what the old per-frame links provided.

2. **Both views had a *second* count display that D2 did not account for.** Fixing only the status
   message would have left `resultCountLabel` reading "5000 results" beside a status line saying the
   result was capped — the same wrong-total bug D2 exists to remove, just relocated. In
   annotation-explore that label is driven by a `searchResults` list listener, so truncation is
   carried in a `lastResultTruncated` field set before the results are added and reset before both
   `clear()` calls (otherwise an emptied list inherits the previous search's truncation). In
   dataset-explore the label was a raw `resultCountProperty().asString().concat(...)` binding in the
   controller, replaced with a `resultCountMessage` property so the formatting lives with the data.

3. **A failed page has to abort the whole accumulation.** `accumulatePages()` throws
   `QueryFailedException` rather than breaking the loop and returning what it had — returning a
   partial list as a complete one is the same failure shape as the wrong-total bug. Covered by
   `aFailedPageAbortsTheAccumulation`.

**Deferred deliberately:** the dataset load path (`loadDatasetIntoBuilder`) still uses
`queryDataSets` + `.get(0)` rather than `getDataSet()`. It was adapted to the `PagedResult` shape
because it no longer compiled otherwise, with a comment pointing at P3.2. Unlike the annotation
path it loses no data, so it stays P3.2 rather than being pulled forward.

Original criteria: `mvn clean test` green; the Calculations column and the annotation load path
both correct against the modernized API.

## Phase 3 — adopt the modernized surface ◐ PARTIAL (2026-09-12)

Elective. Each item is independently schedulable and none blocks the others.

**P3.1, P3.2 and P3.5 are done** — the items that complete the #132 migration rather than add
capability. **P3.3 and P3.4 were deliberately not done**: per [D4](#d4--no-delete-or-patch-ui-restore-existing-functionality-only)
they are new capability (new entity fields, new export inputs and UI), not restoration, and belong
in their own tickets. See "Phase 3 outcome" below.

### P3.1 — finish the `comment` → `description` rename

Phase 1 renames only the protobuf accessors. The identifier survives throughout the app, and one
instance is a **reflective** binding the compiler cannot check:

- `AnnotationExploreController.java:102` — `new PropertyValueFactory<>("comment")`, a string that
  resolves to `AnnotationInfoTableRow.getComment()` by reflection. Renaming the row property
  without this line yields a **silently blank column**, not a compile error.
- `AnnotationExploreViewModel.java:27, 210-212` — `nameCommentEventText`, whose name encodes both
  the old `comment` field and the `event.description` field that #132 removed from the proto
  altogether
- `DataExploreController.java:1169, 1198, 1214`; `DpApplication.java:1077, 1093, 1108` — view-model
  and wrapper parameter names
- The matching FXML labels and `fx:id`s

Do this as one deliberate pass with `ViewLoadSmokeTest` as the guard, or not at all — a partial
rename is worse than none, because it splits one concept across two names.

### P3.2 — use the new single-record getters

`getDataSet(id)`, `getAnnotation(id)`, and `getCalculations(calculationsId)` all exist on
`AnnotationClient` today. Phase 2 already consumes two of them —
`getAnnotation` in P2.3 and `getCalculations` in P2.1 — leaving one site here:

- `DataExploreController.java:2109-2125` (dataset load) → `getDataSet`

It currently emulates a single-record fetch with `queryDataSets(id, null, null, null)` plus
`.get(0)`. Correct, but it is the wrong RPC: a query for a one-record lookup, and the "should be
only" comment at `:2125` is an assumption the dedicated getter makes structurally true. Needs a
`DpApplication.getDataSet()` wrapper.

Purely a cleanup — unlike the annotation path, nothing is lost by leaving it, because the Dataset
Builder's fields all survive a `queryDataSets` round trip.

### P3.3 — carry the new entity fields

- `modifiedBy` on both saves. `DpApplication` hardcodes `ownerId = "demo-user"` at 1039-1046 and
  1086-1097; `modifiedBy` is a distinct concept (last writer, not owner) and both client params
  records accept it. The PV-metadata and machine-configuration views already have a Modified By
  field — follow that precedent.
- `DataSet` gains `tags`, `attributes`, `createdTime`, `updatedTime`, `modifiedBy`. The Dataset
  Builder has no tags/attributes inputs; `TagsListComponent` and `AttributesListComponent` exist and
  are the obvious fit, subject to the Critical Integration Pattern in CLAUDE.md.
- `SaveAnnotationApiResult.calculationsId` is returned and ignored
  (`DataExploreController.java:1232-1246`). It is the addressing key for `getCalculations`,
  `CalculationsSpec`, and provenance links — capture it.

Both client params records ship **compatibility constructors** that omit these fields, which is
exactly why the app compiles while silently writing `null` for all of them. Compiling is not
evidence of correctness here.

### P3.4 — export improvements

- `ExportDataRequestParams` gained a 4-arg form taking `List<DataBlock>` (raw protobuf) for inline
  ad-hoc export. This would lift the "dataset must be saved first" precondition at
  `DataExploreController.java:1856-1862`.
- `calculationsSpec` is hardcoded `null` at `DataExploreController.java:1871`, so **calculations are
  never exported**, even for annotations that have them.
- Document the upstream restriction: CSV and XLSX can only represent scalar columns; array, image,
  and struct columns are HDF5-only. The app offers all three formats from one combo with no
  indication of this.

### P3.5 — housekeeping

- `model/CalculationsDetails.java` is **unreferenced dead code**. Either wire it to the new
  `calculationsId` or delete it; leaving an unused model class beside a changing API invites
  someone to "fix" it.
- `QueryAnnotationsParams.eventCriterion` is a **dead field in the client** — never read by the
  request builder, so setting it is silently ignored. The app does not set it; do not start.

### Phase 3 outcome — P3.1, P3.2, P3.5 complete; P3.3, P3.4 deferred by D4

`mvn clean test` green: **154 tests, 0 failures, 0 errors** (151 after Phase 2, plus 3 new binding
guard tests).

- **P3.1 — done, carried through to user-visible labels.** The rename covers the row-model property,
  both view models, the `fx:id`s in `annotation-explore.fxml` and `data-explore.fxml`, the reflective
  `PropertyValueFactory` string, and — by explicit choice — the "Comment:" field label, the "Comment"
  column header and the "Name / Comment / Event" search label. A repo-wide case-insensitive grep for
  `comment` across `src/**/*.java` and `src/**/*.fxml` returns nothing but one prose mention in the
  new test's javadoc.
- **P3.2 — done.** New `DpApplication.getDataSet()` wrapper; `loadDatasetIntoBuilder` now uses it and
  branches on `isReject()` for not-found instead of emulating a single-record fetch with
  `queryDataSets(id, null, null, null)` + `.get(0)`.
- **P3.5 — done.** `model/CalculationsDetails.java` deleted after confirming every reference to the
  identifier was inside the file itself (the apparent hits elsewhere are
  `showCalculationsDetailsDialog`, a different symbol). Its CLAUDE.md section went with it. The
  `eventCriterion` half needed no action — the app never set it.

**On the guard test, and a correction worth recording.** `AnnotationInfoTableRowBindingTest` asserts
that every `PropertyValueFactory` string resolves against the row model. The first version asserted
only non-null, and mutation testing showed that was **worthless**: `PropertyValueFactory` falls back
from `someProperty()` to `getSome()`, so a half-finished rename still resolves. A probe established
the real semantics — an unresolvable name returns null, a resolvable one returns the value — so the
test now asserts each binding's *expected value*. Verified by mutation: renaming the row property
with no accessor left under the old name fails the test with a message naming
`setupTableColumns()`. Two earlier mutation attempts passed and were rejected as invalid rather than
taken as evidence the guard worked.

**Not done, and why.** P3.3 (`modifiedBy` on both saves, `DataSet` tags/attributes/timestamps,
capturing `SaveAnnotationApiResult.calculationsId`) and P3.4 (inline ad-hoc export, exporting
calculations, documenting the CSV/XLSX scalar-only restriction) both add capability the app never
had. D4's restore-only principle governs Phase 3 explicitly, and both items want UI design decisions
— new Dataset Builder inputs, a changed export precondition — that deserve their own tickets rather
than riding along on a migration. Note the plan's own warning still stands for whoever picks up
P3.3: the client params records ship compatibility constructors that omit these fields, so the app
compiles while writing `null` for all of them. Compiling is not evidence of correctness there.

## What dp-service still owes this app

dp-service #248 decision **D17** limited client scope to *get* wrappers. Verified against
`AnnotationClient` on `main`:

**Present, no dp-service work needed:** `getDataSet`, `getAnnotation`, `getCalculations`; flat
`saveDataSet` with tags/attributes/modifiedBy; `saveAnnotation` with `description` and
`calculationsId` returned; `limit`/`pageToken`/`nextPageToken` on both queries; inline `dataBlocks`
on export.

**Missing, and needed only if the corresponding UI is wanted:**

1. **`deleteDataSet` / `deleteAnnotation` wrappers** (plus `patchDataSet` / `patchAnnotation`, which
   are deferred server-side stubs and would return an error if called). D17 defers these
   deliberately — no entity has a client delete wrapper today. **Required only if #42 adds delete
   UI; see Q4.**
2. **Criteria expressiveness.** The client params express at most one value per criterion and one
   criterion per type. Not reachable through them: multi-id lookups, `NameCriterion`
   (exact/prefix/contains), and `TagsCriterion` / `AttributesCriterion` on `queryDataSets`. The
   proto supports all of these. Needed only if the explore views should offer them.
3. **A page-accumulating helper.** None exists anywhere in the client. Either dp-service adds one
   or this app writes the loop (Q2).

None of these block Phases 1 and 2. **If #42 is scoped to Phases 1–2, dp-service needs no changes
at all** — which is the main scoping question below.

## Decisions

### D1 — Calculations column shows presence; names resolve on open

The annotation-explore Calculations column is driven by `calculationsId`, which `queryAnnotations`
does return: non-empty means the annotation has calculations. Frame names are no longer listed in
the column. Clicking through fetches content via `getCalculations(calculationsId)` and opens the
existing `CalculationFrameDetailsDialogController` — one request per user action, none per row.

**Why not fetch per row.** It would preserve today's appearance exactly, at the cost of rebuilding
the N+1 fan-out dp-grpc #132 deleted — and rebuilding it client-side, as serial round-trips driven
from a GUI thread, which is worse than the server-side version it replaced.

**Why not drop the column.** `calculationsId` is already in the query result, so presence is free.
Discarding it would remove a useful signal to save nothing.

**Accepted cost.** A user scanning the table sees *that* an annotation has calculations rather than
*which* frames it has. Frame names in a list column were not directly actionable — reading a frame
always required clicking through to the dialog — so the loss is one of preview, not of capability.

**Consequence for the row model.** `AnnotationInfoTableRow.getCalculationDataFrameByName()`
(`:151-157`) can no longer resolve from the embedded content it reads today. It either takes the
fetched `Calculations` as an argument or moves out of the row model entirely; the row keeps
`calculationsId` and the presence flag. `AnnotationExploreController:131/271-314` (the cell factory
and hyperlink wiring) and `:341-345` (the dialog call) change with it. The dialog itself is
insulated — it consumes `DataFrameDetails`, not protobuf.

### D2 — Paging is transparent, but capped

`DpApplication.queryDataSets()` / `queryAnnotations()` follow `nextPageToken` and accumulate pages
internally, so the explore views keep receiving a single list and need no paging UI. Accumulation
stops at an explicit cap; when the cap is reached the result is marked as truncated and the view
says so ("showing first N of more") instead of reporting the count as a total.

**Why not uncapped.** Transparent paging alone would restore today's behavior and keep both views
correct, but it moves the unbounded read from the server to the client — the exact failure server
paging was introduced to prevent. The cap is what makes this option defensible rather than a
re-creation of the old problem one layer up.

**Why not paging UI.** Correct, but it is real work in two views for a demo application, and it
changes the interaction model of both explore screens to solve a problem the cap already bounds.
Revisit only if real result sets outgrow the cap in practice.

**Why the honest message matters.** The present bug is not that results are incomplete — it is that
the views state a wrong total with no indication anything is missing. A silent cap would reproduce
that bug at a higher threshold. Truncation must be visible in the status message, not just in the
data.

**Implementation note.** Factor the page loop as a static helper on `DpApplication`, and have it
return both the accumulated list and whether it was truncated. Nothing requiring a live service has
test coverage today, so keeping the loop static and pure is what allows it to be tested at all —
the same reasoning behind `emptyToNull` and `timestampFromInstant`. The cap value belongs beside
it as a named constant, not inline.

**The cap is 5000** (settled 2026-09-11), as `DpApplication.QUERY_RESULT_CAP`. Large enough that
ordinary demo result sets never reach it, and small enough to stay well inside the bound the cap
exists to enforce. Server-side defaults for comparison are 10000 for sample statuses and Query V2,
which the dp-service handoff notes is likely too large for record-shaped results like annotations.

### D3 — The annotation load path is a Phase 2 correctness fix

Loading an annotation through `getAnnotation(id)` rather than `queryAnnotations` + `.get(0)` moves
from Phase 3 to Phase 2, as [P2.3](#p23--editing-an-annotation-silently-destroys-its-calculations).

**Why.** Phase 3 is explicitly elective and independently schedulable. That is the wrong home for
the only change standing between a routine edit and destroyed archive data. Phase 3 might never
happen; the defect would ship.

**It is not extra work.** `getAnnotation` was already required by P3.2. Only its scheduling changes,
and it resolves P2.1's builder path in the same edit.

**Provenance, recorded honestly.** This hazard is not introduced by #42 and is not named in the
upstream release notes — it was found while auditing this app's call sites against the new
contract. It is a pre-existing full-replace hazard that #132 *sharpened*, by removing the embedded
content that had been keeping the path safe by accident. Splitting it into its own ticket would
also have been defensible; leaving it in Phase 3 would not.

### D4 — No delete or patch UI: restore existing functionality only

**#42's goal is to get the app working again with the same functionality it had.** This is a demo
application; the migration is repair, not expansion. New capability the modernized API makes
possible belongs in its own tickets.

Delete UI would require dp-service client wrappers first — #248's D17 scoped the client to *get*
wrappers, and `AnnotationClient` has no `deleteDataSet` / `deleteAnnotation` and no `Patch` methods
at all. Patch UI is not buildable in any case: `patchDataSet` / `patchAnnotation` are deferred
server-side stubs that return an error when called.

Delete also carries real semantics to design rather than plumb: `deleteDataSet` is rejected while
any Annotation references it (the UI would have to surface a referential-integrity rejection
usefully), `deleteAnnotation` is *not* blocked by incoming references and leaves `annotationIds` and
provenance links dangling by design, and it cascades to the annotation's Calculations. Destructive
operations in a demo app deserve that conversation on their own terms.

**Consistency argument, which cuts toward omission.** The PV metadata and machine configuration
views — the very views #42 is aligning to — have no delete UI either. Omitting it matches the
pattern rather than falling short of it.

**Scope consequence beyond delete.** This principle also governs Phase 3, which is elective
precisely because it is largely *new* capability (new entity fields, export improvements) rather
than restoration. Phases 1 and 2 restore the app; Phase 3 items should each be judged against the
same bar and are reasonable candidates for their own tickets.

### D5 — Criteria AND/OR change is documented, not coded around

Recorded in CLAUDE.md beside the `isReject()` guidance in API Integration Patterns; no code change.

The app sends one value per criterion from single text fields, so the AND/OR change does not affect
it. The hazard is prospective: a future multi-tag search box built as a comma-separated field would
read "tag A, tag B" as "either" — which is what it meant before #132 and what most search UIs do —
and would silently return fewer results instead of erroring.

The note also records that the dp-service client params cannot express multi-value or name-scoped
criteria, so any such UI needs client work first. That pairs the trap with its prerequisite.

**Why this belongs in #42 despite [D4](#d4--no-delete-or-patch-ui-restore-existing-functionality-only).**
It documents a future hazard rather than restoring functionality, so it sits outside the
restore-only principle. Admitted narrowly: one paragraph, directly about the API this ticket
migrates to, and the knowledge is perishable — cheapest to write while it is in hand and verified.

## Resolved questions

All five questions raised at scoping were settled 2026-09-11; the rationale lives in
[Decisions](#decisions) above.

**Q1 — Annotation-explore Calculations column. RESOLVED 2026-09-11: presence indicator, names
resolved on open.** See [D1](#d1--calculations-column-shows-presence-names-resolve-on-open).

**Q2 — How far to take paging. RESOLVED 2026-09-11: capped transparent paging.** See
[D2](#d2--paging-is-transparent-but-capped).

**Q3 — Does #42 include the full-replace data-loss fix? RESOLVED 2026-09-11: yes, promoted into
Phase 2 as P2.3.** See [D3](#d3--the-annotation-load-path-is-a-phase-2-correctness-fix).

**Q4 — Does #42 add delete/patch UI? RESOLVED 2026-09-11: no.** See
[D4](#d4--no-delete-or-patch-ui-restore-existing-functionality-only).

**Q5 — Criteria semantics. RESOLVED 2026-09-11: documented in CLAUDE.md, no code change.** See
[D5](#d5--criteria-andor-change-is-documented-not-coded-around).

## Sequencing

1. **Prerequisite** — reinstall dp-service locally. Blocks everything, including any green build.
2. **Phase 1** — restore compilation. Blocks everything else; no decisions required.
3. **Phase 2** — silent behavior repairs, now including P2.3 (per D3). All design questions are
   settled; no further decisions needed before implementation, except the D2 cap value.
4. **Phase 3** — elective; any order, independently schedulable.

**Phases 1 and 2 together are the whole of #42.** After them the app compiles and no annotation
feature silently reports wrong or empty results — which is the ticket's goal: the same functionality
as before, working again. Per [D4](#d4--no-delete-or-patch-ui-restore-existing-functionality-only)
Phase 3 is new capability rather than restoration, so its items are candidates for their own
tickets rather than work that must land here. The exception is P3.1, the `comment` → `description`
rename, which is not new capability but unfinished migration — worth doing with #42 while the
context is fresh, and cheap given `ViewLoadSmokeTest` guards the reflective binding.

## Testing

Existing coverage is genuinely useful here and should be leaned on rather than rebuilt:
`AnnotationInfoTableRowTest` covers the embedded-calculations read path (so a correct P1.3 is
verifiable), `DpApplicationParamsTest` guards `buildCalculations`, and `ViewLoadSmokeTest` catches
FXML and reflective-binding fallout — which is the only automated guard P3.1 has.

Gaps worth closing as part of this work:

- **`AnnotationBuilderViewModel.loadFromAnnotation` has no test** and carries all three Phase 1
  patterns plus the P3.2 data-loss behavior. Highest-risk unguarded code in the ticket.
- **Neither explore ViewModel is tested**, so P2.2 has no failing test to drive it.
- No test calls a `DpApplication` API wrapper (they need a live service); `DpApplicationParamsTest`
  deliberately covers only the static pure helpers. Any new paging or conversion logic should be
  factored as a static helper so it lands on the testable side of that line — the same reasoning
  that produced `emptyToNull` / `timestampFromInstant`.

## Out of scope

- **Sample Status API** — unaffected by #132. Its `SampleStatusFrame.getDataTimestamps()` is a
  different message that must not be swept up in P1.3.
- **Ingestion and query paths** — `DataImportUtility` / `DataImportResult` are unchanged by #248,
  and `DataImportController` touches no annotation API.
- **`ValueStatus` (dp-grpc #143)** — already removed upstream; this app never referenced it.
- **Delete / patch UI** — pending Q4; would require dp-service client work first.
