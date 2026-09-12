# Plan: detect activation id collisions against the server (issue #36)

- **Ticket**: [osprey-dcs/dp-desktop-app#36](https://github.com/osprey-dcs/dp-desktop-app/issues/36)
- **Prerequisite**: [dp-service#243](https://github.com/osprey-dcs/dp-service/issues/243) — **CLOSED, merged as PR #247**. The wrappers this ticket needs are present in the installed 1.16.0 jar.
- **Parent**: sub-issue of [#39](https://github.com/osprey-dcs/dp-desktop-app/issues/39) task 5.
- **Status**: scoped 2026-09-12 against dp-desktop-app `889b5c3`, dp-grpc `6dfff3f`, dp-service `89bb822` (`main`). Verified by reading the merged `AnnotationClient` source and confirming the classes are present in the installed jar, not by reading the ticket.

## Triage verdict: unblocked, and smaller than the ticket implies

The ticket says "the fix needs a dp-service change first, which is out of scope for a
dp-desktop-app PR". **That is no longer true.** dp-service #243 merged and added both arms:

| Wrapper | `AnnotationClient` | Result class |
|---|---|---|
| `getConfigurationActivationById(String)` | `:2538` | `GetConfigurationActivationApiResult` |
| `getConfigurationActivationByCompositeKey(String, Timestamp)` | `:2562` | same |

Both are in `~/.m2/.../dp-service-1.16.0.jar` (installed 2026-09-11). Every dp-service commit
since that install is #232 bucket-span work, which does not touch this surface.

**Proposed-work item 1 in the ticket body is therefore already done** and should be struck. Only
item 2 — the dp-desktop-app side — remains. This is a small, self-contained ticket that can be
implemented immediately and independently of #39.

### Why two named methods, not one nullable-argument method

#243 deliberately exposed the proto's `oneof key` as two explicitly named methods so that "both
keys supplied" and "neither supplied" cannot arise client-side. This ticket uses the
`ById` arm only: the view's collision check is keyed on the id the user typed.

## Background: what the session-local guard already does

`MachineConfigurationViewModel.addActivation()`
(`src/main/java/com/ospreydcs/dp/gui/MachineConfigurationViewModel.java:453`) already has the
complete shape this ticket extends. Reading it first is worthwhile — the extension point is one
`if`.

- `findSessionActivation(id)` (`:601`) looks the id up in the session list.
- On a hit, `activationOverwriteConfirmation.confirmOverwrite(id)` (`:512`) prompts, and a decline
  aborts the save.
- After a successful save, the session list is **reconciled in place** rather than appended to
  (`:570-576`), so a replacement does not leave a stale row beside its replacement.
- A comment at `:498-506` marks exactly the gap this ticket closes, and names the missing wrapper.

So the confirmation dialog, the abort path, and the list reconciliation all exist and are correct.
What is missing is a second existence check, consulted only when the session-local one misses.

## The change

### T1 — `DpApplication.getConfigurationActivationById(String clientActivationId)`

A thin wrapper next to `getConfiguration()` (`DpApplication.java:1523`), matching its shape
exactly:

```java
public GetConfigurationActivationApiResult getConfigurationActivationById(String clientActivationId) {
    return api.annotationClient.getConfigurationActivationById(clientActivationId);
}
```

Javadoc must state the `isReject()` contract, copying the wording already established for
`getConfiguration()` and restated in CLAUDE.md's "API Integration Patterns":

> A missing record is reported as a **rejection**, not an empty successful result. Branch on
> `ApiResultBase.isReject()`, never on `isError()` — a service that is unreachable also sets
> `isError`, and treating that as "no existing record" would suppress the overwrite warning.
> `REJECT` also covers server-side validation failures, so reading it as not-found is only safe
> once the request itself is known to be valid.

The composite-key arm is **not** wrapped. The view has no use for it (it always has the typed id),
and an unused wrapper is a surface to keep correct for no benefit. Note this decision so a later
reader does not read the omission as an oversight.

### T2 — extend the pre-save check in `addActivation()`

The current block at `:508-517` becomes a two-stage check. Stage 1 is unchanged. Stage 2 runs
**only when stage 1 misses and the id is non-blank**:

```
if (id is blank)                      -> no check at all; the server generates the id
else if (session list has id)         -> confirm (existing path)
else                                  -> server check: getConfigurationActivationById(id)
                                           isReject()  -> no such record; proceed silently
                                           success     -> confirm; a decline aborts
                                           isError()   -> see D3
```

### D1 — the server check runs on the background thread, not the FX thread

`addActivation()` currently does all its validation on the FX thread and only then starts the
`Task`. A network round trip must not join that. The check therefore moves **into** the task body,
ahead of the save call, and reuses the existing `runOnFxThreadAndWait()` seam to raise the
confirmation dialog — the same mechanism the configuration-save path already uses for its
`getConfiguration()` overwrite warning.

That seam is already bounded and already returns `Boolean` so the caller can distinguish
"declined" from "never answered" (CLAUDE.md: *"An unbounded await deadlocks the save thread if the
FX thread is gone… A timeout is treated as do not save"*). Reusing it means this ticket inherits
that correctness rather than reproducing it. **Do not add a second waiting mechanism.**

The session-local check at `:508` stays on the FX thread: it reads `activations`, an observable
list, and reading it off-thread is exactly the hazard the existing code avoids elsewhere by
copying component lists on the FX thread before the task starts.

### D2 — the outcome is typed, not a message prefix

`addActivation()`'s sibling `saveConfiguration()` path already carries `SaveOutcome` /
`PreSaveOutcome` (`MachineConfigurationViewModel.java:410-417`, `:423-444`) precisely because an
earlier version distinguished outcomes by prefix-matching the status message, which
"re-introduced the message-sniffing that `isReject()` exists to avoid and raced with the
`Platform.runLater` that sets the message" (CLAUDE.md).

The activation path must not regress into that. **Reuse the existing `PreSaveOutcome`** — its three
cases already cover this ticket exactly — rather than inventing a parallel scheme or hand-rolling a
boolean plus a message.

Note the refactor this implies, which is the bulk of the diff. The activation save currently
returns `SaveConfigurationActivationApiResult` directly from its `Task` (`:522-537`), with no
pre-save stage to report. It needs the same outcome-wrapper shape the configuration task already
uses (`:239-252`, `:265-278`) so its `setOnSucceeded` can tell "declined" from "saved".

`SaveOutcome` (`:423-444`) is typed to `SaveConfigurationApiResult` and so cannot be reused as-is.
Two options: generify it over the result type, or add a parallel
`SaveActivationOutcome`. **Prefer generifying** — two near-identical wrapper classes in one file is
the duplication that invites them to drift, and the class is 22 lines with no behavior beyond
holding a pair. `PreSaveOutcome` itself is already result-type-agnostic and is reused unchanged.

### D3 — a failed existence check aborts the save, matching the configuration path

The ticket does not address what happens when `getConfigurationActivationById()` returns `isError()`
(service unreachable, backend failure — *not* a rejection), where the client cannot tell whether
the id collides.

**The decision is already made, and this ticket must follow it rather than re-open it.** The
sibling configuration path decided exactly this case at
`MachineConfigurationViewModel.java:366-374`: a genuine failure returns `PreSaveOutcome.CHECK_FAILED`
and the save is **not** attempted, with the message "Save failed: could not check for an existing
configuration: …". A null result is treated the same way (`:355-357`).

Copy that behavior verbatim for activations. The reasoning that justified it there applies
unchanged here: proceeding on an unverifiable check reproduces the silent-replacement bug precisely
when the system is unhealthy, and the whole purpose of the check is that an unconfirmed overwrite
must not go through.

Deviating — e.g. "warn and let the user decide" — was considered and rejected. It would make two
adjacent saves in the same view behave differently for the same class of failure, which is a worse
outcome than either policy on its own. If the abort policy is ever judged too strict, it should
change for both paths in one ticket, not diverge here.

`PreSaveOutcome` already has all three cases (`PROCEED` / `DECLINED` / `CHECK_FAILED`, `:410-417`),
so D2 is satisfied by reusing it rather than extending it.

### T3 — tests

`MachineConfigurationViewModelTest` already exists and exercises the view model without a service
ecosystem, which is why `DpApplication` access goes through injectable seams. Extend it with:

- id blank → **no** server call (a blank id is a server-generate request, not a collision)
- id in session list → server **not** consulted (stage 1 short-circuits; this pins the ordering, so
  a refactor cannot turn every add into a round trip)
- id absent from session, server rejects → save proceeds, no dialog
- id absent from session, server returns a record → dialog raised; decline aborts the save
- id absent from session, server errors → dialog raised with the *uncertainty* wording (D3), and
  the outcome is the typed "skipped" value, not a message match (D2)
- confirmation times out → treated as decline

The existing `AnnotationApiLiveIT` is the right home for one end-to-end case: save an activation
with an explicit id, then `getConfigurationActivationById()` it back and assert the record is found —
pinning the REJECT-vs-found contract against a real server rather than against a stub. It skips
automatically when MongoDB is unreachable, so this costs nothing in CI.

### T4 — remove the now-stale interim text

Two places assert the gap this ticket closes and must be updated in the same PR, or they become
lies:

1. The comment at `MachineConfigurationViewModel.java:498-506` ("AnnotationClient currently exposes
   no wrapper… see the follow-up issue").
2. The Client Activation ID prompt text ("a supplied id replaces any existing activation") — soften
   to reflect that a collision is now detected and confirmed.
3. CLAUDE.md's Machine Configuration section carries the same claim verbatim ("A collision with a
   record this session knows nothing about is **not** detected… until that lands the field's prompt
   text carries the warning"). Update it.

## Out of scope

- **Load-for-edit** for the configuration form. Adjacent but distinct, as the ticket says; it lands
  under #39 task 5.
- The composite-key wrapper arm (see T1).
- Any change to the overlap-rejection behavior, which is correct server behavior and is surfaced
  verbatim today.

## Sequencing

Independent of #39 and of dp-service #244. Can be implemented and merged on its own at any time. If
it lands before #39 task 5, that task inherits the wrapper; if after, no conflict — the two touch
different methods on the same view model.
