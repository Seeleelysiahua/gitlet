# `pull` (bonus)

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void pull(String remoteName, String remoteBranchName)`

## 2. Dependent Data

This card adds no new dependencies: fetch section dependencies see the fetch card, merge section dependencies see the merge card.

## 3. Execution Steps

1. Fetch → `fetch(remoteName, remoteBranchName)`
2. Compose remote tracking branch name → `String trackingBranch = remoteName + "/" + remoteBranchName`
3. Merge fetched branch → `merge(trackingBranch)`

All failure messages are thrown by fetch and merge own check lines along the reporting chain; this card has no check line.

## 4. Reuse and Reused By

- Utils methods called: none (branch name concatenation is a String operation)
- Object interfaces called: none
- Called by: only dispatched by Repository.run; currently no other command reuses it
- Newly extracted helper methods: none. This card is command-level composition of fetch + merge; to support merging a remote tracking branch, the merge card branch resolution has been extended to two directories.

## 5. State Modification and Failure Protection

Boundary: this card has no check and no disk write; checks and writes are entirely inside fetch and merge sections, each section's boundary unchanged.

Changes:

- working directory: changed (merge section)
- staging area: changed (merge section)
- `.gitlet`: changed (fetch section adds missing objects; merge section adds merge commit and conflict blobs)
- branch pointer: changed (fetch section updates refs/remotes/<remote>/<branch>; merge section moves current branch pointer)
- HEAD: no change

Failure protection: fetch section failure means zero change throughout; merge section failure keeps the fetch section's add-only objects and remote tracking pointer, and a fetch rerun is idempotent; merge section atomicity is the same as the merge card.
