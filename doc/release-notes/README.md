# Release Notes

One document per release, named `rel-<version>.md` to match the git tag, starting with 1.16.0.
Releases before that were documented on the
[GitHub release](https://github.com/osprey-dcs/dp-desktop-app/releases) itself; the `rel-*` tags
remain the authority on what any past release contained.

The index, with a one-line summary per release, is the `## Release Notes` section of the top-level
[`README.md`](../../README.md).  Add each new document there.

## Conventions

**Organized by issue ticket, not by PR.**  A ticket often spans several PRs, and the PR boundaries
are an artifact of how the work was split rather than something a reader of the release cares
about.

**A breaking release leads with an "Upgrading from `<previous>`" checklist**, which calls out
silent behavior changes separately from compile errors.  The compile errors are the easy half — the
compiler finds every one of them.  The changes worth the checklist are the ones where nothing
errors and the behavior is simply different.

**Name the upstream ticket when the cause is upstream.**  This app consumes the gRPC API defined in
dp-grpc and implemented in dp-service, and the three repos are tagged in lockstep, so a good share
of any release here exists because that API changed underneath it.  Those sections should point at
the dp-grpc or dp-service ticket rather than restating its reasoning.

**Correct the `README.md` statements a release falsifies, in the same change.**  1.16.0 made two of
them obsolete — that the app ran only in demonstration mode, and that the demo database was reset at
launch — and a release note describing a behavior change beside a README still asserting the old
behavior is worse than either alone.

## Publishing

`release.yml` publishes `doc/release-notes/rel-<version>.md` as the GitHub release body via
`body_path`, and **fails the release job before the build** if the file is not present on the
tagged commit.  This repo builds dp-grpc and dp-service from source before it builds the app, so
leaving that check to the publish step would surface a missing file three builds late.

Write the notes and merge them **before** pushing the `rel-*` tag.

A manual `workflow_dispatch` run defaults to a dry run and only warns about missing notes, since a
rehearsal usually happens before the notes are written.  Both publishing paths — a `rel-*` tag push,
and a dispatch with `dry_run: false` — fail hard.
