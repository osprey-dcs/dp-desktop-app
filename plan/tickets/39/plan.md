# Plan: query support for PV metadata, machine configuration, sample status, and V2 query API (issue #39)

- **Ticket**: [osprey-dcs/dp-desktop-app#39](https://github.com/osprey-dcs/dp-desktop-app/issues/39)
- **Sub-issue**: [#36](https://github.com/osprey-dcs/dp-desktop-app/issues/36) — see [`plan/tickets/36/plan.md`](../36/plan.md)
- **Status**: scoped 2026-09-12 against dp-desktop-app `889b5c3`, dp-grpc `6dfff3f`, dp-service `89bb822` (`main`), with the installed `dp-service-1.16.0.jar` (2026-09-11). Every upstream claim below was verified by reading the sibling source, not by reading the tickets.
- **Baseline**: `mvn clean compile` and `mvn clean test` are both green on `889b5c3`. Unlike #42, this ticket starts from a working build; nothing here is non-deferrable.

## Triage summary

The ticket is well-scoped and its architecture is sound. Triage changed four things:

1. **Two of the three named upstream prerequisites have already landed.** Only dp-service #244
   remains, and it blocks task 6 alone.
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
| dp-service [#244](https://github.com/osprey-dcs/dp-service/issues/244) — `QueryClient.querySamples` | required | **OPEN, no plan document.** The only live blocker. |
| dp-service [#235](https://github.com/osprey-dcs/dp-service/issues/235) — reject-vs-error | "related" | Resolved during #243 triage: both getters already classify not-found as `REJECT`. Nothing to wait for. |

Verification, rather than trusting the issue state:

- `AnnotationClient.queryPvMetadata` `:2352`, `getPvMetadata` `:2391`, `queryConfigurations` `:2440`,
  `queryConfigurationActivations` `:2490`, `getConfigurationActivationById` `:2538`,
  `getConfigurationActivationByCompositeKey` `:2562`; params records at `:1944`, `:2048`, `:2156`;
  shared `TextMatch` / `AttributeCriterion` at `:1670` / `:1700`.
- `MongoSyncAnnotationClient.java:81` `DEFAULT_QUERY_LIMIT = 100`, applied unconditionally at
  `:382`, `:720`, `:987`, `:1245`, `:1503`.
- `QueryClient` has **no** V2 method (only `queryTable`, `queryPvStats`, `queryProviders`).

**#244 is a hard blocker for task 6, not a convention.** `DpApplication` reaches gRPC exclusively
through `ApiClient`'s typed clients (`api.queryClient.*` / `api.annotationClient.*`, ~28 call
sites) and never touches a stub or channel. There is no in-repo workaround that does not fork that
architecture.

### Consequence for sequencing

The ticket's stated order — "1 ‖ 3, then 2, then 4/5, then 6" — is now:

> **3 ‖ 4 ‖ 5 ‖ 36 can all start immediately.** Only task 6 waits on #244.

Tasks 4 and 5 were gated on #243/#245, which have landed. That is a substantial unblocking: four of
the six tasks plus the sub-issue can proceed in parallel today, and #244 can be written
concurrently rather than on the critical path.

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

**Action**: comment on dp-service #244 with this correction before it is implemented.

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

### C4 — "Explore → PVs" is already `pvMetadataMenuItem`

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

**T2a — extract the multi-value hyperlink `TableCell` into `gui/component/`.** Three near-identical
~40-line copies exist: `PvNamesTableCell` (`ProviderExploreController.java:152-201`),
`DatasetIdsTableCell` and `AnnotationIdsTableCell` (`AnnotationExploreController.java:187-278`),
plus Pv's two cell factories (`PvExploreController.java:112-157`). One parameterized cell
(`values extractor`, `label mapper`, `onClick handler`) replaces all of them. **Largest genuine
duplication in the four views, needs no inheritance, and both new views want it immediately** —
configuration rows list tags/attributes, sample-status rows link PV + timestamp.

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

**T2c — only then consider a helper, not a superclass.** After T2b, what remains shared is
`protected <T> void runSearch(Supplier<T>, Consumer<T>)` — a static utility. Revisit after view
five exists and there is real evidence of what the sixth needs.

**Sequencing**: T2a is a prerequisite for tasks 3–5 (they all want the cell). T2b should also
precede them, so the new views are written in the settled vocabulary rather than being renamed
afterwards. Neither blocks task 6.

## Defects found in the views being extended

Fix these as part of T2b rather than building on them. All verified by reading the code.

- **D-1 — unbounded listener accumulation.** `PvExploreController.onSearch()` (`:244-251`)
  registers a **new** `ListChangeListener` on `resultsTable.getItems()` on every click, in addition
  to the one registered once at `:170`. N searches leave N listeners, each firing on every
  subsequent change. Delete the one in `onSearch()`; it is redundant with `:170`.
- **D-2 — stale-read races.** `ProviderExploreViewModel.java:100-104` and
  `DatasetExploreViewModel.java:99-103` read state set by a `Platform.runLater` queued from the
  worker, with no ordering guarantee. Currently cosmetic (log lines only), but it is the pattern,
  not the symptom, that the new views must not copy. Fixed by T2b's threading model.
- **D-3 — dead code** (~50 lines): `cancel()` in Provider/Dataset (no callers on these view
  models); `primaryStage` assigned-never-read in Provider/Dataset/Pv;
  `AnnotationExploreViewModel.hasResults` (set at `:64`, never bound);
  `resultsStatusLabel` declared in `annotation-explore.fxml:88` and in the controller at `:53` but
  **never bound**, so it is a permanently-static "Ready"; and
  `DatasetExploreViewModel.navigateToDatasetBuilder` (`:165-172`), a TODO stub duplicating the
  controller's working method at `DatasetExploreController.java:107`.
- **D-4 — `emptyToNull` is ignored by three of four views.** `DpApplication.emptyToNull()` exists
  and is tested (`DpApplicationParamsTest`), yet Provider (`:119-123`) and Dataset (`:118-121`)
  hand-roll inline ternaries and Annotation has its own `nullIfEmpty` (`:210-212`). Collapse onto
  the tested helper.
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

### Task 4 — PV metadata explore + load-for-edit

Unblocked by #243/#245. Beyond the ticket:

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

### Task 5 — configuration/activation explore + #36

Unblocked by #243/#245. `#36` is planned separately and is independent — see
[`plan/tickets/36/plan.md`](../36/plan.md); its dp-service half is already done.

- Note the params asymmetry when building the search form: `QueryConfigurationActivationsParams`
  (`:2156`) takes protobuf `Timestamp` for `activeAt`/`rangeStart`/`rangeEnd`, and
  `TimeRangeCriterion` **requires both bounds** — a half-filled range must emit no criterion at all,
  not a partial one. Convert with `DpApplication.timestampFromInstant()`, which maps null to null
  for exactly this reason.
- Configuration load-for-edit uses the existing `getConfiguration()`; the overwrite-warning path it
  feeds is already built (`MachineConfigurationViewModel.java:347-404`).

### Task 6 — V2 migration (blocked on #244)

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
  `querySamples` round trip once #244 lands (paging across a real multi-page result, and the
  missing-value encoding), and the #36 activation-collision case.

## Out of scope

Unchanged from the ticket: streaming wrappers at the `DpApplication` layer, `deleteSampleStatuses`
UI, in-table/chart status display, the domain registry, `getActiveConfigurations`. Added by triage:

- Bringing provider-explore onto the paging pattern (needs dp-grpc proto work — C3).
- An explore-view base class (task 2 as originally written — see above).
