# `init`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void init()`

## 2. Dependent Data

None (init runs in an uninitialized empty directory; the repo does not exist yet, so there are no objects or files to read).

## 3. Execution Steps

1. Check whether `.gitlet` is already initialized:
   - Construct the `.gitlet` directory object → `File gitletDir = Utils.join(".gitlet")`
   - Check whether `.gitlet` already exists `gitletDir.exists()` → throw `Utils.error("A Gitlet version-control system already exists in the current directory.")`
2. Construct the initial commit in memory → `Commit commit = new Commit("initial commit", new Date(0), null, null, new HashMap<>())`
3. Compute the initial commit ID → `String commitID = Utils.sha1("commit", "initial commit", String.valueOf(new Date(0).getTime()), "", "")`
4. Build the directory tree → call `mkdirs()` on each of the following:
   - `Utils.join(".gitlet","objects","commits").mkdirs()`,
   - `Utils.join(".gitlet","objects","blobs").mkdirs()`,
   - `Utils.join(".gitlet","refs","heads").mkdirs()`,
   - `Utils.join(".gitlet","refs","remotes").mkdirs()`
5. Write the initial commit → `Utils.writeObject(Utils.join(".gitlet","objects","commits",commitID), commit)`
6. Write the master pointer → `Utils.writeContents(Utils.join(".gitlet","refs","heads","master"), commitID)`
7. Write HEAD → `Utils.writeContents(Utils.join(".gitlet","HEAD"), "master")`
8. Write an empty stage → `Utils.writeObject(Utils.join(".gitlet","stage"), new StagingArea())`

## 4. Reuse and Reused By

- Utils methods called: error, sha1, join, writeObject, writeContents
- Object interfaces called: Commit five-arg constructor, StagingArea no-arg constructor (both see the object interface list)
- Called by: only dispatched by Repository.run by command name; currently no other command reuses it
- Newly extracted helper methods: none

## 5. State Modification and Failure Protection

Boundary: the only check finishes at step 1; steps 2–3 only construct objects and compute IDs in memory, no disk access; the first disk write starts at step 4 `mkdirs()`.

Changes:

- working directory: no change
- staging area: changed (new empty stage)
- `.gitlet`: changed (new directory tree, new initial commit object)
- branch pointer: changed (new master pointing to initial commit)
- HEAD: changed (new, content master)

Failure protection: the only failure point (`.gitlet` already exists) is before any write; on disk, write the unreferenced initial commit first, then write branch pointer and HEAD.

## 6. Performance Check

spec requirement: runtime constant relative to any significant measure; memory not separately required.

Compliance analysis: does not read existing data or traverse directories; only a fixed number of directory creations and file writes, independent of repo size; runtime is constant.
