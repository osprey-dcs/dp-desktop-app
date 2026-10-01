# Release Notes — next release (unreleased)

**This is the working draft for the next release. It is not a release note yet.**

Sections accumulate here as tickets land, so the content is written while it is fresh and gets
reviewed in the PR that causes it. At release time this file is renamed to
`doc/release-notes/rel-<version>.md` and finished — see **Cutting the release** at the bottom.

**The version of the upcoming release is deliberately not named anywhere in this file**, in its
filename or in its prose. `release.yml` resolves the notes path from the tag
(`doc/release-notes/rel-<version>.md`), so a file committed under a guessed version is both
stranded and a failed release-notes check on the tag that does ship. Past versions are named
freely where they are the point — "published through 1.16.0" is a durable fact about what shipped,
not a guess about what is about to.

Nothing here should assert what *else* the release contains, either: that is knowable only once
the release is cut, and a stale claim in a file that already looks finished is not something the
person cutting the release has any reason to re-read.

**Cross-file links here point at `blob/main/…`**, because the tag they should be pinned to does not
exist yet. Repointing them is a step in **Cutting the release**. Never make one relative, or pin it
to a `rel-*` tag, which would guess the version. CI runs `.github/scripts/check-release-notes.py`
over this file, which also confirms that each link into this repo names a file and heading that
exist, so a PR renaming a heading linked from here fails until the link is fixed.

## Contents

- [Signed release artifacts (Issue #24)](#signed-release-artifacts-issue-24)
- [Cutting the release](#cutting-the-release)

---

## Signed release artifacts (Issue #24)

The checksum published with previous releases established integrity but not origin. It was
written by the same job, with the same token, to the same release page as the jar it described —
so anyone able to replace the jar could replace the checksum sitting next to it. That matters more
for this repository than for dp-grpc or dp-service: this jar is downloaded from the release page
and run directly by a person, on their own machine.

This release adds a keyless Sigstore signature over `SHA256SUMS`, which binds the jar to the
repository, workflow file, tag, and source commit that produced it. There is no key to distribute,
rotate, or leak: the signing identity is a short-lived certificate issued to the GitHub Actions
run itself and recorded in the public Rekor transparency log.

The release workflow is now split into three jobs. `build` builds dp-grpc, dp-service and this app
with read-only permissions. `sign` holds the OIDC signing token, but checks out no code and runs
nothing from any repository: it checksums and signs only what `build` handed over. `publish` can
write to the release but holds no signing token. Before anything is uploaded, `sign` verifies the
new signature with the same `cosign verify-blob` command given below, so a release whose signature
the documented command would reject fails instead of publishing.

**The release workflow no longer publishes from a manual dispatch.** Publishing is gated on a
`rel-*` tag push; a `workflow_dispatch` run is a rehearsal that builds and signs but cannot publish.
The `version` and `dry_run` inputs added in 1.16.0 are gone: a rehearsal takes its version from the
POM, and an optional `sibling_ref` input selects the dp-grpc and dp-service branch or tag to build
against.

**The asset names change.** A scripted download of `dp-desktop-app-<version>.jar.sha256` will get a
404 against this release. Published assets are now:

```
dp-desktop-app-<version>.jar
SHA256SUMS
SHA256SUMS.cosign.bundle
```

### Verifying the jar

Download the three assets into a single directory with no subdirectories, then check the
checksum:

```bash
sha256sum -c SHA256SUMS          # Linux
shasum -a 256 -c SHA256SUMS      # macOS
```

Expect `dp-desktop-app-<version>.jar: OK`. On Windows, PowerShell has no equivalent of `-c`, so this
compares the hash itself and prints `True` or `False`:

```powershell
$f='dp-desktop-app-<version>.jar'; (Get-FileHash -Algorithm SHA256 $f).Hash -eq ((Select-String -Path SHA256SUMS -SimpleMatch $f).Line -split '\s+')[0]
```

Anything other than `True` means the jar does not match: do not run it.

`SHA256SUMS` does not list itself or `SHA256SUMS.cosign.bundle`, so the checksum says nothing about
either. What protects them is the signature: `cosign verify-blob` below checks the bundle against
`SHA256SUMS`, and the checksum in turn covers the jar.

To verify the signature, install [cosign](https://docs.sigstore.dev/cosign/system_config/installation/)
**v3.1.3 or later** — earlier versions have a verification flaw
([GHSA-fx35-mq7g-6g98](https://github.com/sigstore/cosign/security/advisories/GHSA-fx35-mq7g-6g98))
that lets a substituted bundle skip the identity checks. Then run, with `<version>` replaced by the
release you downloaded:

```bash
cosign verify-blob \
  --bundle SHA256SUMS.cosign.bundle \
  --certificate-identity 'https://github.com/osprey-dcs/dp-desktop-app/.github/workflows/release.yml@refs/tags/rel-<version>' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  --certificate-github-workflow-trigger push \
  SHA256SUMS
```

On Windows, where `\` does not continue a line, the same command on one line:

```powershell
cosign verify-blob --bundle SHA256SUMS.cosign.bundle --certificate-identity "https://github.com/osprey-dcs/dp-desktop-app/.github/workflows/release.yml@refs/tags/rel-<version>" --certificate-oidc-issuer https://token.actions.githubusercontent.com --certificate-github-workflow-trigger push SHA256SUMS
```

Expect `Verified OK`.

Keep every identity flag exactly as written:

- `--certificate-identity` is an exact match on this repository, the `release.yml` workflow file,
  and the one tag being verified. A pattern accepting any `rel-` tag would also accept an older
  release's genuine `SHA256SUMS` and bundle substituted for this one's. A loosened or unanchored
  pattern would accept a valid signature made by any workflow in any repository — the usual way
  this check ends up passing while proving nothing.
- `--certificate-github-workflow-trigger push` requires the signature to come from the tag push
  that published the release. A manual run of the same workflow against the tag would carry the
  same identity; the release workflow refuses such runs, and this flag is what makes a verifier
  independent of that.

dp-grpc and dp-service publish near-identical instructions whose identity names their own
repository; a command copied from either fails against this release's bundle.

Full instructions are in
[`README.env`](https://github.com/osprey-dcs/dp-desktop-app/blob/main/README.env).

### Checksum path fixed

The `.sha256` files published through 1.16.0 recorded the jar's path as
`release/dp-desktop-app-<version>.jar`, because the workflow generated them from the repository
root. A consumer who downloaded the jar and its checksum into one directory and ran `sha256sum -c`
— the command the README documented — got:

```
sha256sum: release/dp-desktop-app-<version>.jar: No such file or directory
```

unless they first recreated a `release/` subdirectory. `SHA256SUMS` is generated from inside the
artifact directory and records bare filenames, so it verifies where the files actually land.

### Upgrade items

1. **Update any scripted download of the `.sha256` file**, including the Data Platform installer's
   bundling if it fetches one. `dp-desktop-app-<version>.jar.sha256` no longer exists;
   `SHA256SUMS` replaces it.
2. **Drop any workaround for the checksum path.** If a script recreated a `release/` subdirectory
   to make `sha256sum -c` succeed, remove it.
3. **Optionally, start verifying the signature.** It is a new capability, not a new requirement.

---

## Cutting the release

When the version is known and the release is being cut:

1. **`git mv doc/release-notes/NEXT.md doc/release-notes/rel-<version>.md`.** The filename must
   match the tag exactly; `release.yml` fails the run before the build if it does not.
2. **Retitle** the H1 to `# dp-desktop-app <version> Release Notes` and replace this file's
   preamble with a "Changes since rel-<previous>" summary, naming the dp-grpc and dp-service
   releases it builds against — written now, when the full contents of the release are actually
   known.
3. **Add the "Upgrading from &lt;previous&gt;" section** as the first section after Contents,
   folding in the per-ticket upgrade items above. Call out silent behavior changes separately from
   compile errors, per this directory's
   [`README.md`](https://github.com/osprey-dcs/dp-desktop-app/blob/main/doc/release-notes/README.md).
4. **Repoint every `blob/main/...` link to `blob/rel-<version>/...`**, including links into the
   other four lockstep repos (dp-grpc, dp-service, dp-python-lib, data-platform), and **replace
   `rel-<version>`** in the verify commands' `--certificate-identity` with this release's tag. This
   file is published as the release body via `body_path`, where a relative link 404s and a `main`
   link drifts as the repo moves on. Don't hunt for them by eye: step 6 lists every one you missed.
5. **Delete this "Cutting the release" section** and update Contents.
6. **Run `python3 .github/scripts/check-release-notes.py`** and fix everything it lists; CI runs it
   on the PR too, and `release.yml` again on the tag. For the new file it fails on a relative link;
   a link into any of the five lockstep repos not pinned to `rel-<version>`, including a stale tag
   copied from older notes; a path or `#anchor` into this repo missing from the tree being tagged,
   or pointing at a duplicated heading; a leftover `rel-<version>` or `<previous>`; and a
   `--certificate-identity` that is not exactly this repo's `release.yml@refs/tags/rel-<version>`.
   The rules are in the script's docstring (osprey-dcs/data-platform#98).
7. **Add the row to `README.md`'s `## Release Notes` table**, newest first, with a one-line
   summary and **Breaking.** if it is.
8. **Decide whether the release is breaking** and say so in the opening if it is. #24 renames the
   published release assets: that breaks scripted downloads even in a release with no API change.
9. **Merge the notes before pushing the tag.** `release.yml` reads them from the tagged commit.
   Push tags in dependency order — dp-grpc, then dp-service, then this repo; `release.yml` fails
   before any build if either sibling tag is missing. An error "Could not query … for" is a network
   failure, not a missing tag — re-run the failed jobs rather than retagging.
10. **Start a fresh `NEXT.md`** for the following cycle.
