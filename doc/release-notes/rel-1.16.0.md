# dp-desktop-app 1.16.0 Release Notes

Changes since rel-1.15.0.  This repo is the JavaFX desktop GUI for the MLDP archive; it consumes
the gRPC API defined in dp-grpc 1.16.0 and implemented in dp-service 1.16.0, and the three repos
are tagged in lockstep.  Several changes here exist because that API changed underneath the app —
those sections name the upstream ticket.

**Two things that were true of every previous release are no longer true**, and both are easy to
miss because nothing errors:

- the application no longer runs only in demonstration mode, and
- the demo database is no longer wiped at launch.

Read [Upgrading from 1.15.0](#upgrading-from-1150) before installing.

## Contents

- [Upgrading from 1.15.0](#upgrading-from-1150)
- [Remote gRPC targets (#4)](#remote-grpc-targets-4)
- [Query API V2, and query support for the new metadata APIs (#39)](#query-api-v2-and-query-support-for-the-new-metadata-apis-39)
- [Annotation API modernization (#42)](#annotation-api-modernization-42)
- [Sample status generation (#37, #38)](#sample-status-generation-37-38)
- [Machine configuration authoring (#27, #36)](#machine-configuration-authoring-27-36)
- [PV metadata authoring (#18)](#pv-metadata-authoring-18)
- [Column metadata replaces request-level metadata (#17)](#column-metadata-replaces-request-level-metadata-17)
- [Test coverage and CI (#29)](#test-coverage-and-ci-29)
- [Build and release infrastructure (#21)](#build-and-release-infrastructure-21)

## Upgrading from 1.15.0

1. **Expect a populated demo database on first launch.**  Previous releases dropped `dp-demo` at
   startup, so every session began empty.  This one does not, and a 1.15.0 database is left in
   place.  `Tools → Delete Demo Data` clears it on request.  If you were relying on the launch
   drop to reset state between demos, that is now an explicit action.
2. **Nothing needs configuring to keep the old behavior.**  Demo mode is the default, and an
   absent or unrecognized `mode` value resolves to demo — never to deployment.  Launching with no
   flags behaves as 1.15.0 did, apart from the database persistence above.
3. **To point at a real deployment, set the mode *and* the connect strings.**  Setting only the
   mode connects to `localhost` on the default ports.  See
   [Remote gRPC targets](#remote-grpc-targets-4).
4. **Rebuild against dp-grpc and dp-service `rel-1.16.0`.**  This release does not compile against
   1.15.0 of either; the Annotation API reshaping in dp-grpc #132 is the bulk of it.  Both are
   resolved from the local Maven repository rather than a package registry, so install them first.
5. **If you scripted `mvn javafx:run -Ddp.DpDesktopApp.mode=...`, it does not work.**  The plugin
   forks a JVM that does not inherit Maven's system properties, so that launch **silently starts in
   demo mode**.  Use `--mode=deployment` instead, which works on every launch path.

## Remote gRPC targets (#4)

The application previously ran only in demonstration mode, with the MLDP services hosted in-process
alongside the GUI.  It now also runs against **already-running remote services**, selected by a
mode setting:

- **`demo`** (the default) — starts the self-contained in-process service ecosystem backed by the
  local `dp-demo` MongoDB database.  Unchanged from 1.15.0.
- **`deployment`** — connects to the four services at their configured connect strings, and
  **never constructs a MongoDB client at all**.

Deployment mode is deliberately read-only for this release.  Ingestion (`Ingest → Generate`,
`Ingest → Import`), metadata authoring (`Metadata → PV`, `Metadata → Machine Configuration`),
`Explore → Data Events` and `Tools → Delete Demo Data` are all disabled; the Explore views are
enabled at launch rather than after an ingestion, since a real archive already has data to browse.
Dataset, annotation and export actions within the Explore views remain available — they are the
analysis workflow and write no PV time-series data.  Re-enabling import and metadata authoring
against a live archive is deferred to a follow-on.

### Selecting the mode

Prefer the `--mode` argument, which works on every launch path:

```
java -jar dp-desktop-app-1.16.0-shaded.jar --mode=deployment
mvn javafx:run -Djavafx.args=--mode=deployment
```

`-Ddp.DpDesktopApp.mode=deployment` also works **on the shaded jar**, as does a deployment
`application.yml` selected with `DP.CONFIG=<file>`.  It does **not** work with `mvn javafx:run`:
the plugin forks a JVM that does not inherit Maven's system properties, so that combination
silently launches in demo mode.  An explicit `-D` wins over `--mode`, so a launcher script that
always appends `--mode=demo` cannot override what an operator set deliberately.

An absent or unrecognized mode value resolves to **demo**.  That direction is deliberate: a
misspelling must not become a connection attempt against production.

### Configuring the targets

Four connect strings, each environment-overridable, following dp-service's convention:

| Key | Environment variable | Default |
|---|---|---|
| `GrpcClient.ingestionConnectString` | `DP_GRPC_CLIENT_INGESTION_CONNECT_STRING` | `localhost:50051` |
| `GrpcClient.queryConnectString` | `DP_GRPC_CLIENT_QUERY_CONNECT_STRING` | `localhost:50052` |
| `GrpcClient.annotationConnectString` | `DP_GRPC_CLIENT_ANNOTATION_CONNECT_STRING` | `localhost:50053` |
| `GrpcClient.ingestionStreamConnectString` | `DP_GRPC_CLIENT_INGESTION_STREAM_CONNECT_STRING` | `localhost:50054` |

Precedence is `-Ddp.<key>` > environment variable > the configuration file's default.  The status
bar, window title and startup log all name the **query** target in deployment mode, so "which
archive am I pointed at" is answerable from the UI.

### BEHAVIOR CHANGE: the demo database is no longer dropped at launch

Previous releases dropped `dp-demo` on every startup, so demo data never survived a restart — the
README said so, and that statement is now obsolete.  **This is the first release in which a demo
session starts with the previous session's providers, buckets, sample statuses and metadata still
present.**  `Tools → Delete Demo Data` clears it on request, with a confirmation dialog naming the
database.

Two consequences worth knowing:

- The Explore menu is now enabled when the **archive** has data, not only when the current session
  has ingested some.  Otherwise a demo relaunched on a populated database would show every Explore
  item disabled over hundreds of buckets.
- The delete rebuilds the schema after dropping.  Dropping a database destroys its indexes, and the
  services still running hold collection handles bound at their own startup — without the rebuild
  the session would continue against an unindexed database while every operation kept reporting
  success.

Deployment mode never constructs a MongoDB client, so none of this is reachable there.

## Query API V2, and query support for the new metadata APIs (#39)

#39 subsumed three earlier tickets, which were closed as not-planned rather than implemented
separately: #19 (a view for querying PV metadata), #20 (a view for the V2 query API with PV and
machine configuration metadata as search criteria) and #34 (a view for querying machine
configuration and activation metadata).  All three are delivered by the work below.

### The data query migrated to Query API V2

`Explore → Data` now issues `querySamples` rather than the retired V1 `queryTable`.  Visible
differences:

- **Missing values render blank rather than `N/A`.**  V2 distinguishes "this PV had no sample at
  this timestamp" from a value the decoder did not recognize; V1 could not.  A blank cell now means
  genuinely missing, and anything else means a decode gap.
- **The 1-minute query chopping is gone.**  The server bounds each page itself and returns a resume
  token, so the client no longer guesses a window small enough to fit under the message size limit.
- **Results are capped at 50,000 rows**, and a capped result says so rather than reporting the
  count as a total.
- **A running query can be stopped.**  The Query Editor carries a **Stop Query** button while a
  query runs.  Cancellation takes effect between pages; rows already received are kept.
- **`uint64` columns display as exact unsigned decimal strings.**  Java has no integral type that
  holds the upper half, and both alternatives display a wrong value that looks right.

### PVs can be selected three ways

The Query Editor's **Select PVs...** dialog covers the three arms of the V2 PV selector: a **name
list** (the default, and unchanged), a **name pattern** (a regex, not a glob), and **metadata
criteria** resolved against curated PV metadata.

One sharp edge, stated in the dialog and in the summary line: **an empty metadata query is not an
error — it matches every PV in the archive.**  Nothing downstream complains, so an unfilled
metadata tab produces a whole-archive scan that looks like a filter.  A blank *pattern* is refused,
because the server rejects one.

Adding to a dataset is refused for a pattern or metadata selection: a data block is a PV name list
by definition, and building one from the displayed name list would save a dataset covering PVs the
query never touched.

The README's [Querying PV time-series data](../../README.md#querying-pv-time-series-data) section
walks through the Query Editor with these controls in place.

### Two optional query filters

**Query Filters...** adds two restrictions, both off by default, composing by intersection:

- **Machine configuration activations** restrict the *time* axis to the intervals during which
  matching configurations were live.
- **Sample status** then drops individual samples from what survives.

The sample status mode is **not a polarity switch**, and the difference is in the unlabeled
samples: `INCLUDE` returns matching samples and excludes unlabeled ones, `EXCLUDE` drops matching
samples and returns unlabeled ones.  Over a sparsely labeled archive the two are not complements,
and choosing wrong is silent — `INCLUDE` over a lightly labeled PV returns a nearly empty table
that reads as "no data here" rather than "almost nothing here is labeled".  Both the mode labels
and the filter summary say so.

A filtered-out sample becomes a missing *value*, not a missing row; a timestamp disappears only
when every selected PV is filtered out at it.

### Three new Explore views

- **`Explore → PV Metadata`** — searches the *curated* PV metadata records (aliases, tags,
  attributes, description) by name or alias, each with Contains / Starts-with / Exact matching, and
  loads one into the PV metadata editor.  This is distinct from `Explore → PV Statistics`, which
  shows per-PV statistics derived by aggregation over ingested buckets; a PV can appear in one and
  not the other.  The old `Explore → PVs` item is renamed **PV Statistics** to make the difference
  visible.
- **`Explore → Machine Configurations`** — two independent searches, for configuration records and
  for their activation intervals, and loads a configuration into its editor.  A half-filled time
  range is **refused**: the server's request builder responds to one bound by dropping the criterion
  entirely, which silently widens the search rather than failing it.
- **`Explore → Sample Statuses`** — queries stored sample statuses by time range, PV, domain and
  layer.  Status code labels resolve for the `epics_alarm` domain only; the server-side domain
  registry is not implemented yet, so a status in any other domain shows its raw code with an empty
  label rather than a guess.

See also [Exploring metadata](../../README.md#exploring-metadata-added-in-1160) in the README.

Loading a PV metadata record for editing hands the editor **the resolved record**, not the name
that was typed.  `getPvMetadata()` resolves aliases, so searching by a historical name returns the
record under its canonical one — and saving is a full-replace upsert keyed on the name.  Editing a
record found by alias would otherwise write a new record under the alias and leave the original
untouched.

## Annotation API modernization (#42)

dp-grpc #132 reshaped the DataSet and Annotation messages and changed their query semantics.  Most
of the work here was restoring compilation, but three changes are visible in the UI:

- **"Comment" is now "Description"** on annotations, throughout — field labels, column headers and
  the underlying proto field.
- **The annotation search no longer offers an event criterion.**  The modernized `Annotation` has no
  event field, and the free-text box is a collection-level text search over name and description.
  The field was labelled "Name / Description / Event" through the rename, which promised a search
  that silently matched nothing; it is now "Name / Description".
- **The Calculations column shows presence, not frame names.**  `queryAnnotations` no longer returns
  Calculations content, so the column is a single "View calculations" link that fetches the frames
  on click, prompting for which frame to open when there are several.  Resolving names per row would
  rebuild client-side, as serial round trips, the N+1 fan-out #132 removed.

**One silent data-loss defect was fixed in the process.**  Loading an annotation for editing now
goes through `getAnnotation()`, the only method returning Calculations content inline.  Loading
through a query result populated the builder with no calculations, and saving any unrelated edit
then destroyed the stored Calculations — no error, no warning, nothing in the UI indicating the
loss.  The Annotation Builder's tags and attributes had the same shape of bug from the opposite
direction, and are fixed in the same release.

**Query criteria now combine with AND** across these queries, where the previous scheme ORed some
of them.  The app sends one value per criterion from single text fields, so its own searches are
unaffected — but the result sets differ for anything scripted against the API directly.

## Sample status generation (#37, #38)

The data generation view gains an optional **sample status** checkbox, off by default.  When
checked, every generated sample of every PV also gets a random EPICS-style alarm status
(`NO_ALARM` / `MINOR_ALARM` / `MAJOR_ALARM` / `INVALID_ALARM`, weighted roughly 85/10/4/1) in domain
`epics_alarm`, layer `demo_generator`.  This exercises the Sample Status API added in dp-grpc #121,
and produces data for the new `Explore → Sample Statuses` view and the sample status query filter.

The reported count is an **upsert** count, not an insert count: statuses are keyed on
(PV, timestamp, domain, layer) and fully replace, so re-generating over the same PVs and time range
reports the same number while replacing rather than adding.

A status save failure does not fail the ingestion — the data is already in the archive by then, and
status generation is an opt-in demo extra.  Failures are reported alongside the count.

See [Sample Status (demo)](../../README.md#sample-status-demo) in the README.

## Machine configuration authoring (#27, #36)

New `Metadata → Machine Configuration` view, for creating configuration records and their
activation intervals.  Both sections carry independent tags and attributes.

The activation section stays disabled until a configuration is saved or loaded, because the server
resolves an activation's configuration name on every save and rejects the request if it does not
resolve — gating the section makes that rejection unreachable through normal use.

Both saves are **full-replace upserts**, so an existing record is confirmed before it is
overwritten.  The confirmation names the record that was **found**, not what was typed.

See [Creating machine configurations](../../README.md#creating-machine-configurations) in the README
for the walkthrough.

Activation id collisions are detected in two stages (#36): against activations created in the
current session, and — for a record from an earlier session or another client — against the server.
A supplied activation id is an upsert key, not a label, so a collision silently replaces the
existing record.  End time is required in this version: the server supports open-ended activations,
but one blocks every subsequent activation in its entire category indefinitely.

## PV metadata authoring (#18)

New `Metadata → PV` view, for creating and updating curated PV metadata — aliases, tags,
attributes, description.

See [Creating PV metadata](../../README.md#creating-pv-metadata) in the README for the walkthrough.

The save is a **full-replace upsert**: it replaces the entire record for a PV name, and omitted
fields are not preserved.  An existing record is therefore confirmed before it is overwritten,
which is the likeliest way to lose data in this view.  Loading a record for editing before changing
it arrived with #39 above.

## Column metadata replaces request-level metadata (#17)

The ingestion views' "Request Details" panel is replaced by a **Column Metadata** panel, in both
the generate and import workflows.  Tags, attributes and provenance (source, process) are now
attached to **every column** of the ingested data rather than to the request, which is where the
archive actually stores and queries them.

Unset provenance fields are omitted rather than sent as empty strings, and a panel with nothing
entered sends no metadata field at all.

The README's [Column Metadata](../../README.md#column-metadata) section covers the panel; note that
its screenshot still shows the pre-1.16.0 "Request Details" panel.

## Test coverage and CI (#29)

This release adds the repo's first automated test coverage and a CI build.

- **CI** (`.github/workflows/ci.yml`) builds every pull request, including the dp-grpc and
  dp-service dependencies from `main`.
- **Unit tests** cover the models, the reusable components, and the `DpApplication` parameter
  helpers.
- **View-load smoke tests** load every FXML under `/fxml` that declares a controller, enumerated
  **from the classpath rather than a hand-maintained list**, so a new view is covered the moment its
  file lands.  These catch FXML syntax errors, `fx:id` type mismatches and a throwing `initialize()`.
- **Live integration tests** (`*IT`) exercise the Annotation API, the explore queries, Query V2 and
  the demo database lifecycle against a real in-process ecosystem and a real MongoDB.  They **skip
  rather than fail** when MongoDB is unreachable, so CI stays green while a developer running
  MongoDB gets the coverage from a plain `mvn test`.

`DemoDatabaseLifecycleLiveIT` is the exception: it drops the configured database, so it requires an
explicit `-Ddp.test.allowDemoDatabaseDrop=true` opt-in rather than merely a reachable MongoDB.
Since demo data now survives restarts, reachability is no longer sufficient consent.

## Build and release infrastructure (#21)

All GitHub Actions references are **pinned to full commit SHAs** with trailing version comments,
per osprey-dcs/data-platform#90.  The release job runs with `contents: write` and publishes the
artifacts users download, so a compromised upstream tag there could replace them.  A
`.github/dependabot.yml` keeps the pins from going stale silently.

`release.yml` gains a `workflow_dispatch` rehearsal path, defaulting to a **dry run**: it builds the
app end to end, including both dependencies, and stops before publishing.  Before this the workflow
could only be exercised by cutting a real release.

Release notes are now version-controlled under `doc/release-notes/`, one document per release, and
published as the GitHub release body.  The release job verifies the notes exist **before** starting
the build — this repo builds dp-grpc and dp-service from source first, so leaving that check to the
publish step would surface a missing file three builds late.  A dry run warns instead of failing,
since a rehearsal usually happens before the notes are written.
