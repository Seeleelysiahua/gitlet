# `rm`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void rm(String fileName)`

## 2. Dependent Data

- Parameter fileName
- stage: its addition map and removal set
- current commit: filename → blob ID map

## 3. Execution Steps

1. Read stage → `StagingArea stage = Utils.readObject(Utils.join(".gitlet", "stage"), StagingArea.class)`
2. Read current commit object → `Commit currentCommit = getCurrentCommit()`
3. Check target file is neither in addition nor in current commit map `!stage.addition.containsKey(fileName) && !currentCommit.filenameToBlob.containsKey(fileName)` → throw `Utils.error("No reason to remove the file.")`
4. Remove the file from addition → `stage.addition.remove(fileName)`
5. If target file is in current commit map `currentCommit.filenameToBlob.containsKey(fileName)`:
   - Add filename to removal → `stage.removal.add(fileName)`
   - Delete target file → `Utils.restrictedDelete(fileName)`
6. Write stage back → `Utils.writeObject(Utils.join(".gitlet", "stage"), stage)`

## 4. Reuse and Reused By

- Utils methods called: readObject, join, error, restrictedDelete, writeObject
- Object interfaces called:
  - Commit: read field filenameToBlob (containsKey)
  - StagingArea: read/modify field addition (containsKey/remove), removal (add)
- Called by: only dispatched by Repository.run; currently no other command reuses it
- Newly extracted helper methods: none. Calls extracted getCurrentCommit() (extracted by commit card, see 03 §6)

## 5. State Modification and Failure Protection

Boundary: the only check finishes at step 3; step 4 only modifies in-memory object, no disk write; first disk write starts at step 5 (delete working file first, then write stage at step 6).

Changes:

- working directory: changed (if file tracked by current commit: delete that working file)
- staging area: changed (remove from addition, add to removal, overwrite stage)
- `.gitlet`: changed (only stage file overwrite, no new object)
- branch pointer: no change
- HEAD: no change

Failure protection: on disk in two steps, delete target file first, then overwrite stage. Delete-before-write: mid-way failure leaves file deleted but removal not marked; file still tracked by current commit and recoverable. Reverse order: if delete fails, target file silently lost on next commit.

## 6. Performance Check

spec requirement: runtime constant relative to any significant measure; memory and `.gitlet` growth not separately required.

Compliance analysis:

- runtime: operations on addition, removal, current commit are all constant; stage read/write does not grow with history total
- memory: not separately required; peak is one stage object, varies only with current staged entry count
