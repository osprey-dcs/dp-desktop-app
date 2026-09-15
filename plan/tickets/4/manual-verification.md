# Manual verification — #4 remote gRPC targets

**Why this exists.** No automated test in this repo connects to a remote service, and none can: CI
has no running dp-service instances. The plan calls this document *load-bearing, not a formality*
for exactly that reason — a green build does not prove deployment mode works.

**What is already covered automatically**, and therefore is NOT repeated here as a manual step:

| Claim | Covered by |
|---|---|
| Mode parsing (`demo` / `deployment` / absent / unrecognized / mixed case) | `AppConfigurationTest` |
| The menu matrix in both modes | `MenuGatingTest` |
| The new Tools menu loads (FXML, `fx:id`, handler reference) | `ViewLoadSmokeTest` |
| Demo data survives a restart; the delete drops it; the session reset clears it | `DemoDatabaseLifecycleLiveIT` |
| This run's buckets land in `dp-demo` and **not** in dp-service's default `dp` | `DemoDatabaseLifecycleLiveIT` |

What remains is what genuinely needs running remote services, plus the UI-level checks no headless
test reaches.

Each step names **what would be wrong if it fails**, so a failure is actionable rather than just red.

---

## Setup

Four services are expected on the standard ports:

| Port | Service | Needed for |
|---|---|---|
| 50051 | Ingestion | Part 2 (baseline), and the disabled-write checks |
| 50052 | Query | Part 2, Part 3 |
| 50053 | Annotation | Part 3 (Datasets, Annotations, Machine Configurations) |
| 50054 | Ingestion Stream | **not required** — see the note below |

Check what is actually listening before starting:

```bash
for p in 50051 50052 50053 50054; do printf "%s: " "$p"; \
  (nc -z -G 1 localhost $p 2>/dev/null && echo OPEN) || echo closed; done
```

> ℹ️ **50054 may be closed and that is fine.** gRPC channels connect lazily, so the app constructs
> the ingestion-stream channel at launch without contacting it. Verified: with 50054 closed, launch
> succeeded and logged `creating remote grpc channel to: localhost:50054` with no error. Data Events
> is disabled in deployment mode for this release anyway, so nothing reaches that channel.

Launch in deployment mode. **Two forms are verified to work; the obvious one does not.**

> ❗ **`mvn javafx:run -Ddp.DpDesktopApp.mode=deployment` does NOT work, and fails silently.**
> The plugin forks a JVM that does not inherit Maven's system properties, so the flag is ignored and
> **the app launches in demo mode while appearing to honor the flag**. Verified: with that flag the
> startup log read `application configuration: Demo (in-process)`. `-Djavafx.options=...` and
> `-Djavafx.options.0=...` are ignored the same way. This is the most dangerous failure in the whole
> procedure — every check below would then be run against the wrong target and would "pass".

**Form A — the shaded jar** (simplest; `-D` reaches the JVM directly):

```bash
mvn clean package -DskipTests
java -Ddp.DpDesktopApp.mode=deployment -jar target/dp-desktop-app-1.16.0-shaded.jar
```

**Form B — `javafx:run` with a config file** (an environment variable does reach the forked JVM):

```bash
sed -E 's/^([[:space:]]*)mode: demo$/\1mode: deployment/' \
  src/main/resources/application.yml > /tmp/deployment.yml
grep -n 'mode:' /tmp/deployment.yml     # confirm it now reads "mode: deployment"
env "DP.CONFIG=/tmp/deployment.yml" mvn javafx:run
```

> ❗ The `env` prefix is required, not stylistic. `DP.CONFIG=... mvn javafx:run` fails in **zsh**
> (the macOS default) with `no such file or directory: DP.CONFIG=...`, because the variable name
> contains a dot. Bash accepts the inline form; zsh does not.

Both were verified to log `application configuration: Deployment — localhost:50051`.

> ℹ️ Form B is also the shape a real install uses — ship a deployment `application.yml` and point
> `DP.CONFIG` (or `-Ddp.config=<path>`) at it. Do check the `grep` output: a substitution that
> matches the line but leaves the value unchanged will launch demo mode with a config-override line
> in the log that looks like success.

---

> ❗ **Confirm the mode took effect before trusting anything below.** The startup log line
> `application configuration:` must read `Deployment — localhost:50051`. In the resolved
> configuration line (`initialize dp configuration`), `DpDesktopApp.mode` must be `deployment`.
> Note that the line above it (`initialize config file properties`) may still say `mode=demo` when
> using the `-D` form — that is the file's value before the per-key override is applied, and it is
> **not** a failure. The status bar is the quickest check: it must read
> `Deployment — localhost:50051`.

---

## Part 1 — The app is pointed where you think it is

1. Read the status bar (bottom right) and the window title.

**Expect**: both say `Deployment — localhost:50051`. In demo mode they say `Demo (in-process)`.

| Check | What a failure means |
|---|---|
| Status bar names the deployment and its host | `AppConfiguration.describe()` is not reaching `MainViewModel`, and the user has no way to tell which archive they are on — the single cheapest safeguard against running a demo against production |
| Startup log has **no** MongoDB client activity | deployment mode constructed a Mongo client, which the whole design says is structurally impossible; treat as a release blocker |

> ℹ️ **How to check the Mongo claim honestly.** Grepping for `mongo` matches the configuration dump,
> which contains `MongoClient.dbHost` as a *key* in both modes — it will show hits and mean nothing.
> Grep for client activity instead:
> ```bash
> grep -cE "MongoSyncClient|MongoInterface|overriding db name globally|cluster created" <logfile>
> ```
> Verified: **0** in deployment mode, **7** in demo mode. The demo number is the control — a zero
> from a pattern that never matches anything proves nothing.

---

## Part 2 — The write menus are disabled

1. Open each menu and confirm the state of every item.

**Expect**, in deployment mode:

| Menu item | State |
|---|---|
| Ingest → Generate | **disabled** |
| Ingest → Import | **disabled** |
| Metadata → PV | **disabled** |
| Metadata → Machine Configuration | **disabled** |
| Tools → Delete Demo Data | **disabled** |
| Explore → Data, PV Statistics, PV Metadata, Providers, Datasets, Annotations, Machine Configurations, Sample Statuses | **enabled immediately**, with no ingestion |
| Explore → Data Events | **disabled** |

| Check | What a failure means |
|---|---|
| The four write items are disabled | the `writeEnabled` binding is not applied; the app can write PV data or curated metadata into a real archive |
| Tools → Delete Demo Data is disabled | the binding is missing. Note this one has a second line of defense — the handler re-checks the mode and refuses — but a disabled-looking menu that is actually live is the failure this check exists to catch |
| The eight Explore items are enabled **before** any ingestion | `exploreEnabled` is still keyed on `hasIngestedData`, which is permanently false in deployment mode — the app would be unusable for its actual purpose |
| Data Events is disabled | expected for this release; re-enabling it is follow-on #3 |

---

## Part 3 — The Explore views actually query the remote archive

This is the part nothing automated covers.

1. `Explore > PV Statistics`. Search with the pattern `.*`.

**Expect**: the PVs held by the remote archive, with data type, first/last timestamp and sample
period populated.

> ❗ **An empty result is ambiguous and must be resolved, not accepted.** The remote services use
> dp-service's default database (`dp`), which is *not* the demo database — so a freshly started
> deployment can legitimately hold nothing. An empty table then looks identical to a broken query
> path. Before concluding anything, confirm the archive is non-empty independently:
> ```bash
> mongosh "mongodb://admin:admin@localhost:27017/" --quiet \
>   --eval 'db.getSiblingDB("dp").buckets.countDocuments()'
> ```
> (Credentials are required — without them this returns
> `MongoServerError: Command aggregate requires authentication`, which is an auth failure rather
> than an empty archive. Use the `MongoClient.*` values from your `application.yml` if they differ.)
> If that is 0, the archive is genuinely empty: seed it (see below) and repeat. This was hit during
> verification — the first remote query returned 0 PVs, and the archive really was empty.

2. `Explore > Providers`. Search with a name fragment, and separately with a provider ID.
3. `Explore > Datasets`, `Explore > Annotations`, `Explore > Machine Configurations`,
   `Explore > PV Metadata`, `Explore > Sample Statuses` — run one search in each.
4. `Explore > Data`. Select PVs and a time range covering the archived data, and submit.

| Check | What a failure means |
|---|---|
| Results render, with populated columns | the remote channel works but a `PropertyValueFactory` binding is stale — a silently blank column |
| A query against a wide time range returns without `RESOURCE_EXHAUSTED` | `maxInboundMessageSize` is not raised on the remote channels. **This is the failure the plan singles out as easiest to omit and hardest to diagnose**, because it appears only on a large page against a real deployment |
| The chart in Explore → Data renders beside the table | the synthesized timestamp column is missing — the table still renders, so the chart is the only signal |
| Searches complete in well under a second | see the wedged-server note below |

> ❗ **A hung server presents exactly like a broken client.** During verification, a provider text
> search failed with `QueryProvidersResponseObserver timed out waiting for finishLatch after 60
> seconds` while an ID search on the same channel returned in 15 ms. It was **not** an app defect:
> the query service process had wedged. Restarting it made the same search return in 302 ms.
> **Before filing a client bug against a timeout, restart the service and retry.** A useful
> discriminator: if some calls on a channel are fast and others hang, the transport is fine and the
> server is the suspect.

### Seeding the remote archive, if it is empty

Deployment mode deliberately disables every ingestion path in the UI, so the archive must be seeded
by something else — a dp-service ingestion benchmark, another producer, or a temporary demo-mode run
pointed at the same database. Do **not** re-enable the write menus to seed it.

---

## Part 4 — Demo mode is unchanged

The regression check. Relaunch with no flags:

```bash
mvn javafx:run
```

| Check | What a failure means |
|---|---|
| Status bar reads `Demo (in-process)` | mode defaulting is broken; an absent or unrecognized value **must** resolve to demo, never to a connection attempt against a production host |
| All Ingest / Metadata / Tools items are enabled | the mode predicate is inverted |
| Explore items are disabled on an **empty** demo archive | `exploreEnabled` lost its archive/ingestion arms, and the views will be empty with nothing explaining why |
| Explore items are **enabled** when the demo database already holds data | the launch probe is not running or its result is not reaching the menu. This was a real bug found during verification: leftover demo data was unreachable because the menu was gated on this session having ingested |
| `Ingest > Generate` ingests successfully | the in-process path regressed |
| **Relaunching shows the previous session's data** | the drop is back on the launch path — the #4 behavior change has been reverted |
| After relaunching on a populated demo database, **Explore is enabled without ingesting** | the archive probe regressed; leftover data is present but unreachable from the UI |
| After `Tools > Delete Demo Data`, Explore goes **disabled** again | `resetIngestedDataState()` is not clearing `archiveHasData`, leaving the menu enabled over a dropped database |
| `Tools > Delete Demo Data` prompts, naming `dp-demo`, and clears the data | covered by `DemoDatabaseLifecycleLiveIT` at the API level; this is the dialog and menu-state half |

---

## What this verification established, and what it did not

**Established** (observed, 2026-09-14, against services on 50051-50053 with 50054 closed):

- Deployment-mode init against real remote services: succeeded in ~130 ms.
- Status label resolved to `Deployment — localhost:50051`, over a file value of `demo` — verified
  through all three override paths: `-D` on a surefire-forked JVM (the probes), `-D` on the shaded
  jar, and `DP.CONFIG` with `javafx:run`. The one path that does **not** work is `-D` passed to
  `mvn javafx:run`, as the Setup section warns.
- **A full ingest → read-back round trip through remote channels**: a stamped PV went from
  `pvStats count=0` before ingest to `count=1` after, proving registration and ingestion reached
  50051 and the read-back reached 50052. The before/after transition is what makes this non-vacuous —
  a bare "1" could have been pre-existing data.
- `queryProviders` returned 3 records through the remote channel.
- **Zero** Mongo client log lines in deployment mode, against **7** in demo mode.
- A closed 50054 did not prevent launch.

> ℹ️ **Note on the startup log in Part 4.** A failed archive probe logs a WARN saying the Explore
> menu is being enabled without confirming the archive holds anything. That is the designed
> behavior, not an error: a probe that cannot reach the query service must not hide data. Observed
> in practice when the query service was down — the menu stayed reachable, which is the point.

**Not established, and still worth a human at the keyboard:**

- Everything above the view model: FXML rendering, clicks, navigation, the editor forms, the chart.
  The evidence above came from API-level probes, not from the GUI.
- Any deployment that is not `localhost` — DNS resolution, a real network path, latency.
- TLS, which this release does not implement (follow-on #4).
- Large-page behavior against a substantial remote archive, which is where a missing
  `maxInboundMessageSize` would surface.
