# Object Interface List

This file is a quick interface list checked before writing command cards, not the finalized object structure body.

Object field final source of truth is [02 Object Model](02-object-model.md); "why this way" see [decisions.md](decisions.md).

Purpose: before writing a new card check this table, prevent redefining already-grown constructors/methods; if a command card reverse-defines a new interface, append here and mark source card in "Source".

After all command cards complete and methods backfilled into 02, this list's temporary mission ends (merge into 02 or keep).

## Commit

| Kind | Signature | Description | Source |
| --- | --- | --- | --- |
| Field | String message | Commit message text, non-null | 02:19 |
| Field | Date timestamp | Commit time, non-null | 02:20 |
| Field | String firstParent | first parent ID, nullable | 02:21 |
| Field | String secondParent | second parent ID, nullable | 02:22 |
| Field | `Map<String,String> filenameToBlob` | filename → blob ID, non-null (initial empty map) | 02:23 |
| Constructor | `Commit(String message, Date timestamp, String firstParent, String secondParent, Map<String,String> filenameToBlob)` | Five-arg constructor | init card (section 3 step 2) |

## StagingArea

| Kind | Signature | Description | Source |
| --- | --- | --- | --- |
| Field | `Map<String,String> addition` | staged for addition: filename → blob ID | 02:47 |
| Field | `Set<String> removal` | staged for removal: filename | 02:48 |
| Constructor | `StagingArea()` | No-arg constructor, produces empty addition/removal | init card (section 3 step 3) |

## Repository (helper methods)

Extracted per command card reuse; commit card (third card) first forms card. First three (three-hop) are package-private static, no-arg, nested bottom-up, bottom disk read only once.

| Signature | Purpose (what it reads) | Source |
| --- | --- | --- |
| `String getCurrentBranchName()` | Read `.gitlet/HEAD`, get current branch name | commit card section 4 |
| `String getCurrentCommitID()` | Call getCurrentBranchName(), read refs/heads/<branch> single-line pointer, get current commit ID | commit card section 4 |
| `Commit getCurrentCommit()` | Call getCurrentCommitID(), deserialize current commit from objects/commits/ by ID | commit card section 4 |
| `void printCommit(Commit commit, String commitID)` | Print one commit block to stdout in log format (===/commit ID/[merge's `Merge: 4975af1 2c1ead1` line]/Date/message/blank line after block); holds object and its own ID | log card section 3 |
| `String resolveCommitID(String givenID)` | Abbreviated ID resolution: list objects/commits/, match by `givenID.toLowerCase()` prefix, 0 or multiple matches both throw `No commit with that id exists.`, return unique full ID; caller readObject itself | checkout card section 3 usage 2 (reset card shares) |
| `void replaceWorkingDir(Commit currentCommit, Commit targetedCommit)` | Whole working directory replacement: inside read stage, list working dir normal files, first do untracked file blocking check (throw `There is an untracked file in the way; delete it, or add and commit it first.`), then iterate target map write all tracked blobs, delete current tracked but target not; does not handle branch pointer and stage | checkout card section 3 usage 3 (reset card shares) |
| `void makeCommit(String message, String secondParentID)` | Shared commit current stage implementation: blank message/empty stage two checks → copy current map and apply stage → five-arg constructor → sha1 (secondParentID null becomes "") → write object → move current branch pointer → clear stage; method body only in commit card §3 | commit card section 3/4 (merge card shares) |
| `HashMap<String, String> readRemotes()` | Read `.gitlet/remotes`, missing file returns empty map (init does not create) | add-remote card section 3/4 (rm-remote etc. remote cards share) |
| `void copyMissingObjects(String srcGitlet, String dstGitlet, String headID)` | Copy dst-missing commits and blobs from src repo to dst repo (existence pruned) | fetch card section 3/4 (push card shares) |
