# Plan: Sign release artifacts with keyless Sigstore (issue #24)

- **Ticket**: [osprey-dcs/dp-desktop-app#24](https://github.com/osprey-dcs/dp-desktop-app/issues/24)
- **Reference implementations**, both merged and rehearsed:
  - [osprey-dcs/dp-grpc#137](https://github.com/osprey-dcs/dp-grpc/issues/137) — PRs #155
    (workflow) and #156 (`NEXT.md`). Plan:
    [`plan/tickets/137/plan.md`](https://github.com/osprey-dcs/dp-grpc/blob/main/plan/tickets/137/plan.md).
    Decisions cited as **grpc-D*n***.
  - [osprey-dcs/dp-service#221](https://github.com/osprey-dcs/dp-service/issues/221) — PRs #298
    (release page) and #299 (container image). Plan:
    [`plan/tickets/221/plan.md`](https://github.com/osprey-dcs/dp-service/blob/main/plan/tickets/221/plan.md).
    Decisions cited as **svc-D*n***. **This is the closer model**: dp-service's `release.yml` is
    the shape ported here, with the image half dropped.
- **Related**: [#21](https://github.com/osprey-dcs/dp-desktop-app/issues/21) / PR #22 — SHA-pinned
  actions, and the `workflow_dispatch` + `dry_run` path this plan retires (D2).
- **Status**: triaged and planned 2026-09-28 against `main` at `7b1a378`; Dependabot #41 merged
  2026-09-30 (`c29b58e`), clearing the one prerequisite. Tasks 1–5 implemented and rehearsed
  2026-09-30 in PR #50 (see **Rehearsal results**); Task 6 waits on the next release.

## Overview

The dp-desktop-app release page carries `dp-desktop-app-<version>.jar` and a `.sha256` written by
the same job, with the same token, to the same page. The checksum proves integrity, not origin.

This ticket delivers:

1. **A signed release page.** One `SHA256SUMS` signed with `cosign sign-blob`, replacing the
   per-jar `.sha256`. The signature binds the jar to the repository, workflow file, tag, and
   source commit.
2. **A release workflow that cannot publish from anything but a `rel-*` tag push**, so every
   published release carries a signature its own published verify command accepts.
3. **Verification instructions** in `README.env` and in the release notes (which are the release
   body), written for the people who actually download this jar — desktop users on macOS and
   Windows as well as Linux.

The ticket's own argument for why this repo matters most holds: dp-grpc and dp-service jars feed
other builds, but this one is downloaded from the release page and run directly by a person.

## Triage findings

Each premise was checked on 2026-09-28 against `main` (`7b1a378`) and the published `rel-1.16.0`.

### What holds

| Ticket claim | Verified |
|---|---|
| `release.yml` is one job with `contents: write` throughout | Yes: workflow-level `permissions: contents: write`, single `release` job |
| One jar plus one `.sha256` is published | Yes: `rel-1.16.0` has exactly `dp-desktop-app-1.16.0.jar` and `dp-desktop-app-1.16.0.jar.sha256` |
| The job runs `mvn install` of dp-grpc and dp-service from source | Yes: "Build and install dp-grpc" / "Build and install dp-service" |
| Nothing is published to a Maven repository | Yes: `pom.xml` has no `distributionManagement`, `maven-deploy-plugin`, or `maven-gpg-plugin` |
| `sigstore/cosign-installer@6f9f177…` is v4.1.2 | Yes: tag `v4.1.2` → `6f9f17788090df1f26f669e9d70d6ae9567deba6` (a commit, lightweight tag). Still the latest release |
| A `workflow_dispatch` with a `dry_run` input exists (#22) | Yes, and it is the source of findings 3 and 4 |
| Keep verification out of a generated release body (the settled grpc-D9) | Adopted unchanged; see D8 |

### 1. The target version is stale

The ticket targets `rel-1.16.0`, which shipped unsigned on 2026-09-16. As in both siblings
(grpc-D6), this targets the next release. Do not re-cut 1.16.0.

### 2. The published checksum is broken, as it was in both siblings

`release.yml` runs `sha256sum release/dp-desktop-app-${VERSION}.jar` from the repository root. The
published `rel-1.16.0` checksum is:

```
37420542…be36d  release/dp-desktop-app-1.16.0.jar
```

Downloaded into one directory, `sha256sum -c` fails with "No such file or directory" — the exact
command `README.env` documents. Generate from inside `release/`, as both siblings now do.

### 3. The ticket's two-job sketch keeps the exposure it names

The ticket argues for the split because the job runs "the `mvn install` builds of dp-grpc and
dp-service, which execute code from two other repositories" — and then its sketch puts those builds
in the job that holds `id-token: write`. That is exactly svc-D1's finding: a job with
`id-token: write` exposes `ACTIONS_ID_TOKEN_REQUEST_URL` / `_TOKEN` to every step, so any Maven
plugin in any of the three builds could mint a certificate as this workflow and sign bytes of its
choosing. The fix is dp-service's three-job shape (D1).

### 4. A publishing dispatch cannot coexist with signing

#22's `dry_run: false` dispatch publishes a release from whatever ref it was dispatched on. After
this change such a release would be signed under `release.yml@refs/heads/<branch>` with trigger
`workflow_dispatch`, and the published verify command (exact tag identity plus
`--certificate-github-workflow-trigger push`, per svc-D5) **rejects it**. That is a release that
fails its own documented verification — or, if the command were loosened to accept it, a verify
command that proves nothing. The ticket's suggestion to "gate the publish job" on `dry_run` does not
resolve this; it keeps the path.

The path has never been used. The workflow's run history has exactly one dispatch — #22's own
rehearsal on 2026-08-14 — and every release was a tag push. D2 retires it.

### 5. `rel-1.16.0` permanently carries that publishing dispatch

A dispatch runs the workflow file **as it exists at the target ref** (settled by dp-service's
rehearsal, run `36067531985`; dp-grpc's contrary workflow comment is wrong). `rel-1.16.0` is the only
tag whose `release.yml` has the dispatch (`rel-1.15.0` and earlier do not). So after this ticket,

```
gh workflow run release.yml --ref rel-1.16.0 -f version=<v> -f dry_run=false
```

still runs the **old** single-job workflow: it rebuilds unsigned and publishes to `rel-<v>` with
`overwrite_files: true`. With `<v>` naming a signed release, it would replace that release's jar
with an unsigned rebuild. The file at the tag cannot be changed short of deleting the tag. What
signing changes is that the damage becomes **detectable**: the replaced jar no longer matches the
signed `SHA256SUMS`, so `sha256sum -c` fails for every consumer.

The path stays open only while a workflow named `release.yml` with a `workflow_dispatch` trigger
exists on the default branch, which is what makes a dispatch possible at all. *Rejected:* renaming
the new workflow and removing `release.yml` from `main`, which would close it. The rename would
break Task 5's pre-merge rehearsal, which dispatches the PR branch's copy and works only because
`release.yml` already exists on `main`; a new filename cannot be dispatched until after it merges.
It would also move this repo's certificate identity off the `release.yml@…` shape both siblings
publish. Against that, the hazard needs write access, an explicit `dry_run=false` (the old input
defaults to `true`), and a version naming a signed release, and signing makes its damage
detectable.

It is not a privilege escalation (dispatch needs write access, which can push tags anyway); it is an
accident hazard. It is documented in CLAUDE.md with the rule "never dispatch `release.yml` against a
tag" (Task 4), exactly as dp-service documents its permanent "never push a tag to rehearse".
GitHub's immutable releases would be a stronger guard. They are left out of scope because their
interaction with `action-gh-release`'s upload-after-create and `overwrite_files` has not been
checked.

### 6. Nothing checks the tag against the POM

`release.yml` renames whatever `target/dp-desktop-app-*-shaded.jar` exists to the tag's version, and
checks out the siblings at `rel-<tag version>` while Maven resolves `dp-grpc.version` /
`dp-service.version` from the POM. A tag pushed on an unbumped POM would publish — and after this
ticket, **sign** — a jar whose name, self-reported version, and dependencies disagree. dp-service
added the check in #298's review; this plan takes it from the start (D5).

### Other facts the design depends on

- **Rehearsing against a PR branch works.** The dispatch runs the branch's copy (finding 5), and
  `release.yml` already exists with a `workflow_dispatch` trigger on the default branch.
- **The build runs no tests** (`-DskipTests` on all three builds); `ci.yml` does. That is unchanged
  and unaffected here.
- **`ci.yml` is unaffected.** It neither signs nor publishes.
- **Existing action pins now match the siblings'.** At triage they were older (`checkout` v4.4.0,
  `action-gh-release` v2.6.2). Dependabot PR
  [#41](https://github.com/osprey-dcs/dp-desktop-app/pull/41) bumped them and merged 2026-09-30
  (`c29b58e`), ahead of Task 1 so the rewrite starts from these pins rather than conflicting with
  them: `checkout` v7.0.1 and `action-gh-release` v3.0.3, as in dp-service, and `setup-java`
  v6.0.1, one patch ahead of dp-service's v6.0.0. No major bump is mixed into this change. New
  actions use the siblings' pins, re-verified 2026-09-28:
  `upload-artifact` v7.0.1 → `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a`, `download-artifact`
  v8.0.1 → `3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c`, both the latest release.

## Design decisions

Carried over unchanged, reasoning in the sibling plans: Sigstore rather than GPG (grpc-D1),
`cosign` rather than the Python action (grpc-D2), one signed `SHA256SUMS` (grpc-D3), the asset
rename called out in the notes (grpc-D7), notes as the release body with verification in
`README.env` (grpc-D9), and the exact-identity-plus-trigger verify command (svc-D5 amendment). The
decisions below are the ones this repo has to make for itself.

### D1 — Three jobs: `build`, `sign`, `publish` (svc-D1)

| Job | Permissions | Runs |
|---|---|---|
| `build` | `contents: read` | checkout, derive version, notes check, resolve + build dp-grpc and dp-service, build shaded jar, stage notes, upload `build-outputs` |
| `sign` | `contents: read`, `id-token: write` | download, `SHA256SUMS`, cosign, upload `signatures`. **No checkout, no project code** |
| `publish` | `contents: write` | download both, `action-gh-release`. Release-only gate |

The limit, stated as dp-service states it: code running in `build` can still tamper with the jar
before `sign` checksums it. The split confines the signing identity to what `build` handed over,
for seconds rather than the whole run. Workflow-level `permissions` drops from `contents: write` to
`contents: read`; each job widens only what it needs.

*Rejected:* the ticket's two-job sketch (finding 3).

### D2 — Publish only on a `rel-*` tag push; retire `dry_run` and the publishing dispatch

The `publish` job's `if:` is the event expression (D3), so a `workflow_dispatch` is structurally
incapable of publishing, as in both siblings. The `dry_run` input, the `DRY_RUN` env, and the
"Report what a real run would publish" step go away: every dispatch is now a rehearsal that
builds and signs, and the skipped `publish` job is the report.

This removes a capability #22 added deliberately, so the reasons are worth stating: it cannot be
signed in a way the published command accepts (finding 4), it has never been used, and "re-publish
a release without a tag push" is better served by re-running the failed jobs of the tag's own run —
which dp-service showed works (a failed `sign` re-run reused `build`'s artifact without rebuilding).

`tag_name: rel-${VERSION}` stays explicit on `action-gh-release`. On a tag push it equals the
default, so it is now belt-and-braces rather than load-bearing; its comment is updated to say so.

### D3 — `IS_RELEASE` from the event, repeated literally in `publish.if` (svc-D3)

```
github.event_name == 'push' && startsWith(github.ref, 'refs/tags/rel-')
```

Defined once as `build`'s job env, and repeated textually in `publish.if` (a job `if:` cannot read
`env`), with a comment at each copy naming the other. It is not passed as a `build` output: the job
that runs other repositories' code must not decide whether publishing happens.

### D4 — A rehearsal's version comes from the POM; siblings resolve all-or-nothing

On a release, `VERSION` is the tag minus `rel-`. On a rehearsal it is `project.version` — replacing
the `version` input, which let a rehearsal build a filename and a sibling combination the POM does
not describe.

Sibling refs:

- **Release:** strictly `rel-${VERSION}` for both dp-grpc and dp-service. Each is checked up front
  with `git ls-remote --exit-code`, distinguishing exit 2 ("no such tag": fail, naming the
  dependency order — dp-grpc, then dp-service, then dp-desktop-app) from any other failure ("could
  not reach", never reported as a missing tag). This is dp-service's #250 check, taken here because
  this workflow depends on **two** siblings being tagged first, and today a missing one surfaces
  only as a checkout failure.
- **Rehearsal:** an optional `sibling_ref` input, applied to both siblings, must exist if given
  (typically `main`, to rehearse against unreleased sibling APIs mid-cycle). Otherwise
  `rel-<dp-grpc.version>` and `rel-<dp-service.version>` from the POM **if both exist**, else `main`
  for both. Always logged.

*All-or-nothing, rather than dp-service's per-sibling fallback,* because this repo has two
siblings: falling back independently can pair a tagged dp-grpc with dp-service `main` — a
combination no release will ever build, and the one most likely to fail for reasons unrelated to
the change being rehearsed.

`sibling_ref` reaches the resolver script through `env:`, never `${{ }}`-spliced into a `run:`.

### D5 — On a release, the tag must match the POM (finding 6)

In "Derive version": the tag's version must equal `project.version`, `dp-grpc.version`, and
`dp-service.version` (read with `mvn -B -q -DforceStdout help:evaluate`), or the run fails before
any build with "bump the POM and retag". This also closes the follow-on the dp-grpc plan left
open ("Tag and version validation") for this repo. The version must further match
`^[0-9][0-9A-Za-z.+-]*$`, since it becomes a filename and crosses into `sign` and `publish`, which
read it through `env:` only. The leading digit rules out a value starting with `-` being read as
an option.

### D6 — A dispatch against a tag ref is refused (svc-D5 amendment)

Its certificate would be `release.yml@refs/tags/rel-<version>` — the release's own identity,
distinguishable only by the workflow trigger. "Derive version" fails such a run with "dispatch
against a branch". The published command's `--certificate-github-workflow-trigger push` makes a
verifier independent of this refusal; the refusal is defense in depth. Note it protects only tags
cut **after** this change (finding 5).

### D7 — One verification identity, and the cross-repo copy-paste hazard

```
https://github.com/osprey-dcs/dp-desktop-app/.github/workflows/release.yml@refs/tags/rel-<version>
```

This repo has one signed artifact and one workflow, so none of dp-service's two-identity hazard
applies inside it. The hazard the ticket's comment warns about is **across** repos: this
`README.env` is a near-copy of two others whose identities name a different repository. The
rehearsal (Task 5) checks the dp-service identity **fails** against this repo's bundle, so a
copy-paste slip is caught against a real signature rather than by reading.

### D8 — Release-note content goes in `NEXT.md` (svc-D7)

Adopt the version-less `doc/release-notes/NEXT.md` both siblings use: sections accumulate as
tickets land, and the file is renamed at cut time. Seed it with #24's section and a "Cutting the
release" checklist adapted from dp-service's.

This repo has one extra rule to reconcile: `doc/release-notes/README.md` requires cross-file links
**pinned to the release tag**, which `NEXT.md` cannot name. So `NEXT.md` links to `blob/main/…`, and
the cut checklist carries dp-grpc's step "repoint `blob/main/...` links to `blob/rel-<version>/...`".

### D9 — Verification instructions cover the desktop platforms

This is the one repo of the three whose jar is typically run on a workstation rather than a Linux
server. `README.env` and `NEXT.md` therefore give, alongside `sha256sum -c SHA256SUMS`:

- **macOS:** `shasum -a 256 -c SHA256SUMS`
- **Windows (PowerShell):** there is no built-in `-c` equivalent, so the command compares the hash
  itself and prints `True` or `False`, rather than leaving a 64-character comparison to the eye:

  ```powershell
  $f='dp-desktop-app-<version>.jar'; (Get-FileHash -Algorithm SHA256 $f).Hash -eq ((Select-String -Path SHA256SUMS -SimpleMatch $f).Line -split '\s+')[0]
  ```

  `-eq` is case-insensitive, so `Get-FileHash`'s uppercase hash matches `SHA256SUMS`'s lowercase
  one. Task 5 runs this command, and `NEXT.md` says that anything other than `True` means the jar
  must not be run.

`cosign` ships binaries for all three, and `verify-blob` is the same command everywhere; the only
Windows difference is line continuation, so the command is also given on one line.

### D10 — cosign v3.1.3, verified with v3.1.3 or later, with one retry

`cosign-release: 'v3.1.3'` on the installer. The siblings pin v3.0.6, which is affected by
[GHSA-fx35-mq7g-6g98](https://github.com/sigstore/cosign/security/advisories/GHSA-fx35-mq7g-6g98)
(high; all versions up to v3.1.2, fixed in v3.1.3 on 2026-08-06). A substituted legacy bundle
can embed a public key that makes `verify-blob` skip the certificate identity and issuer checks.
Replacing the jar, `SHA256SUMS` and the bundle together is exactly the attacker this ticket
defends against, so a verifier with the flaw would make the signature worthless.

The flaw is in **verification**, so what protects users is the version the docs name. `README.env`
and `NEXT.md` therefore say "verify with cosign **v3.1.3 or later**" as a minimum, not "the
signatures are produced with vX", which reads as an instruction to install that version. The
installer is pinned to the same version because Task 5 verifies with it too.

The decision to match the siblings' version is dropped. v3.1.3 already verified these signatures
locally in dp-service's rehearsal, so the move carries no known compatibility cost, and a matching
version is not worth steering users to a vulnerable verifier. dp-grpc and dp-service need the same
change (dp-service's `README.env` currently says "produced with cosign v3.0.6"). That belongs in
follow-up tickets in those repos, not yet filed, rather than here.

`sign-blob` retries once after 30 s, per dp-service's rehearsal losing a signing step to a
connection reset at Sigstore's timestamp authority.

## Target workflow shape

```yaml
on:
  push:
    tags: ['rel-*']
  workflow_dispatch:          # rehearsal; cannot publish (see publish.if)
    inputs:
      sibling_ref:
        description: "Optional dp-grpc/dp-service ref for both siblings (e.g. main). Default: the POM's rel- tags, else main."
        required: false
        default: ''

concurrency:
  group: release-${{ github.ref }}
  cancel-in-progress: false

permissions:
  contents: read

jobs:
  build:                      # contents: read
    outputs: { version }
    env: { IS_RELEASE: <D3> }
    # checkout; setup-java
    # Derive version: tag + POM check on release (D5); refuse tag-ref dispatch (D6); POM on rehearsal (D4)
    # Verify release notes exist: if IS_RELEASE (path from VERSION, equivalent to the tag here)
    # Resolve sibling refs (D4)
    # checkout + install dp-grpc; checkout + install dp-service; build shaded jar
    # Prepare + verify release/dp-desktop-app-${VERSION}.jar
    # Stage notes: real file on release, placeholder on rehearsal
    # upload-artifact build-outputs: jar + RELEASE_NOTES.md; if-no-files-found: error

  sign:                       # contents: read, id-token: write
    needs: build
    env: { VERSION: needs.build.outputs.version }
    # download build-outputs -> release/   (NO checkout)
    # SHA256SUMS, working-directory: release (finding 2)
    # cosign-installer (cosign-release v3.1.3); sign-blob with one retry (D10)
    # upload-artifact signatures: SHA256SUMS + SHA256SUMS.cosign.bundle

  publish:                    # contents: write
    needs: [build, sign]
    if: github.event_name == 'push' && startsWith(github.ref, 'refs/tags/rel-')   # = IS_RELEASE
    env: { VERSION: needs.build.outputs.version }   # GITHUB_ENV does not cross jobs; bind it, as sign does
    # download both -> release/
    # action-gh-release: tag_name rel-${VERSION}; jar, SHA256SUMS, bundle;
    #   body_path release/RELEASE_NOTES.md; fail_on_unmatched_files: true; overwrite_files: true
    #   (with dp-service's comment: overwrite does not delete a stale .sha256 on a re-cut)
```

Published assets become:

```
dp-desktop-app-<version>.jar
SHA256SUMS
SHA256SUMS.cosign.bundle
```

## Blast radius

**Files that change:**

- `.github/workflows/release.yml` — rewritten to the shape above.
- `README.env` — asset list and step 2 are wrong after this change (`dp-desktop-app-<version>.jar.sha256`
  no longer exists). Port dp-service's jar section (not its image section), with this repo's
  identity and D9's platform variants.
- `doc/release-notes/NEXT.md` — new (D8).
- `doc/release-notes/README.md` — its "Publishing" section describes the dry-run warning and "both
  publishing paths"; both become false. Rewrite it, and add the `NEXT.md` convention.
- `CLAUDE.md` "Releases" — the paragraphs "The notes path is derived from `VERSION`…" (its reason,
  the dispatch, still applies but no longer publishes) and "A dry run warns rather than failing…"
  become wrong. Replace with the signing paragraph, D1–D6, the `NEXT.md` convention, and finding 5.

**Not changed:** `doc/release-notes/rel-1.16.0.md` describes the dry-run path as shipped in 1.16.0,
which remains true of that release; published notes are not rewritten. `README.md` gets its
`## Release Notes` row at cut time, not now. `ci.yml` is unaffected.

**Outside this repo:** nothing downloads this repo's release assets programmatically that we
control; the Data Platform installer bundling is the one consumer worth a heads-up about the asset
rename (NEXT.md's upgrade item).

**Rekor is public and append-only.** Every rehearsal signs for real and leaves a permanent entry
naming its branch. Harmless for a public repo; choose rehearsal branch names accordingly.

## Implementation tasks

One PR — there is no image half.

**Task 1 — `release.yml`.** Rewrite to the target shape. Carry the existing sibling-checkout and
jar-location steps across unchanged in content, except: version derivation (D4–D6), the notes
check (release-only), sibling resolution (D4), and checksum generation from inside `release/`.
`set -euo pipefail` on every multi-line `run`. Port dp-service's workflow comments where the
reasoning carries over (rehearsal trigger, target-ref copy, `IS_RELEASE`, the job-split rationale,
`working-directory`, `fail_on_unmatched_files`, `overwrite_files`, the retry), adjusted for two
siblings and no image.

**Task 2 — `README.env`.** Replace "Release Contents" and step 2 with dp-service's jar text: what
`SHA256SUMS` covers and does not, the cosign pointer with the v3.1.3 minimum (D10), the exact-identity verify command
with this repo's identity, and the "keep every flag as written" explanation. Add D9's platform
variants. Add a line saying releases through 1.16.0 shipped an unsigned `.sha256` with a
`release/`-prefixed path.

**Task 3 — `NEXT.md`** (D8). Seed with #24's section: why, the three-job split, the asset rename
(scripted downloads of `.sha256` will 404 — flag it for the installer), the checksum-path fix, the
verification commands in full with D9's variants, and one line that `release.yml` no longer
publishes from a manual dispatch. Cutting checklist: rename, retitle, "Upgrading from"
section, repoint `blob/main` links to the tag, delete the checklist, add the `README.md` row,
start a fresh `NEXT.md`. No version number anywhere.

**Task 4 — Docs.** `CLAUDE.md` "Releases" and `doc/release-notes/README.md` "Publishing", per Blast
radius. Record in CLAUDE.md, as permanent rules: rehearse with `gh workflow run release.yml --ref
<branch>`, never by pushing a tag; and never dispatch `release.yml` against a tag — against
`rel-1.16.0` that still runs the old publishing workflow (finding 5).

**Task 5 — Rehearse** with `gh workflow run release.yml --ref <PR branch>`. Confirm:

- all three jobs run and `publish` is **skipped**; `sign` has no checkout and no `mvn`
- `VERSION` is the POM's `1.16.0`, and the log names the sibling refs (`rel-1.16.0` for both)
- the downloaded `SHA256SUMS` has a bare filename, and `sha256sum -c` / `shasum -a 256 -c` pass in a
  flat directory; D9's PowerShell command prints `True`, and `False` against a modified jar
- every local `cosign verify-blob` below is run with v3.1.3 or later (D10)
- `cosign verify-blob` **passes** with the exact `release.yml@refs/heads/<branch>` identity
- it **fails** with the published `@refs/tags/rel-1.16.0` identity, with
  `--certificate-github-workflow-trigger push`, and with the **dp-service** identity (D7)

Then a second dispatch with `sibling_ref=main`, confirming both siblings resolve to `main` and the
build passes; and a dispatch with `sibling_ref=no-such-ref`, confirming it fails at resolution
rather than at checkout. The tag-ref refusal (D6) cannot be rehearsed — every existing tag carries
an older file — so it rests on the expression, as dp-service's did.

Record run IDs and results in this plan, as the sibling plans do.

**Task 6 — Close out.** After the next release is cut, verify the published jar end to end from the
release page as a consumer would, on macOS or Windows as well as Linux, before announcing it.

## Rehearsal results (Task 5, 2026-09-30)

All against branch `issue-24-sigstore` at `4a23465`; local checks with cosign v3.1.3.

| Run | Dispatch | Result |
|---|---|---|
| [36769998025](https://github.com/osprey-dcs/dp-desktop-app/actions/runs/36769998025) | no inputs | `build` ✅ `sign` ✅ `publish` skipped. `VERSION` 1.16.0 from the POM; "Sibling refs: dp-grpc rel-1.16.0, dp-service rel-1.16.0 (rehearsal: POM versions)" |
| [36770330908](https://github.com/osprey-dcs/dp-desktop-app/actions/runs/36770330908) | `sibling_ref=main` | `build` ✅ `sign` ✅ `publish` skipped. "Sibling refs: main for both (rehearsal: sibling_ref input)" |
| [36770597532](https://github.com/osprey-dcs/dp-desktop-app/actions/runs/36770597532) | `sibling_ref=no-such-ref` | `build` ❌ at **Resolve sibling refs** ("sibling_ref 'no-such-ref' does not exist in osprey-dcs/dp-grpc"), before any checkout; `sign` and `publish` skipped |

`sign`'s steps are exactly download, `SHA256SUMS`, install cosign, sign, upload: no checkout and no
`mvn`. The installer log confirms the **v3.1.3** binary signed. It bootstraps with v3.0.6 only to
check the v3.1.3 download against cosign's published release key, which is key-based
verification, not the keyless identity check GHSA-fx35-mq7g-6g98 concerns.

Run 36769998025's artifacts, downloaded into one flat directory:

| Check | Expected | Result |
|---|---|---|
| `SHA256SUMS` content | bare filename | `13131f93…c851  dp-desktop-app-1.16.0.jar` |
| `sha256sum -c` / `shasum -a 256 -c` | OK | OK / OK |
| `shasum -a 256 -c` after appending a byte to the jar | FAILED | FAILED |
| `verify-blob`, identity `release.yml@refs/heads/issue-24-sigstore` | pass | Verified OK |
| same, plus `--certificate-github-workflow-trigger workflow_dispatch` | pass | Verified OK |
| same, plus `--certificate-github-workflow-trigger push` | fail | `expected GithubWorkflowTrigger to be "push", got "workflow_dispatch"` |
| identity `…@refs/tags/rel-1.16.0` (the published form) | fail | `no matching CertificateIdentity` |
| **dp-service** identity (D7) | fail | `no matching CertificateIdentity` |
| one hex digit of `SHA256SUMS` altered | fail | `invalid signature` |

**Not rehearsed:**

- **D9's PowerShell one-liner.** There was no PowerShell on the rehearsal machine, so the command
  is unexercised. Run it on Windows (expect `True`, then `False` after altering the jar) before the
  PR leaves draft or, at the latest, in Task 6.
- **The release-only paths** (tag/POM check, strict sibling tags, notes check, `publish`) and
  **D6's tag-ref refusal.** Every existing tag carries an older workflow file, so these rest on
  their expressions until the next real release, as dp-service's did. Task 6 is their first run.

## Out of scope

- **Maven signing / a Maven repository, and the distribution-model question.** grpc-D1; the ticket
  says so itself.
- **Bumping existing action pins.** Dependabot's job (#41); see "Other facts".
- **Signing or notarizing for the OS** (macOS Gatekeeper, Windows Authenticode). A different
  problem — the OS trusting a launched binary rather than a user verifying a download — and not
  applicable to a jar run with `java -jar`. Worth its own ticket only if the app ships native
  packages (jpackage).
- **Running tests in the release build.** `ci.yml` covers them; unchanged.
- **Making the release jar reproducible** from the signed source commit. The signature names the
  commit; nothing yet lets a third party rebuild and compare.

## Dependencies and sequencing

- **Depends on nothing unmerged upstream.** Both siblings are merged and rehearsed; this repo
  builds them from source and never consumes their signed assets.
- **Dependabot #41 is merged** (2026-09-30), as this plan required before Task 1: it edited every
  `uses:` line in the `release.yml` that Task 1 rewrites. Its `release.yml` pins have not run yet —
  no release or dispatch since — so Task 5's rehearsal is also their first exercise.
- **Required before the next `rel-*` tag** for that release to ship signed. If it misses, the next
  release ships unsigned as 1.16.0 did, and `NEXT.md` must not claim otherwise.
- **The next release's tagging order is unchanged**: dp-grpc, then dp-service, then this repo —
  now enforced up front by D4's existence check rather than discovered at a checkout.
