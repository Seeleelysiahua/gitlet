# `find`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void find(String message)`

## 2. Dependent Data

- objects/commits/ directory: list all file names (each file name is a commit ID)
- Each commit: deserialize Commit from objects/commits/<ID> by file name, read message

## 3. Execution Steps

1. Read all file names in objects/commits/ → `List<String> allCommitIDs = Utils.plainFilenamesIn(Utils.join(".gitlet", "objects", "commits"))`
2. Construct matched flag → `boolean matched = false`
3. Loop each commit:

```java
for (String id : allCommitIDs) {
    Commit commit = Utils.readObject(
        Utils.join(".gitlet", "objects", "commits", id),
        Commit.class);
    if (message.equals(commit.message)) {
        matched = true;
        Utils.message(id);
    }
}
```

4. No matching commit `!matched` → throw `Utils.error("Found no commit with that message.")`

## 4. Reuse and Reused By

- Utils methods called: plainFilenamesIn, join, readObject, message, error
- Object interfaces called:
  - Commit: read message field
- Called by: only dispatched by Repository.run; currently no other command reuses it
- Newly extracted helper methods: none

## 5. State Modification and Failure Protection

Boundary: this command is entirely read-only, no disk write step; the only failure is judged by matched after traversal matching; no write boundary.

Changes:

- working directory: no change
- staging area: no change
- `.gitlet`: no change
- branch pointer: no change
- HEAD: no change

Failure protection: no disk write, five states naturally unchanged.

## 6. Performance Check

spec requirement: runtime linear in total number of commits ever created; memory not separately required.

Compliance analysis:

- runtime: plainFilenamesIn lists objects/commits/ once (linear in commit count), then each commit readObject once by ID, one fixed-length string equals match; print one line on hit
- memory: not separately required; peak is the list of all commit IDs (linear in commit count) plus one Commit object and one matched flag per loop
