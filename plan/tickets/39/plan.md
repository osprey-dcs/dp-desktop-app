# Plan: query support for PV metadata, machine configuration, sample status, and V2 query API (issue #39)

- **Ticket**: [osprey-dcs/dp-desktop-app#39](https://github.com/osprey-dcs/dp-desktop-app/issues/39)
- **Sub-issue**: [#36](https://github.com/osprey-dcs/dp-desktop-app/issues/36) — see [`plan/tickets/36/plan.md`](../36/plan.md)
- **Status**: scoped 2026-09-12 against dp-desktop-app `889b5c3`, dp-grpc `6dfff3f`, dp-service `89bb822` (`main`), with the installed `dp-service-1.16.0.jar` (2026-09-11). Every upstream claim below was verified by reading the sibling source, not by reading the tickets.
- **Updated 2026-09-12 (later same day)**: dp-service #244 is **CLOSED** (PR #270, `6d2f5a9`) and the jar is reinstalled (dp-service `6d2f5a9`, dp-grpc `76dba79`). **All six tasks are now unblocked.** `mvn clean test` is green against the new jar (188 tests, 0 failures, 0 skipped). Task 6's notes below were re-verified against the shipped wrapper rather than the resolver they were originally written from — every behavior they assert is now stated in the wrapper's own javadoc.
- **Baseline**: `mvn clean compile` and `mvn clean test` are both green on `889b5c3`. Unlike #42, this ticket starts from a working build; nothing here is non-deferrable.

## Triage summary

The ticket is well-scoped and its architecture is sound. Triage changed four things:

1. **All three named upstream prerequisites have landed.** dp-service #244 closed after this plan
   was written; nothing in this ticket waits on upstream any longer.
2. **Task 2 (explore-view base extraction) should not be done as described.** Measured, it buys
   ~3% line reduction and imposes a lifecycle contract one existing view already violates. It is
   replaced below with three higher-value, lower-risk refactors.
3. **Four factual corrections to the ticket body** (§"Corrections"), one of which belongs upstream
   in dp-service #244.
4. **Five pre-existing defects** were found in the views this ticket extends — one of them a real
   leak — and should be fixed before building on them (§"Defects found in the views being
   extended").

Nothing changes the ticket's overall shape or its task decomposition beyond task 2.

## Prerequisite status — verified, not assumed

| Upstream ticket | Ticket says | Actual |
|---|---|---|
| dp-service [#243](https://github.com/osprey-dcs/dp-service/issues/243) — client wrappers for PV metadata / configuration | required | **CLOSED** (PR #247). All six wrappers + result classes present in the installed jar. |
| dp-service [#245](https://github.com/osprey-dcs/dp-service/issues/245) — empty criteria = match-all | required | **CLOSED** (PR, dp-grpc #147). All three queries now bounded by `DEFAULT_QUERY_LIMIT = 100`. |
| dp-service [#244](https://github.com/osprey-dcs/dp-service/issues/244) — `QueryClient.querySamples` | required | **CLOSED** (PR #270). Shipped as **four** V2 wrappers, not one: `querySamples` / `querySamplesStream` / `queryBuckets` / `queryBucketsStream`, plus `QuerySamplesParams` / `QueryBucketsParams` and `QuerySamplesApiResult`. Present in the installed jar. |
| dp-service [#235](https://github.com/osprey-dcs/dp-service/issues/235) — reject-vs-error | "related" | Resolved during #243 triage: both getters already classify not-found as `REJECT`. Nothing to wait for. |

Verification, rather than trusting the issue state:

- `AnnotationClient.queryPvMetadata` `:2352`, `getPvMetadata` `:2391`, `queryConfigurations` `:2440`,
  `queryConfigurationActivations` `:2490`, `getConfigurationActivationById` `:2538`,
  `getConfigurationActivationByCompositeKey` `:2562`; params records at `:1944`, `:2048`, `:2156`;
  shared `TextMatch` / `AttributeCriterion` at `:1670` / `:1700`.
- `MongoSyncAnnotationClient.java:81` `DEFAULT_QUERY_LIMIT = 100`, applied unconditionally at
  `:382`, `:720`, `:987`, `:1245`, `:1503`.
- `QueryClient` has **no** V2 method (only `queryTable`, `queryPvStats`, `queryProviders`).

**~~#244 is a hard blocker for task 6, not a convention.~~ Resolved.** The reasoning stood: this app
reaches gRPC exclusively through `ApiClient`'s typed clients (`api.queryClient.*` /
`api.annotationClient.*`, ~28 call sites) and never touches a stub or channel, so there was no
in-repo workaround. The wrapper now exists, so task 6 proceeds through the normal
`DpApplication` → `api.queryClient.querySamples()` path with no architectural exception.

### Consequence for sequencing

The ticket's stated order — "1 ‖ 3, then 2, then 4/5, then 6" — is now:

> **3 ‖ 4 ‖ 5 ‖ 6 ‖ 36 can all start immediately.** Nothing waits on upstream.

Tasks 4 and 5 were gated on #243/#245 and task 6 on #244; all three have landed. **Every task in
this ticket is now unblocked**, and #36 is complete (PR #44). The remaining sequencing constraint is
internal only: T2a/T2b should precede tasks 3-5 so the new views are written in the settled
vocabulary (see below).

## Corrections to the ticket bodies

### C1 — #244's `ResultRepresentation` note is wrong (belongs upstream)

#244 says: *"`ResultRepresentation`: default to plain `DataColumn`s with column metadata included."*

**Column metadata is never included on the querySamples path.** `ResolvedQuery.isExcludeColumnMetadata()`
is never called by `QuerySamplesUnaryDispatcher`, `AbstractQuerySamplesDispatcher`, or
`QuerySamplesStreamDispatcher` — its only consumers are the bucket path. The sample column builders
call only `.setName(...)` and `.addDataValues(...)`; no `setMetadata` call exists anywhere in the V2
samples path. The dispatcher documents this itself:

> `excludeColumnMetadata` (Q8) is inert — the tabular path carries no column metadata.
> — `QuerySamplesUnaryDispatcher.java:42`

The flag is not merely defaulted-on; it does nothing. #244's wrapper doc should say so, and this
app must not plan any feature on `DataColumn.metadata` from `querySamples`. Provenance/tags/attributes
for a PV come from `queryPvMetadata` (task 4) or from `queryBuckets`.

**Action**: ~~comment on dp-service #244 with this correction before it is implemented.~~
**Done, and accepted upstream.** The correction was posted to #244 and the shipped wrapper
incorporates it: `excludeColumnMetadata` is *deliberately not exposed* on `QuerySamplesParams`,
whose javadoc states the flag "is inert here". `QueryBucketsParams` does expose it, and documents
that it is functional there. The remaining C1 conclusion stands unchanged for this app: **plan no
feature on `DataColumn.metadata` from `querySamples`** — take PV provenance/tags/attributes from
`queryPvMetadata` (task 4) or from `queryBuckets`.

### C2 — three different page-token behaviors, not one

The ticket says these views establish "one paging UI pattern (`limit`/`pageToken`/`nextPageToken`)".
The field names are shared; the **error semantics are not**:

| API | Malformed `pageToken` |
|---|---|
| `queryPvMetadata` / `queryConfigurations` / `queryConfigurationActivations` | **silently ignored**, pagination restarts at page 1 |
| `querySampleStatuses` | **rejected** with an `ExceptionalResult` |
| `querySamples` (V2) | **rejected** — `"invalid pageToken: …"` / `"pageToken is not valid for this query type"` (`QueryV2Resolver.java:118-129`) |

A shared paging helper must not assume one of these. In practice the app always round-trips a token
it was just handed, so the divergence is latent rather than active — but a retry-on-error path, or
any future "resume this query" feature, would hit it. Document it at the helper.

**Confirmed upstream.** The shipped `QuerySamplesParams` javadoc now states the divergence itself:
"A malformed token *is* rejected here, unlike the annotation metadata queries, which silently reset
to the first page." So this is a documented contract difference, not an implementation accident that
might be normalized later — the helper has to accommodate it permanently.

### C3 — `queryProviders` cannot be paged, so "one paging pattern" cannot cover all views

The existing provider-explore view calls `DpApplication.queryProviders()` (`:1144`), which is
**unbounded, unpaged, and still on the legacy two-bucket AND/OR criteria scheme**.
`QueryProvidersRequest` has no `limit`/`pageToken` fields at all, and the response has no
`nextPageToken` — see dp-service [#265](https://github.com/osprey-dcs/dp-service/issues/265), which
also records a missing `return` after its empty-criteria reject.

So provider-explore cannot be brought onto the paging pattern without a dp-grpc proto change. Either
accept that it stays unpaged (and say so at the view), or file/land #265's proto half first. **This
plan accepts it as unpaged** and treats it as out of scope; the honest-truncation requirement below
applies only to views whose API can express truncation.

### C4 — "Explore → PVs" is already `pvMetadataMenuItem` — **DONE (task 4)**

Renamed as planned; `ViewLoadSmokeTest` validated it by loading `main-window.fxml`, which is what a
partial rename would have broken.

The ticket renames Explore→PVs to "PV Statistics" and adds a new "PV Metadata" view. The existing
menu item for the *statistics* view is already named `pvMetadataMenuItem`
(`main-window.fxml:34`, `MainController.java:35`, `MainViewModel.pvMetadataEnabledProperty()`
`:87`), with a comment at `MainController.java:40` disambiguating it from the *editor* item.

Adding a genuine PV-metadata explore view makes that name actively wrong in three files. Rename in
the same commit as the view rename: `pvMetadataMenuItem` → `pvStatsMenuItem`,
`pvMetadataEnabledProperty` → `pvStatsEnabledProperty`, `onPvMetadata` → `onPvStats`. FXML
`fx:id`/`onAction` are resolved reflectively, so a partial rename fails at **view-load time**, not
compile time — `ViewLoadSmokeTest` covers `main-window.fxml` and will catch it.

Also confirmed: the rename is semantically right, not cosmetic. `PvStats`
(`query.proto:855-867`) is ingestion-derived — bucket counts, data types, last provider, first/last
data timestamps. `PvMetadata` (`common.proto:152-161`) is curated — aliases, tags, attributes,
description. Disjoint field sets, different services, different lifecycles. The two views must coexist.

## Task 2 — do not extract an explore-view base class

The ticket proposes extracting a base class from the four existing explore views "before adding the
fifth and sixth". Measured against the actual code, this is the wrong move.

### What the measurement shows

Across the eight files (1,805 lines: Pv 268+213, Provider 223+179, Dataset 165+182, Annotation 488+260):

| Category | Lines |
|---|---|
| Structurally identical, base-class-extractable (DI setters ~79, thread-launch 12, `updateStatus`/`cancel` ~15, `onSearch` ~12) | ~110 |
| …net of the base class itself (~45–60 lines with generics + abstract hooks) | **net ~50–65 removed (~3%)** |
| Same concept, genuinely divergent implementation — unifiable only by rewriting 3 of 4 | ~180 |
| Duplicated multi-value hyperlink `TableCell` (3 near-identical copies) | ~120 → ~35, **net ~85 removed** |
| Genuinely view-specific | ~1,000+ |

The base class removes *less* than the one component extraction does, and needs 5+ abstract
methods/type parameters to accommodate the divergence.

### Why the surface similarity is misleading

The "identical Task boilerplate" is four different threading models:

- **Pv** — `Task<QueryPvStatsApiResult>` returns the result; the handler wraps six separate
  `Platform.runLater` blocks (`PvExploreViewModel.java:119-167`).
- **Provider** — `Task<Void>`; the worker mutates `providerResults` via `Platform.runLater`
  *inside* `call()` (`:144-158`), so `setOnSucceeded` (`:100-104`) logs a `resultCount` that may not
  be set yet.
- **Dataset** — same inside-the-worker pattern (`:135-156`), same stale read (`:99-103`).
- **Annotation** — `Task<PagedResult<Annotation>>` returns the result, then wraps the success
  handler in a **redundant** `Platform.runLater` (`:151`); `setOnSucceeded` already runs on the FX
  thread.

A base class must pick one, which means rewriting three of the four view models regardless — at
which point the rewrite, not the inheritance, is delivering the value.

The lifecycle contract is also not uniform: Pv, Dataset and Annotation bind in `initialize()`;
**Provider binds in a separate `initializeView()`** (`:105-122`) that `MainController` must remember
to call (`MainController.java:225,277`), because its `QueryPvsComponent` is constructed
programmatically and does not exist at `initialize()` time. A base `initialize()` template would
silently do the wrong thing for Provider.

FXML sharing is thinner still. The `fx:id` intersection across all four is **`resultsTable` and
`searchButton` — two ids**. Root layouts differ (BorderPane / HBox / VBox / VBox), so there is no
shared skeleton. Row types are four unrelated classes, so `setupTableColumns()` would be a pure
abstract method with an empty shared body.

### Replacement: T2a/T2b/T2c

Do these instead, in order. They are what actually makes views five and six cheap.

**T2a — extract the multi-value hyperlink `TableCell` into `gui/component/`. DONE.**
Shipped as `HyperlinkListTableCell` with `forValues()` / `forSingleValue()` factories; 206 lines
removed from the three controllers, 198 tests green. Two defects surfaced during the extraction that
this inventory did not capture — both pre-existing in every copy, and neither visible from the
line-count framing:

1. **Emptiness was decided by the cell's display string**, so a row whose joined string was blank
   but whose value list was not rendered no links at all. It is now decided by the list itself.
2. **A recycled cell read a stale `TableRow`.** A virtualized table sets a cell's new index and item
   immediately but repoints its `TableRow` in a later pass, so every hand-written copy could render
   the *previous* row's links against the new row's index — a link navigating somewhere unrelated to
   the row it sits on, with no error. `resolveRow()` resolves by index and falls back to the row only
   when the index is out of range; `HyperlinkListTableCellTest` pins it by driving `updateIndex()`
   and was mutation-checked against the pre-fix behavior.

A third hazard was designed out rather than fixed: recovering values by splitting the display string
would break on any value *containing* a comma. The component reads the row's list accessor, so the
item string's formatting is irrelevant.

Original analysis follows.

**T2a — extract the multi-value hyperlink `TableCell` into `gui/component/`.** Three near-identical
~40-line copies exist: `PvNamesTableCell` (`ProviderExploreController.java:152-201`),
`DatasetIdsTableCell` and `AnnotationIdsTableCell` (`AnnotationExploreController.java:187-278`),
plus Pv's two cell factories (`PvExploreController.java:112-157`). One parameterized cell
(`values extractor`, `label mapper`, `onClick handler`) replaces all of them. **Largest genuine
duplication in the four views, needs no inheritance, and both new views want it immediately** —
configuration rows list tags/attributes, sample-status rows link PV + timestamp.

**T2b — normalize naming and pick one threading model. DONE**, with one deliberate deviation from
what this section proposed. 204 tests green.

**Deviation: `statusMessage` was kept, not renamed to `searchStatusMessage`.** This section compared
only the four explore views and read Provider's and Dataset's having *both* properties as duplication.
It is not: every explore FXML declares **two** labels — `searchStatusLabel` beside the search controls
and `resultsStatusLabel` beside the results table — so the two properties are two distinct UI slots.
Renaming `statusMessage` would also have broken a wider convention this section did not measure:
eleven other view models expose `statusMessageProperty()`, and four controllers forward it to
`MainViewModel.updateStatus()` to drive the application status bar. Renaming it in four of fifteen
view models would have made those four the inconsistent ones.

**Settled vocabulary**: `searchStatusMessage` (search area), `statusMessage` (results area),
`resultCountMessage` (always a `String`), `searchInProgress`. Changes made:

- Pv gained `searchStatusMessage` and `resultCountMessage` plus a search-status row in its FXML; it
  had one label doing both jobs, so search progress was overwritten by the result summary.
- Annotation gained `statusMessage` — which is **why** `annotation-explore.fxml:88` declared a
  `resultsStatusLabel` that nothing bound (D-3): there was no property to bind it to. Now bound.
- Provider's `IntegerProperty resultCount` was replaced by `resultCountMessage`. It was bound as
  `.asString().concat(" provider(s)")`, which **cannot express truncation**.
- Dataset's `IntegerProperty resultCount` was deleted outright — nothing bound it, and it duplicated
  `resultCountMessage` with a value that could disagree.
- `isSearching` → `searchInProgress` in Pv, Provider and Dataset.

**Threading**: all four now return the result from `call()` and publish it in `setOnSucceeded`, which
already runs on the FX thread. Pv's six redundant `Platform.runLater` blocks and Annotation's one
redundant wrapper are gone; Provider and Dataset no longer publish from inside `call()`. **This is the
D-2 fix** — see below. No `Platform.runLater` remains in any of the four search paths.

**D-3 partly cleared**: the unreachable `cancel()` in Provider and Dataset is deleted.
`DataExploreViewModel.cancel()` is genuinely wired to a Cancel button and stays. `primaryStage` is
deliberately **left** in the three controllers: it is part of the uniform injection contract
`MainController` calls on every controller, and removing it from three would break that symmetry for
no gain.

Original proposal follows.

**T2b — normalize naming and pick one threading model.** Settle on `searchStatusMessage`,
`resultCountMessage`, `searchInProgress`, and the return-from-`call()` model (the Annotation shape,
minus its redundant `runLater`). This costs nothing structurally and is what makes a later shared
helper possible. Today there are four spellings of the same three concepts:

| View | Status | Count | Flag |
|---|---|---|---|
| Pv | `statusMessage` only | *none* — folded into the status string (`:158`) | `isSearching` |
| Provider | `statusMessage` **and** `searchStatusMessage` | `IntegerProperty resultCount` | `isSearching` |
| Dataset | both | **both** `resultCount` *and* `resultCountMessage` | `isSearching` |
| Annotation | `searchStatusMessage` only | `resultCountMessage`, via a `ListChangeListener` | `searchInProgress` |

The flag also drives different UI in each: Pv disables the button only, Provider/Dataset drive a
progress indicator only, Annotation does both.

**T2c — only then consider a helper, not a superclass. DECIDED: do not extract — after task 5.**
The condition for revisiting was "after view five exists". It does, and the evidence argues against
the helper.

Task 5 is the case that would have justified one, and it is the case that refutes it: the
configuration view hosts **two** searches in one view model, so a `runSearch` helper would have to be
a static utility parameterized by four properties (two status messages, a count, a flag) — at which
point the call site is longer than the seven lines it replaces. The activation search also carries
pre-flight validation the configuration search does not (the half-filled range), so the two are not
the same shape even within one file.

What actually made views five and six cheap was T2a (the shared cell) and T2b (the settled
vocabulary), both of which removed duplication without imposing a lifecycle contract. The remaining
repetition is a `Task` construction idiom that reads correctly in place and whose shape is load
bearing — it is exactly where D-2's ordering bug lived, and where a future one would. Leave it
visible.

**Sequencing**: T2a is a prerequisite for tasks 3–5 (they all want the cell). T2b should also
precede them, so the new views are written in the settled vocabulary rather than being renamed
afterwards. Neither blocks task 6.

## Defects found in the views being extended

Fix these as part of T2b rather than building on them. All verified by reading the code.

- **D-1 — unbounded listener accumulation. FIXED in PR #44**, rather than deferred to T2b, since
  the fix is a deletion with no dependency on the rest of this ticket.
  `PvExploreController.onSearch()` registered a **new** `ListChangeListener` on
  `resultsTable.getItems()` on every click, in addition to the one registered once at `:170`. N
  searches left N listeners, each firing on every subsequent change, none ever removed.
  It was redundant with `:170` — `resultsTable.setItems(viewModel.getSearchResults())` makes both
  the same list instance — and additionally registered *after* `searchPvMetadata()` starts its
  background task, so it could never observe the results of the search that registered it. The
  `:170` listener was doing the work in every case. Deleted, with the reasoning recorded in
  CLAUDE.md so it is not re-added.
- **D-2 — stale-read races in the search completion handlers. FIXED in T2b.** Provider
  (`ProviderExploreViewModel.java:100-104`) and Dataset (`DatasetExploreViewModel.java:99-103`)
  published their result rows from a `Platform.runLater` inside `call()`, then read the count in
  `setOnSucceeded` — which runs on the FX thread *first*, before the queued block — so the completion
  log always reported the pre-search count. Triage called this cosmetic because only a log line read
  the stale value; that was true but incidental. The ordering itself was the defect: any observer of
  `searchInProgress` (the progress indicator, the disabled search button) saw the flag clear while the
  table was still empty. Both now publish in `setOnSucceeded` with no `runLater`.
  `ExploreViewModelSearchTest` observes state from a listener on the flag at the instant it clears,
  and was mutation-checked against the old ordering (fails with `expected: <3> but was: <0>`).

- **D-3 — dead code. FIXED in T2b**, except one item deliberately kept. Removed: `cancel()` in
  Provider/Dataset (no callers); `AnnotationExploreViewModel.hasResults` (maintained by the results
  listener, bound by nothing); `DatasetExploreViewModel.navigateToDatasetBuilder`, a TODO stub
  shadowed by the controller's working method at `DatasetExploreController.java:107`, which is what
  the hyperlink actually calls. `annotation-explore.fxml:88`'s unbound `resultsStatusLabel` is now
  **bound** rather than deleted — the reason nothing bound it was that the view model had no
  results-area `statusMessage`, which T2b added. **`primaryStage` is deliberately kept** in
  Provider/Dataset/Pv: it is part of the uniform injection contract `MainController` invokes on every
  controller, and removing it from three of them would break that symmetry for no gain.
- **D-4 — `emptyToNull` is ignored by three of four views. FIXED in T2b.** Provider and Dataset now
  call `DpApplication.emptyToNull()` and Annotation's private `nullIfEmpty` is gone. **Note the
  behavioral difference that made this more than a mechanical swap**: `emptyToNull()` does *not*
  trim, while all three hand-rolled versions did. Calling it directly on an untrimmed field would
  send a whitespace-only value as a live criterion rather than omitting it — the opposite of the
  intent. Each call site trims explicitly, and Annotation keeps a two-line `trimmedOrNull()` wrapper
  documenting why.
- **D-5 — two of four views cannot report truncation, and neither is fixable here.** Dataset and
  Annotation use `PagedResult`; Pv and Provider do not. Verified: **neither underlying request has
  paging fields at all** — `QueryPvStatsRequest` (`query.proto:819-827`) carries only the PV name
  spec, and `QueryProvidersRequest` likewise (C3). So this is not a client oversight to correct but
  a proto gap, and it is the same gap in both. Both views therefore present whatever the server
  returns as a total. That is acceptable only because both are currently unbounded server-side
  (nothing is being silently dropped) — but it means **any future server-side cap on either would
  become invisible truncation in this app**. Note it at both views and in dp-service #265 rather
  than attempting a fix here.

## Task-by-task notes

Only the parts where triage found something the ticket does not say. The ticket's own descriptions
stand otherwise.

### Task 3 — sample status explore view. DONE

Shipped: `sample-status-explore.fxml`, `SampleStatusExploreController`,
`SampleStatusExploreViewModel`, `SampleStatusTableRow`, and
`DpApplication.querySampleStatusBuckets()` (a paged wrapper over the existing single-page
`querySampleStatuses()`). Menu item added under Explore, enabled on ingestion like its siblings.
228 tests green.

All three hazards below were real and are handled; two additions triage did not list:

- **A second cap was required.** The bucket cap in `querySampleStatusBuckets()` cannot bound the
  table, because paging is by whole buckets and one bucket may hold thousands of statuses. Without
  `MAX_DISPLAYED_STATUSES` the view would have moved the unbounded read from the server to the
  client, which is what server paging exists to prevent. The two truncation causes are reported
  distinctly because they have different remedies.
- **`confidence` / `reasons` are optional parallel arrays.** Each is empty or has exactly one entry
  per timestamp. Indexing blindly throws on the common codes-only case; rendering a *partial* array
  positionally would attach the wrong confidence to a status. Length is checked against the status
  count, and a malformed array is ignored rather than misaligned.

Written in the T2b vocabulary from the start, which is what that sequencing was for. T2a's
`HyperlinkListTableCell` is **not** used here after all: no column in this view navigates anywhere —
a status has no target view to link to — so every column is plain text. That does not retire the T2a
dependency for tasks 4 and 5, whose rows do carry ids.

Original notes follow.

### Task 3 — sample status explore view (unblocked, start here)

`DpApplication.querySampleStatuses()` (`:1565`) already exists with full javadoc. No upstream
dependency. Good first task: it exercises the paging pattern and the new hyperlink cell (T2a)
without touching any existing view's behavior.

Three things the ticket understates:

1. **Boundary trimming is not optional and not merely cosmetic.** Bucket selection is a `TimeRange`
   overlap test and boundary buckets are returned **whole** (`annotation.proto:632-634`), so the
   view must trim per-status before counting *or* displaying. A count taken from the raw buckets is
   wrong, not just generous.
2. **`SampleStatusBucket.dataTimestamps` is a full `DataTimestamps`** (`common.proto:854`) — a
   `SamplingClock` **or** a `TimestampList`. The demo generator writes a `SamplingClock`
   (CLAUDE.md's sample-status section explains why), so the view must **expand the clock** to get
   per-status timestamps. There is no timestamp list to read. This is the bulk of the decode work
   and the ticket does not mention it.
3. **Code→label resolution is hardcoded and must be labelled as such in the UI.** The constants are
   `DpApplication.SAMPLE_STATUS_DEMO_DOMAIN`/`EPICS_ALARM_*` (`:277-286`). They are valid only for
   `epics_alarm`; the domain registry is unimplemented server-side and deferred. A status in any
   other domain must render its raw code, never a guessed label.

### Task 4 — PV metadata explore + load-for-edit — **DONE**

Shipped as `pv-metadata-explore.fxml` / `PvMetadataExploreController` / `PvMetadataExploreViewModel`
/ `PvMetadataTableRow`, plus `DpApplication.queryPvMetadata()` and `getPvMetadata()`, plus
`PvMetadataViewModel.loadFromPvMetadata()` and the C4 rename. 25 tests added; suite at 254.

All four triage points below were verified against current source before implementing, and all four
held — the only drift was line numbers, since #244 moved `TextMatch` / `AttributeCriterion` into
`com.ospreydcs.dp.client.criteria`.

**The alias trap is closed structurally, not by warning.** `MainController.navigateToPvMetadataEditor()`
takes a `PvMetadata` rather than a name, and `loadFromPvMetadata()` populates from *that record's*
canonical `pvName`. Re-resolving from typed text is what would let an edit of `OLD:NAME` write a new
record under the alias; passing the record makes that unrepresentable. The view states the
consequence too, but the type signature is what enforces it.

**One thing the triage did not list:** `loadFromPvMetadata()` had to write aliases/tags/attributes
into the injected **components**, not into ViewModel properties — the save reads them from the
components. Getting it wrong would be write-only and silent, erasing all three on the next save.
Identical to the Annotation Builder defect, in a view with the same shape. Guarded by
`PvMetadataLoadForEditTest` using real component instances.

**Testing.** Three classes, each mutation-checked against the defect it claims to catch:
`PvMetadataExploreViewModelTest` (16), `PvMetadataLoadForEditTest` (6),
`PvMetadataExploreColumnBindingTest` (3). The blank-criterion guard asserts each `TextMatch` list is
`null` individually rather than relying on `TextMatch.isEmpty()`, which reports true for both the
correct all-null match and the broken empty-list one — the mutation produced exactly
`expected: <null> but was: <[]>`.

**Live coverage added.** `ExploreQueryLiveIT` now exercises this task's query paths against a real
MongoDB, including the alias resolution this task's whole design rests on. UI-level interaction
(clicks, navigation, the editor forms) remains manual — see
[`manual-verification.md`](manual-verification.md).

Original triage notes, for the record:

- **`getPvMetadata()` resolves aliases** (`AnnotationClient.java:2380-2391` — "canonical PV name or
  alias"). So load-for-edit has a trap: typing an *alias* loads a record whose canonical `pvName`
  differs from what was typed, and `savePvMetadata()` — a full-replace upsert — then writes to the
  canonical name. The view must **display the canonical name returned by the load** and make clear
  that is what a save targets. Otherwise a user edits "OLD:NAME" and silently rewrites "NEW:NAME".
- The ticket correctly notes the API has no description criterion. It also has no `TextCriterion`
  here — the criteria are name (exact/prefix/contains), aliases (same shape), tags, attributes. A
  free-text box would have nothing to bind to.
- `TextMatch` (`:1670`) drops blank entries deliberately: a blank prefix compiles to a regex
  matching **everything**, so an unfilled optional field would silently return the whole collection.
  Bind UI fields straight to `TextMatch`; do not pre-process them.
- Per C4, do the menu/property rename in this task.

### Task 5 — configuration/activation explore + #36 — **DONE**

Shipped as `configuration-explore.fxml` / `ConfigurationExploreController` /
`ConfigurationExploreViewModel` / `ConfigurationTableRow` / `ConfigurationActivationTableRow`, plus
`DpApplication.queryConfigurations()` and `queryConfigurationActivations()`, plus
`MachineConfigurationViewModel.loadFromConfiguration()` and the menu wiring. 35 tests added; suite at
290. (`#36` was already complete — PR #44.)

**Both triage points held.** The params asymmetry is real and the `TimeRangeCriterion` both-bounds
rule is confirmed in the request builder.

**One consequence the triage did not state, and it is the important one.** The builder does not
*reject* a half-filled range — it emits **no criterion at all**. So passing one through does not
produce an error; it produces a silently broader result set with every other criterion still applied,
which reads as a working search. The view model refuses the search and says why, rather than relying
on the server to complain. This is the guard most worth keeping.

**Two independent searches, not one.** Configurations and activations are separate records with
disjoint criteria, so each carries the T2b vocabulary per *search* rather than per view. Tests pin
that neither disturbs the other's results, counts, or status.

**The activation gate was widened, deliberately.** Section 2 of the editor was gated on
`configurationSaved`, described as "saved in this session". Its real invariant is that the server
holds a Configuration under `savedConfigurationName` — the server rejects an activation whose name
does not resolve. A loaded record satisfies that invariant, so `loadFromConfiguration()` opens the
gate and binds it to the *record's* name rather than to the editable text field. Leaving it closed
would have denied the one operation the loaded record makes safe. CLAUDE.md's "saved in this session"
wording and the controller's matching comment were both corrected.

**Testing.** Four mutation checks, all confirmed: the half-filled range (`expected: <0> but was:
<1>`), the activation gate (6 of 8 tests failed), the open-ended end time (`expected: <open-ended>
but was: <1970-01-01 00:00:00>`), and a column bound to the wrong `PROPERTY_*` constant. The range
check also exposed a **race in the tests themselves** — `executeActivationSearch()` returns once it
has started a thread, so reading a fake's call count straight afterwards is not ordered; one refusal
test had been passing by luck. Fixed with a bounded poll, since a refused search has no in-progress
transition to await.

**Live coverage added**, as for tasks 3 and 4 — `ExploreQueryLiveIT` pins the half-filled-range
behavior this task's refusal depends on. UI-level interaction remains manual, see
[`manual-verification.md`](manual-verification.md).

Original triage notes, for the record:

Unblocked by #243/#245. `#36` is planned separately and is independent — see
[`plan/tickets/36/plan.md`](../36/plan.md); its dp-service half is already done.

- Note the params asymmetry when building the search form: `QueryConfigurationActivationsParams`
  (`:2156`) takes protobuf `Timestamp` for `activeAt`/`rangeStart`/`rangeEnd`, and
  `TimeRangeCriterion` **requires both bounds** — a half-filled range must emit no criterion at all,
  not a partial one. Convert with `DpApplication.timestampFromInstant()`, which maps null to null
  for exactly this reason.
- Configuration load-for-edit uses the existing `getConfiguration()`; the overwrite-warning path it
  feeds is already built (`MachineConfigurationViewModel.java:347-404`).

## Live verification pass — **DONE**, before task 6

Run before starting task 6, since task 6 rewrites the existing data-explore query path — the one
part of this branch with prior manual coverage — and a regression there should land against a known
good baseline.

`ExploreQueryLiveIT` (16 tests) covers the query paths of tasks 3, 4 and 5 against a real ecosystem
and a real MongoDB. **Every premise these tasks were built on is now verified rather than assumed:**

| Claim | Verified |
|---|---|
| `getPvMetadata()` resolves an alias to the **canonical** record | yes — task 4's structural fix addresses a real behavior |
| sample statuses come back for the window they were written for | yes — exactly 100 statuses for 100 generated samples, so the clock aligns at nanosecond precision |
| boundary buckets are returned **whole** | yes — a 1-second window expanded to 30 untrimmed vs 10 trimmed |
| a **half-filled** activation range is dropped, not rejected | yes — a start bound a year later still returned the activation |
| a missing record is a REJECT, not an error | yes |
| a criteria-free query matches everything | yes |

The negative assertions matter as much as the positive ones: a non-matching attribute value must
*exclude* the record, which is what stops the positive assertions from passing vacuously.

The trim assertion was mutation-checked by deleting the lower-bound trim: `expected: <10> but was:
<30>`. The suite skips cleanly with no database (`-Ddp.MongoClient.dbPort=1`) and leaves no records
behind.

**Note on transport**: `DpApplication.init()` always starts its own in-process ecosystem — the app
has no remote-gRPC path yet — so separately running dp-service instances on 50051-50053 are not
exercised by any of this.

**What remains manual**: FXML rendering, clicks, cross-view navigation, and the editor forms. See
[`manual-verification.md`](manual-verification.md), whose highest-value check is the alias trap in
Part 3c — a silent data-loss path if it regresses.

### Task 6 — V2 migration — **migration half DONE**; selector sections remain

Sequenced in three commits rather than one, because the migration rewrites the one query path with
prior manual coverage while the selector sections are purely additive. **Commit 1 (the migration) is
done**; the modal PV selector and the configuration / sample-status selector sections follow.

**Done in the migration commit:**

- `DpApplication.querySamples(pvNames, begin, end, pageToken)` — single-page by design, unlike every
  other paged wrapper here, because the view displays each page as it arrives.
- `DataExploreViewModel.executeSamplesQuery()` — `do/while` on `nextPageToken`, replacing the
  1-minute interval loop, which is **deleted** rather than relocated.
- `columnNamesOf()` / `reshapePage()` / `renderDataValue()` — pure statics; the synthesized timestamp
  column, the column-to-row transpose, and blank-for-unset rendering.
- `TIMESTAMP_COLUMN_NAME` constant, bound at all four `DataExploreController` consumer sites.
- `InprocessServiceBase` raises `maxInboundMessageSize` to 64 MB.
- The dead V1 `queryTable()` wrapper is **removed** — its only caller migrated, and leaving it would
  invite a future view onto the retired path.

**Claims verified rather than assumed:**

| Claim | How |
|---|---|
| V2 table has no timestamp column (axis only in `timestampList`) | `QuerySamplesLiveIT` order 10 |
| every resolved PV gets a column even with no data | order 11 |
| exactly one `DataValue` per column per timestamp | order 12 |
| paging terminates, and no token repeats | order 20 |
| paging **accumulates** across a real page boundary | order 23 |
| an empty window is a success, not a rejection | order 21 |
| ingested floats render as `Number`, not blanks | order 22 |

**Two findings from mutation-checking the live test itself**, both recorded in CLAUDE.md: a
single-page result cannot distinguish accumulation from stopping early (fixed by adding a set sized
past the server's default page, plus a `pageCount > 1` assertion), and **ingestion is asynchronous**
— a query issued immediately after a successful `generateAndIngestData()` returns the right columns
with an empty timestamp list, which is indistinguishable from a legitimately empty window. The
readiness gate polls rather than sleeping.

Four mutation checks on the decode, all caught: row count read from a column (`expected: <3> but was:
<1>`), unset rendered as `"N/A"`, the synthesized timestamp column dropped, and signed accessors for
unsigned values (`expected: <4294967295> but was: <-1>`).

#### Original task 6 notes (retained)


The single decode point is `DataExploreViewModel.processQueryTableResponse()` (`:320-382`), fed by
`executeIncrementalQuery()` (`:248-318`). The ticket's plan to reshape at that point and leave the
table/chart machinery untouched is correct — `tableColumnNames` / `tableData` are the only outputs.

Verified server behavior that shapes this task:

- **The 1-minute interval chopping can be deleted outright.** It exists to dodge message-size
  limits (`:257`). The server now bounds a page by **both** a row count and a byte budget:
  `pageSize` default 10,000 rows / max 100,000 (`QueryV2Resolver.java:132-137`,
  `application.yml:131,135`), and a 4,096,000-byte assembly budget that truncates mid-window and
  returns a resume token (`QuerySamplesUnaryDispatcher.java:110-117,151-155`). Replace the fixed
  interval loop with `while (!nextPageToken.isEmpty())`.
- **Do not set a small `limit`.** The server does **not** `.limit()` the Mongo cursor
  (`MongoSyncQueryClient.java:622-626`); it drains buckets until the byte budget trips, then
  truncates. A small `limit` therefore causes repeated near-full re-scans without reducing server
  work. Leave `limit` unset (→ server default) and let the byte budget be the real boundary. An
  over-max `limit` is **silently clamped**, not rejected.
- **Raise the client's `maxInboundMessageSize`.** The server's budget is read from
  `GrpcServer.incomingMessageSizeLimitBytes` (4 MB) and is measured on `DataValue` serialized sizes
  only — it excludes the `timestampList`, column framing, names and the response envelope, and
  accounts per-bucket so it can overshoot by up to one bucket. A client left at gRPC's 4 MB default
  has no headroom.
- **One row can be an unpageable hard error.** If a single timestamp's row across all selected PVs
  exceeds the whole byte budget, the server returns `RESULT_STATUS_ERROR`:
  *"single querySamples row at timestamp … exceeds the outgoing message size limit …; narrow the PV
  set or time range"* (`:162-170`). Narrowing the **time range does not help** — only fewer PVs
  does. Surface the message verbatim; do not retry.
- **Non-scalar PVs reject the whole request, and the check is data-driven.**
  `TabularDataUtility.java:159-173` throws mid-assembly and the dispatcher rejects
  (`:118-124`), discarding everything assembled. There is **no pre-flight scalar check** —
  dp-service [#194](https://github.com/osprey-dcs/dp-service/issues/194) is still **open**. So a
  non-scalar PV with no buckets in the window passes silently, and *the same PV set can succeed on
  one page and reject on the next*. Only the first offending PV is named, and which one depends on
  bucket iteration order. The UI must handle a reject arriving mid-paging, after rows have already
  been displayed.
- **Result shape**: columns are bare PV names, sorted ascending and deduped
  (`QueryV2Resolver.java:145`); every resolved PV always gets a column even with zero data;
  `dataColumns` has exactly one `DataValue` per `timestampList` entry; **there is no timestamp
  column** — the axis lives only in `ColumnTable.timestampList`. The existing table treats
  `"timestamp"` as a column name (`:344`), so the reshape must synthesize that column from
  `timestampList` and prepend it.
- **Missing values are an unset `DataValue` oneof** (`AbstractQuerySamplesDispatcher.java:180-186`),
  which is what lets the ticket's "render natively instead of `N/A`" happen. The current code emits
  `"N/A"` at three places (`:352`, `:365`, `:369`) — two for an unrecognized `DataValue` case and
  one for a column absent from the row map.
- **`maxResolvedPvCount` = 10,000** (`application.yml:139`) — a metadata-criteria selector that
  resolves too broadly is rejected with *"narrow the selector"*. Relevant to the modal PV selector's
  metadata mode.
- Do **not** hardcode 10,000 / 100,000 / 4 MB; all three are env-overridable.

**Decisions the shipped wrapper adds** (it exposes more than the plan assumed, so these are choices
task 6 must make rather than behaviors it inherits):

- **Use the unary `querySamples()`, not `querySamplesStream()`.** The wrapper offers both. Unary is
  the right one here for two reasons. First, the existing decode point already drives a paging loop
  and the ticket keeps incremental display, which needs the resume token the streaming call does not
  return (`nextPageToken` is always empty on a stream — completion is signaled by the stream ending).
  Second, `querySamplesStream()` accumulates the **entire** result into one `ColumnTable` before
  returning, which reinstates exactly the unbounded client-side read that paging exists to prevent —
  the same failure the `QUERY_RESULT_CAP` guard addresses for the annotation queries.
- **Leave `useSerializedColumns` false.** On the streaming method it is actively unsafe across pages:
  serialized columns cannot be merged, so a multi-page stream returns per-page column *fragments*
  against a fully concatenated timestamp axis — a well-formed table whose columns do not line up with
  it. The wrapper flags this (`serializedColumnsFragmented`), and a consumer that ignores the flag
  gets silently misaligned data rather than an error. If it is ever enabled for the unary path as an
  optimization, that flag must be checked before the table is read.
- **The params record carries `sampleStatusSelector`**, which is the natural join between task 6 and
  task 3's sample-status work. Note it is accepted **only** by the sample-oriented methods — the
  bucket methods reject it, and `QueryBucketsParams` therefore omits the field entirely.
- **`pageToken` must not be carried across queries.** A token encodes a position only; the server's
  kind check separates bucket tokens from sample tokens but nothing binds a token to the `QuerySpec`
  that produced it, so replaying one against a changed spec yields a well-formed but **semantically
  wrong** result rather than an error. The paging loop must discard its token whenever any query
  parameter changes — not merely on a new search, but on an edited time range or PV list.

**Cost the ticket understates — the modal PV selector.** The Query Editor's PV list is a flat
`ObservableList<String>` (`DataExploreViewModel.java:30`) wired into six places: global-state
restore (`:96-98`), `populateFromDataBlock` (`:451-453`), `QueryPvsComponent` (`:99-105`),
`DpApplication.getPvNames()/setPvNames()`, the data-event-explore hyperlink
(`DataEventExploreViewModel.java:256`) and `DataExploreController.java:1771`. Every one of those is
a *name-list* flow. Keeping name-list as the default and as the identity path for all six is what
makes the change safe; pattern and metadata modes are additive and must not write back into the
shared list.

## Testing

`ViewLoadSmokeTest` enumerates FXML from the classpath, so the two new views get load coverage the
moment their files land — provided `initialize()` stays dependency-free with injection via setters.

Beyond that:

- `AnnotationInfoTableRowBindingTest` is the precedent for guarding reflective
  `PropertyValueFactory` bindings, which fail as a **silently blank column**, not a compile error.
  Each new `*TableRow` needs the equivalent, and must assert each binding resolves **to its expected
  value** — asserting non-null is insufficient, because `PropertyValueFactory` falls back from
  `someProperty()` to `getSome()`. Adopt the `PROPERTY_*` constant pattern
  (`AnnotationExploreController.java:108-113`) for the new views so a rename fails to compile.
- The paging accumulation logic belongs in `DpApplicationPagingTest` alongside `accumulatePages()`,
  which is static and function-fed precisely so it is testable without a service ecosystem. The
  cases that matter: cap reached exactly on a page boundary *with* a next page (truncated) vs. a
  result exactly filling the cap with no next page (not truncated), and a server returning a token
  with an empty page.
- **Boundary-bucket trimming (task 3) and the `SamplingClock` expansion need unit tests**, not just
  manual checking: an off-by-one at a bucket edge produces plausible-looking wrong counts.
- `AnnotationApiLiveIT` is the model for live coverage — it skips on a socket probe when MongoDB is
  unreachable, so CI stays green while a developer with a database gets it from a plain `mvn test`.
  `*IT` is already in the surefire `<includes>` (`pom.xml:269-275`). Worth adding: a
  `querySamples` round trip (paging across a real multi-page result, and the missing-value
  encoding) — **now writable, since #244 has landed** — and the #36 activation-collision case.

## Out of scope

Unchanged from the ticket: streaming wrappers at the `DpApplication` layer, `deleteSampleStatuses`
UI, in-table/chart status display, the domain registry, `getActiveConfigurations`. Added by triage:

- Bringing provider-explore onto the paging pattern (needs dp-grpc proto work — C3).
- An explore-view base class (task 2 as originally written — see above).
