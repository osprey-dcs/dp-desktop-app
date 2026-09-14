# Manual verification — #39 tasks 3, 4, 5

**What this covers and why it exists.** `ExploreQueryLiveIT` verifies every *query path* in these
three tasks against a real MongoDB — alias resolution, clock alignment, boundary trimming, the
half-filled range. What it cannot reach is everything above the view model: FXML rendering, clicks,
navigation between views, and the editor forms. Those are what this scenario is for.

Each step names **what would be wrong if it fails**, so a failure is actionable rather than just red.

---

## Setup

```bash
cd ~/dp/dp-java/dp-desktop-app
mvn clean compile javafx:run
```

MongoDB must be running. Separately running dp-service instances on 50051-50053 are **not** used:
the app has no remote-connection path yet and always starts its own in-process services against the
same database.

---

## Part 1 — Seed data (Ingest → Generate)

1. `Ingest > Generate`.
2. Provider Details: name `manual-check`, description anything.
3. Column Metadata: set a time range of about **2 minutes**, ending now.
4. Add two PVs — e.g. `MANUAL:PV:A` and `MANUAL:PV:B`, type `float`, 10 values/second.
5. **Tick "Generate sample statuses"** (off by default — this is the step that makes Part 2 possible).
6. Ingest.

**Expect**: success message naming the PV and bucket counts, *and* a sample status count. The Explore
menu becomes enabled.

> ❗ If the status count is absent, the demo status generation did not run and Part 2 will find
> nothing. Re-check the checkbox.

> ℹ️ The reported status count is an **upsert** count. Re-running over the same PVs and window
> replaces rather than adds, so the number stays the same — that is correct, not a bug.

---

## Part 2 — Sample Status Explore (task 3)

1. `Explore > Sample Statuses`.
2. Set the window to the range you just ingested. Leave PV names, domains and layers blank.
3. Search.

**Expect**: one row **per status**, not per bucket — roughly 1,200 rows for 2 PVs × 10/s × 60s.
Columns: PV name, timestamp, domain `epics_alarm`, layer `demo_generator`, a raw code 0-3, and a
label (`NO_ALARM` / `MINOR_ALARM` / `MAJOR_ALARM` / `INVALID_ALARM`).

| Check | What a failure means |
|---|---|
| Row count is in the thousands, not single digits | buckets are being rendered directly instead of expanded |
| Timestamps are evenly spaced at 0.1s | the `SamplingClock` is being expanded by accumulation rather than arithmetically |
| Every Label cell is populated | the code→label map is not being applied |
| Confidence and Reason are **empty** | correct — the demo generator deliberately writes neither |

4. **Narrow the window to ~5 seconds inside the range** and search again.

**Expect**: about 100 rows, and **no timestamp outside the window you asked for**.

> ❗ This is the boundary trim. If rows appear outside the window, the client-side trim regressed —
> the server returns boundary buckets whole, so it cannot be relied on to do this.

5. Set a window far in the future (e.g. next year) and search.

**Expect**: zero rows and a clean "0" status — not an error, not a spinning progress indicator.

---

## Part 3 — PV Metadata Explore + editor (task 4)

### 3a. Create a record with an alias

1. `Metadata > PV`.
2. PV Name: `MANUAL:CANONICAL`
3. Aliases: add `MANUAL:OLDNAME`
4. Tags: add `manual-tag`. Attributes: add `owner` = `manual`.
5. Description: `original description`. Save.

**Expect**: success naming `MANUAL:CANONICAL`.

### 3b. Search by name, in each mode

1. `Explore > PV Metadata`.
2. PV Name `MANUAL:CANONICAL`, mode **Exact** → finds it.
3. Change to `MANUAL:` with mode **Starts with** → finds it.
4. Change to `CANON` with mode **Contains** → finds it.
5. Clear the name, search with everything blank → returns all PV metadata records.

> ❗ If the all-blank search **errors**, a blank field is being sent as an empty criterion rather
> than omitted. If it returns suspiciously many results while a *filled* field is also set, a blank
> field is being sent as a match-everything prefix.

### 3c. THE ALIAS TRAP — the most important check in this document

1. Clear the form. Put `MANUAL:OLDNAME` in the **Alias** field, mode Exact. Search.

**Expect**: one row, whose **PV Name column reads `MANUAL:CANONICAL`** — not `MANUAL:OLDNAME`.

2. **Click the PV name hyperlink.**

**Expect**: the PV Metadata editor opens with:
   - PV Name = **`MANUAL:CANONICAL`** (the canonical name, *not* the alias you searched by)
   - Aliases list contains `MANUAL:OLDNAME`
   - Tags contains `manual-tag`
   - Attributes contains `owner=manual`
   - A status line saying the save replaces the entire record

> ❗❗ **If PV Name shows `MANUAL:OLDNAME`, stop and report it.** Saving would create a *second*
> record under the alias while leaving the original untouched — silently, because the save is a
> full-replace upsert.

> ❗ **If Aliases / Tags / Attributes are empty**, the load populated ViewModel properties instead of
> the components. Saving would then erase all three from the stored record with no warning.

### 3d. Round-trip the edit

1. In the editor just loaded, change **only** Description to `edited description`. Save.
2. Return to `Explore > PV Metadata`, search Alias = `MANUAL:OLDNAME` again.

**Expect**: still **one** row, `MANUAL:CANONICAL`, description now `edited description`, and
**aliases, tags and attributes all still present**.

> ❗ Two rows means a duplicate was created under the alias. Missing tags/aliases/attributes means
> the load-save round trip dropped them. Either is a data-loss defect.

---

## Part 4 — Configuration Explore + editor (task 5)

### 4a. Create a configuration and an activation

1. `Metadata > Machine Configuration`.
2. Name `manual-config`, Category `manual-cat`, description anything.
3. Tags: `manual-tag`. Attributes: `owner` = `manual`. Save.

**Expect**: success, and **section 2 (Activations) becomes enabled**, labelled with `manual-config`.

4. In section 2: set start/end dates a day apart, Client Activation ID `manual-act-1`. Add.

**Expect**: it appears in the session list as `manual-act-1: <start> -> <end>`.

### 4b. Search both tabs

1. `Explore > Machine Configurations` → **Configurations** tab.
2. Name `manual-config`, mode Exact. Search → one row with your category, tags and attributes.
3. Switch to the **Activations** tab. Configuration field `manual-config`. Search → one row,
   `manual-act-1`, with your start and end times.

### 4c. THE HALF-FILLED RANGE

1. Still on Activations, tick **Overlapping** and set **only the start date** (leave the end blank).
2. Search.

**Expect**: the search is **refused** with a message about setting both bounds or neither. No results
change, no spinner left running.

> ❗ If it instead runs and returns results, the guard regressed. The server does **not** reject a
> half-filled range — it silently ignores the bound, returning a broader result set that looks
> correct. That is precisely why this is refused client-side.

3. Now set **both** bounds to a window that *contains* your activation. Search → the activation
   comes back.
4. Set both bounds to a window a year later. Search → **no** results.

> ❗ If step 4 still returns the activation, the range criterion is being dropped rather than applied.

### 4d. Open-ended activations

1. Go back to `Metadata > Machine Configuration`. It should be a **fresh, empty** form.
2. Re-save `manual-config` (same name and category) — confirm the **overwrite warning** when it
   appears, since the record exists.
3. Add a second activation, ID `manual-act-2`, with **both dates set** (the UI requires an end).
4. Search Activations for `manual-config`.

**Expect**: both activations listed, each with real start and end times.

> ℹ️ The UI requires an end time, so "open-ended" is not producible through this view. If a record
> written by another client has no end time, the End column must read **`open-ended`** — never a
> 1970 date. A 1970 date means field presence is being ignored.

### 4e. Load a configuration for editing

1. On the **Configurations** tab, search for `manual-config` and **click the name hyperlink**.

**Expect**: the Machine Configuration editor opens with name, category, description, tags and
attributes all populated, **and section 2 (Activations) already enabled**, labelled `manual-config`.

> ❗ **If section 2 is disabled**, the load-for-edit gate regressed. A loaded record proves the
> server holds that configuration, which is exactly the condition the gate protects.

> ❗ **If Tags/Attributes are empty**, same component-population defect as 3c — saving would erase
> them.

2. **Retype the Name field** to `something-else` without saving.

**Expect**: section 2's label still reads **`manual-config`**.

> ❗ If it follows your typing, an activation added now would target a configuration that does not
> exist, and the server would reject it confusingly.

### 4f. Navigate from an activation

1. `Explore > Machine Configurations` → Activations tab → search `manual-config`.
2. Click the **Configuration** name in an activation row.

**Expect**: the editor opens populated with `manual-config` (resolved via `getConfiguration()`).

---

## Part 5 — Menu and navigation sanity

1. Check `Explore` contains, in order: Data, PV Statistics, PV Metadata, Providers, Datasets,
   Annotations, Machine Configurations, Sample Statuses, Data Events.
2. Open **PV Statistics** — the ingestion-derived view (data type, timestamps, sample period). This
   is a *different* view from PV Metadata; confirm they do not open the same thing.

> ❗ If PV Statistics opens the metadata explorer, the C4 rename crossed its wires.

3. Visit each new view, navigate away, and return. Nothing should throw, and forms should be in a
   clean state.

---

## Cleanup

Records created here are named `manual-*` / `MANUAL:*` and are inert. To remove them:

```javascript
mongosh dp-demo --username admin --password admin --authenticationDatabase admin
db.pvMetadata.deleteMany({pvName: /^MANUAL:/})
db.configurations.deleteMany({configurationName: /^manual-/})
db.configurationActivations.deleteMany({clientActivationId: /^manual-act/})
db.sampleStatuses.deleteMany({pvName: /^MANUAL:/})
db.buckets.deleteMany({pvName: /^MANUAL:/})
db.pvStats.deleteMany({_id: /^MANUAL:/})
db.providers.deleteMany({name: "manual-check"})
```

---

## Reporting

For anything marked ❗ or ❗❗, note the step number and what you saw instead. The ❗❗ in **3c** is
the one worth interrupting for — it is a silent data-loss path.
