# `add`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void add(String fileName)`

## 2. Dependent Data

- The working directory file corresponding to parameter fileName
- stage: its addition map and removal set
- HEAD: current branch name
- current branch pointer: the commit ID it points to
- current commit: filename → blob ID map

## 3. Execution Steps

1. Check whether the target file exists:
   - Construct target file → `File file = Utils.join(CWD, fileName)`
   - Check target file does not exist `!file.exists()` → throw `Utils.error("File does not exist.")`
2. Read target file bytes → `byte[] fileContent = Utils.readContents(file)`
3. Read stage → `StagingArea stage = Utils.readObject(Utils.join(".gitlet", "stage"), StagingArea.class)`
4. Clean removal → `boolean removedFromRemoval = stage.removal.remove(fileName)`
5. Get current commit object (call extracted `getCurrentCommit()`, which internally completes the three-hop via getCurrentCommitID()/getCurrentBranchName(), see 03 §6) → `Commit currentCommit = getCurrentCommit()`
6. Compute target file blob ID → `String blobID = Utils.sha1("blob", fileContent)`
7. Initialize two flags → `boolean needWriteBlob = false`, `boolean stageChanged = removedFromRemoval`
8. Determine this change (top-down; hit one rule, handle it and stop; otherwise continue):
   - Not tracked `!currentCommit.filenameToBlob.containsKey(fileName)` → `needWriteBlob = true`; `stageChanged = true`;
   - Get committed blob ID (reaching here means tracked) → `String commitBlobID = currentCommit.filenameToBlob.get(fileName)`
   - Content differs `!blobID.equals(commitBlobID)` → `needWriteBlob = true`; `stageChanged = true`;
   - Content same and in addition `stage.addition.containsKey(fileName)` → `stage.addition.remove(fileName)`; `stageChanged = true`;
   - Content same, not in addition, but removal was cleaned this time `removedFromRemoval` → do not change flags (step 10 writes back based on stageChanged)
   - Otherwise (content same, not in addition, removal not cleaned) → do nothing
9. If needWriteBlob → idempotent blob write (same content gives same ID; overwrite and skip are equivalent): `Utils.writeContents(Utils.join(".gitlet", "objects", "blobs", blobID), fileContent)`; then add to addition → `stage.addition.put(fileName, blobID)`
10. If stageChanged → write stage back: `Utils.writeObject(Utils.join(".gitlet", "stage"), stage)`; otherwise do not write, command ends normally

## 4. Reuse and Reused By

- Utils methods called: error, readContents, readObject, sha1, writeContents, writeObject, join
- Object interfaces called:
  - Commit: read field filenameToBlob (containsKey/get)
  - StagingArea: read/modify field addition (containsKey/remove/put), removal (remove)
- Called by: only dispatched by Repository.run; currently no other command reuses it
- Newly extracted helper methods: none. Calls extracted getCurrentBranchName()/getCurrentCommitID()/getCurrentCommit() (extracted by commit card, see 03 §6)

## 5. State Modification and Failure Protection

Boundary: the only check finishes at step 1; steps 2–8 only read disk, then compute and modify in memory, no disk write; first disk write starts at step 9.

Changes:

- working directory: no change
- staging area: changed (overwrite stage)
- `.gitlet`: changed (add one blob object)
- branch pointer: no change
- HEAD: no change

Failure protection: on disk, write blob first, then overwrite stage; mid-way failure at most leaves an unreferenced new blob.

## 6. Performance Check

spec requirement: runtime linear in added file size plus lgN (N = number of files in commit); memory not separately required.

Compliance analysis:

- runtime: read bytes / compute hash / write blob are each linear in file size; lookup in the commit's filenameToBlob is constant, does not grow with N; reading stage/current commit is deserialization, exempt per rule. Actual runtime is O(file size), better than spec's O(file size+lgN)
- memory: spec does not separately require; peak is added file bytes, linear in file size.
