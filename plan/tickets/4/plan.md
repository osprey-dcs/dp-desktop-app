# Plan: support remote gRPC targets alongside the in-process demo (issue #4)

- **Ticket**: [osprey-dcs/dp-desktop-app#4](https://github.com/osprey-dcs/dp-desktop-app/issues/4)
- **Status**: scoped 2026-09-14 against dp-desktop-app `b6ce98d` (branch `issue-39-dialog-sizing`),
  with the installed `dp-service-1.16.0.jar`. Every claim below was verified by reading the source
  named, not by reading the ticket.
- **Constraint**: a release is planned this week. This plan is deliberately the *minimum* that makes
  remote targets usable and safe, with everything else pushed to named follow-on tickets (§Follow-ons).

## Summary

The app hardwires a self-contained in-process service ecosystem. This ticket adds a second mode that
points the same UI at already-running remote services, selected by configuration at launch.

The seam needed already exists. `DpApplication.init()` (`DpApplication.java:572`) is the **only**
place `InprocessServiceEcosystem` is referenced, and `ApiClient` (dp-service
`client/ApiClient.java:5`) already takes four plain `ManagedChannel`s. So the mode difference is
confined to *how those four channels are constructed*; all ~28 `api.*Client.*` call sites are already
transport-agnostic and none of them change.

Three things make this more than a channel swap, and they are what the tasks below are actually about:

1. **`InprocessServiceEcosystem.init()` unconditionally drops the database** before starting any
   service (`InprocessServiceEcosystem.java:28` → `MongoInterface.prepareDemoDatabase()` →
   `dropDemoDatabase()`). This must be structurally unreachable in deployment mode, not merely
   skipped.
2. **`hasIngestedData` currently means two different things** — "this session ingested" and "the
   archive has something to explore". In deployment mode the first is permanently false and the
   second is true from launch.
3. **Write features must be gated**, and the gate must be one rule in one place rather than nine.

## Decisions taken during scoping

Recorded with their reasoning, because each closes off an alternative that will otherwise be
re-proposed later.

### One application, one runner, mode from config — not two applications

The ticket leaves this open. **One app.** Two runners would mean two shaded jars, two CI paths, and
two places for the menu gating to drift, for code that is ~95% shared either way. A second `main`
buys nothing a config key does not.

It is also the *less* safe option for the hazard that matters most here: a separate "demo" jar that
still contains the drop-database path is a jar someone can launch against a production MongoDB. With
one app, deployment mode never constructs a Mongo client at all.

Mode is read through the existing `ConfigurationManager`, which already provides a whole-file
override (`-Ddp.config=<path>`, or the `DP.CONFIG` env var) and per-key overrides
(`-Ddp.DpDesktopApp.mode=deployment`) — see `ConfigurationManager.java:20-24` and `:96-107`. No build
changes, and the installer ships a deployment `application.yml` exactly as README.env already
describes.

**Demo is the default.** `mvn javafx:run`, `DpDesktopApplicationRunner`, and the existing shaded jar
behave as they do today with no flags. An absent or unrecognized mode value is demo.

### Deployment mode means "no ingestion, no metadata authoring" — not strictly read-only

Confirmed with the ticket author during scoping. Dataset save, annotation save and export stay
**enabled** in deployment mode: they are the analysis workflow, they do not write PV time-series or
curated PV/configuration metadata, and they live inside data-explore rather than on the menu bar.

This is the one place the mode's name is not literally accurate, so the plan states the rule as
implemented rather than as a slogan. Gating those too would mean reaching inside data-explore's tabs
— materially more work than menu-item bindings, and it would remove the Dataset and Annotation
Builders from every real deployment until a follow-on.

### The demo database is no longer dropped at launch

Confirmed with the ticket author during scoping; the ticket raised it as an open question
("do we want to always drop and recreate dp-demo database, or would it be better to just have a
Tools->Delete Data option"). **Tools → Delete Demo Data**, on request, with confirmation.

Worth stating plainly because it is a behavior change shipping in the same release: this is the
**first build in which stale demo data survives a restart**. A demo that previously always started
clean can now start with a previous session's leftovers — including sample statuses and provider
registrations. That is the reason the delete action must be an obvious, labeled menu item rather
than something tucked away, and the reason the home view names the database.

The action is demo-mode only and hard-gated: it is bound to the same mode predicate as the write
features *and* re-checks the mode before acting, so a future refactor that breaks a binding cannot
turn it into a production-data delete.

### Data Events is disabled in deployment mode for this release

Confirmed with the ticket author during scoping. `subscribeDataEvent` is a read (it monitors a live
stream), so it arguably belongs with the Explore items. But the view was built against subscriptions
created during *this session's* ingestion, and that premise is unverified against a live archive.
Shipping it untested would be a view whose failure mode is unknown; §Follow-ons carries a ticket to
confirm it.

## Mode matrix

The single source of truth for the implementation. "Explore" here is the eight Explore items other
than Data Events.

| Feature | Demo mode | Deployment mode | Enforced by |
|---|---|---|---|
| Ingest → Generate | enabled | **disabled** | `generateEnabled` |
| Ingest → Import | enabled | **disabled** | `importEnabled` (new binding — currently unbound) |
| Metadata → PV | enabled | **disabled** | `pvMetadataCreateEnabled` (new binding) |
| Metadata → Machine Configuration | enabled | **disabled** | `machineConfigCreateEnabled` (new binding) |
| Explore → Data, PV Statistics, PV Metadata, Providers, Datasets, Annotations, Machine Configurations, Sample Statuses | after ingestion | **enabled at launch** | `exploreEnabled` (derived) |
| Explore → Data Events | after ingestion | **disabled** | `dataEventsEnabled` |
| Dataset save / Annotation save / Export (in-view) | enabled | enabled | — (unchanged) |
| Tools → Delete Demo Data | enabled | **disabled** + runtime mode re-check | `deleteDemoDataEnabled` + guard |
| In-process services + Mongo client | constructed | **never constructed** | `DpApplication.init()` branch |
| Database dropped at launch | **no longer** (was: yes) | n/a — no Mongo client | `MongoInterface` call removed from `init()` |

Import is disabled despite being real data. "No writes to the archive in deployment mode, except the
annotation-workflow records named above" is a rule that can be stated and checked; "no writes except
import" is a rule with an exception whose safety depends on what the user happens to import.
Re-enabling it is a named follow-on, not an oversight.

## Tasks

### Task 1 — mode configuration and channel construction

**New**: `com.ospreydcs.dp.gui.config.AppMode` (enum `DEMO` / `DEPLOYMENT`) and
`AppConfiguration`, which reads the mode and, for deployment, the four target host/port pairs
through `ConfigurationManager`.

Config keys:

```yaml
DpDesktopApp:
  mode: demo          # demo | deployment

GrpcClient:
  ingestionConnectString:       localhost:50051   # already exists upstream
  queryConnectString:           localhost:50052   # new
  annotationConnectString:      localhost:50053   # new
  ingestionStreamConnectString: localhost:50054   # new
```

**Follow dp-service's connect-string convention, not hostname+port.** Verified during scoping:
`IngestionServiceClientUtility.IngestionServiceGrpcClient`
(dp-service `ingest/utility/IngestionServiceClientUtility.java:22`) already establishes
`GrpcClient.ingestionConnectString` (default `localhost:50051`) and builds its channel with
`Grpc.newChannelBuilder(connectString, InsecureChannelCredentials.create())` plus the three
`GrpcClient.keepAlive*` settings this app's own `application.yml:20-30` already carries.

The other three services have **no** such utility and no connect-string key — only
`IngestionServiceClientUtility` exists (`find src/main/java -name '*ClientUtility.java'` returns one
file). So three keys are new. Naming them to match the one that exists is the point: a hostname+port
scheme would have been the more obvious design and would have quietly contradicted the only
precedent in the codebase, leaving two competing ways to address the same services. A connect string
also expresses things host+port cannot (DNS-based name resolution, `unix:` targets).

`GrpcClient.hostname` (`application.yml:21`) is **not** the key to build on — nothing in dp-service
reads it for client channel construction, so it is a decoy.

Channel construction should reuse the utility's shape — connect string + `InsecureChannelCredentials`
+ the three keepalive settings read from config — rather than a bare `ManagedChannelBuilder`. Whether
to extract a shared four-service utility into dp-service or keep it app-side is an implementation
call; app-side is fine for this release, since dp-service work would put the release on an upstream
reinstall (see CLAUDE.md's "Dependency Updates").

**`DpApplication.init()` branches on mode:**

- `DEMO` — unchanged from today (construct `InprocessServiceEcosystem`, then `ApiClient`).
- `DEPLOYMENT` — construct four `ManagedChannel`s via `ManagedChannelBuilder.forAddress(...)`, then
  the same `ApiClient`. `inprocessServiceEcosystem` stays **null**, so nothing can reach the Mongo
  client or the drop path.

`fini()` branches correspondingly: shut the remote channels down (with a bounded
`awaitTermination`), or `inprocessServiceEcosystem.fini()` as today.

**`maxInboundMessageSize` must be raised on the remote channels too.** The reasoning in
`InprocessServiceBase.java:53-63` is a property of the *server's* page budget, not of the in-process
transport: the server's outgoing budget is 4,096,000 bytes measured on sample values only, excluding
the timestamp list, column framing, names and envelope, and accounted per whole bucket. A remote
channel left at gRPC's 4 MB default has no headroom, and the failure is a `RESOURCE_EXHAUSTED` on
whichever page overshoots, with nothing identifying the cause. This is the single easiest thing to
omit here and the hardest to diagnose afterward — it would present as "Query V2 is broken against
real deployments" long after the release. The constant moves somewhere both paths read it.

Plaintext only for this release; TLS is a named follow-on.

### Task 2 — menu gating

`MainViewModel` gains the mode and one derived rule rather than nine edits:

```
exploreEnabled  =  deploymentMode  ||  hasIngestedData
writeEnabled    =  !deploymentMode
```

`updateMenuStatesFromApplicationState()` (`MainViewModel.java:141`) sets the eight Explore properties
from `exploreEnabled`; Generate, Import, and the two Metadata items bind to `writeEnabled`; Data
Events keeps `hasIngestedData && !deploymentMode`.

Two menu items are currently **unbound** and default-enabled — `importMenuItem`
(`MainController.java:80`, "now always enabled (no binding needed)") and the two Metadata create
items (`:91`). They need real bindings and new properties in `MainViewModel`; they are the items most
likely to be missed, because nothing in the controller currently mentions them.

`connectionStatusLabel` (`main-window.fxml:57`) is currently the hardcoded string `"In-Process Mode"`.
It becomes the live mode indicator — `Demo (in-process)` or `Deployment — <host>` — which is the
cheapest possible answer to "which archive am I pointed at". The window title gains the same
suffix, and `HomeViewModel`'s hint text (`HomeViewModel.java:30,52`) branches, since "Start by using
the Ingest→Generate or Ingest→Import menus" instructs the user to use two menus that are disabled in
deployment mode.

### Task 3 — demo database lifecycle

Today `InprocessServiceEcosystem.init():29` calls `MongoInterface.prepareDemoDatabase()`, which does
**two** things in one call (`MongoInterface.java:22-30`): it overrides the database name globally,
and it drops the database. Only the *drop* is removed.

The name override must still happen, and still before any service starts. Dropping it along with the
drop would silently point the demo at the default production database name — a worse bug than the one
being fixed, and an easy mistake given the two live in one method.

Note the override cannot simply be lifted into `InprocessServiceEcosystem`:
`MongoClientBase.setMongoDatabaseName()` is **`protected static`** (dp-service
`common/mongo/MongoClientBase.java:407`), so `MongoInterface` can call it only because it extends
`MongoSyncClient`. The override therefore stays inside `MongoInterface` — split
`prepareDemoDatabase()` into a name-override-and-init path (called from `init()`) and a separate
`dropDemoDatabase()` reachable only from the new menu action.

`dropDemoDatabase()` (`:33`) is already public and already separate, so the delete action has an
entry point today.

**The live ITs do not depend on drop-on-launch** — verified, since removing it would otherwise make
them accumulate state across runs. `AnnotationApiLiveIT` and `ExploreQueryLiveIT` already namespace
every record with a per-run `STAMP` (`AnnotationApiLiveIT.java:67`) and delete them in `@AfterAll`,
precisely so repeated runs cannot collide. They keep working unchanged.

Add `Tools → Delete Demo Data` to the menu bar: confirmation dialog naming the database, then
`MongoInterface.dropDemoDatabase()` on a background task, then reset the session state
(`hasIngestedData`, `pvNames`, counts) so the menus and home view return to their pre-ingestion
state rather than claiming data that no longer exists.

### Task 4 — tests

Unit-testable without a service ecosystem, in the style the repo already uses for `emptyToNull()`
and `accumulatePages()`:

- `AppConfigurationTest` — mode parsing: `demo`, `deployment`, absent, unrecognized, mixed case. An
  unrecognized value **must** resolve to demo; resolving it to deployment would turn a typo into a
  connection attempt against a production host.
- `MenuGatingTest` — the mode matrix above, asserted per menu item in both modes. This is the table
  that prevents a future menu item from being added with no mode rule, which is how the two unbound
  items in task 2 came to exist.
- `ViewLoadSmokeTest` already enumerates FXML from the classpath, so the new Tools menu and any new
  `fx:id`s are covered the moment they land — provided `initialize()` stays dependency-free.

Not covered, and stated rather than implied: **an actual connection to remote services**. That needs
running dp-service instances, which CI does not have. Manual verification instead
(`plan/tickets/4/manual-verification.md`), following the `#39` precedent: launch against
`localhost:50051-50054` with real services, confirm the Explore views query, confirm the write menus
are disabled, confirm no Mongo client is constructed (log absence), and confirm the demo database
survives a restart.

## Risks

**Pointing demo mode at a production MongoDB.** Mitigated structurally rather than by a flag: in
deployment mode `DpApplication` never constructs the ecosystem, so `MongoInterface` is unreachable.
After task 3 the drop is not on any launch path at all.

**The `maxInboundMessageSize` omission** (task 1). Called out there because it is invisible until a
large Query V2 page fails in a real deployment.

**Stale demo data across restarts** — new in this release, per the decision above.

**A green build does not prove remote mode works.** No automated test connects to a remote service.
The manual verification is load-bearing, not a formality.

## Follow-ons (not in this ticket)

1. Re-enable **Ingest → Import** in deployment mode — real data import against a live archive.
2. Re-enable **Metadata → PV** and **Metadata → Machine Configuration** in deployment mode.
3. Verify and re-enable **Data Events** against remote targets.
4. **TLS / authentication** for remote channels — plaintext only here.
5. **File → Connection** dialog: switch targets at runtime. Currently a disabled stub
   (`main-window.fxml:13`); this ticket sets the mode at launch only.
6. Decide whether random data generation should be removed entirely (raised in the ticket body).
