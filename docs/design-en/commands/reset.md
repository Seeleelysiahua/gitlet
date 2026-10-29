# `reset`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void reset(String givenID)`

## 2. Dependent Data

- Commit corresponding to parameter givenID: filename → blob ID map
- current commit filename → blob ID map
- Byte contents of each blob in the target commit
- objects/commits/ directory list (abbreviated resolution)
- All normal file names in the working directory
- stage
- HEAD: current branch name
- Current branch pointer file under refs/heads

## 3. Execution Steps

1. Resolve abbreviated ID to full commit ID → `String commitID = resolveCommitID(givenID)`
2. Get target commit object → `Commit targetedCommit = Utils.readObject(Utils.join(".gitlet", "objects", "commits", commitID), Commit.class)`
3. Get current commit object → `Commit currentCommit = getCurrentCommit()`
4. Get current branch name → `String branchName = getCurrentBranchName()`
5. Replace working directory entirely with target commit tracked files → `replaceWorkingDir(currentCommit, targetedCommit)`
6. Move current branch pointer → `Utils.writeContents(Utils.join(".gitlet", "refs", "heads", branchName), commitID)`
7. Clear stage → `Utils.writeObject(Utils.join(".gitlet", "stage"), new StagingArea())`

## 4. Reuse and Reused By

- Utils methods called: join, readObject, writeContents, writeObject
- Object interfaces called: `new StagingArea()` no-arg constructor creates empty stage for overwrite
- Called by: only dispatched by Repository.run; currently no other command reuses it
- Newly extracted helper methods: none. Calls extracted resolveCommitID(String) (abbreviated ID resolution, extracted by checkout card, see 03 §6), replaceWorkingDir(Commit, Commit) (whole working directory replacement, extracted by checkout card, see 03 §6), getCurrentCommit() (extracted by commit card, see 03 §6), getCurrentBranchName() (extracted by commit card, see 03 §6)

## 5. State Modification and Failure Protection

Boundary: all checks finish inside step 1 and step 5 (abbreviated resolution and untracked file check); first disk write starts at step 5 working directory write.

Changes:

- working directory: changed (overwrite/create files per target commit, delete files tracked by current commit but not target commit)
- staging area: changed (cleared)
- `.gitlet`: no change
- branch pointer: changed (current branch pointer moved to target commit ID)
- HEAD: no change

Failure protection: all business errors thrown before disk write, zero change on failure. Write order: working directory write/delete, overwrite branch pointer, clear stage; a system-level crash may stop half-switched:

- Working directory written/deleted, pointer not moved: branch still points to old commit; retry reset same ID completes
- Pointer moved, stage not cleared: branch already points to target commit; retry same ID reset is not blocked by checks (residual stage entries pass check), can complete clearing and restore consistency

## 6. Performance Check

spec requirement: runtime linear in total size of files in target commit snapshot, constant relative to any commit-count measure; abbreviated ID exempt from timing; memory not separately required.

Compliance analysis:

- runtime: full ID reads target commit directly, constant in commit count; working directory write/delete blobs total linear in snapshot total size; read single branch pointer, overwrite pointer, clear stage all constant; abbreviated prefix match lists objects/commits/ and scans, linear in commit count, exempt per rule
- memory: not separately required; peak is a single blob's bytes (read/written on the fly, no pre-read), plus directory filename list, two commits, and one stage object; scale grows only with current snapshot and working directory
