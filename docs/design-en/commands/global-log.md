# `global-log`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void globalLog()`

## 2. Dependent Data

- objects/commits/ directory: list all file names (each file name is a commit ID)
- Each commit: deserialize Commit from objects/commits/<ID> by file name, read message, timestamp, firstParent, secondParent

## 3. Execution Steps

1. Read all file names in objects/commits/ → `List<String> allCommitIDs = Utils.plainFilenamesIn(Utils.join(".gitlet", "objects", "commits"))`
2. Loop print each commit:

```java
for (String id : allCommitIDs) {
    Commit commit = Utils.readObject(
        Utils.join(".gitlet", "objects", "commits", id),
        Commit.class);
    printCommit(commit, id);
}
```

## 4. Reuse and Reused By

- Utils methods called: plainFilenamesIn, join, readObject
- Object interfaces called:
  - Commit: read by ID via readObject; field reading is encapsulated in printCommit, this card does not access directly
- Called by: only dispatched by Repository.run; currently no other command reuses it
- Newly extracted helper methods: none. Calls log-card-extracted printCommit(Commit, String) (see 03 §6); this card is its second caller

## 5. State Modification and Failure Protection

Boundary: this command is entirely read-only, no check line, no disk write step; no boundary.

Changes:

- working directory: no change
- staging area: no change
- `.gitlet`: no change
- branch pointer: no change
- HEAD: no change

Failure protection: no command-specific failure, no disk write, no failure window or residue; no failure protection needed.

## 6. Performance Check

spec requirement: runtime linear in total number of commits ever created; memory not separately required.

Compliance analysis:

- runtime: plainFilenamesIn lists objects/commits/ once (linear in file count), then each commit readObject once by ID, printCommit prints fixed lines; total linear in all commits, meets spec
- memory: not separately required; peak is the list of all commit ID strings (linear in commit count) plus one Commit object per loop
