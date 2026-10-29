# `commit`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void commit(String message)`

## 2. Dependent Data

- stage: its addition map and removal set.
- HEAD: current branch name.
- current branch pointer: the commit ID it points to.
- current commit: filename → blob ID map, and the current commit's own ID.

## 3. Execution Steps

`commit` has only one step: delegate to the shared commit helper → `makeCommit(message, null)`.

### makeCommit

`makeCommit(message, secondParentID)`: shared implementation for committing the current stage. `commit` passes null (normal single-parent commit); `merge` passes the merged branch head commit ID (merge commit has two parents, see [02 Object Model](../02-object-model.md)). The following 16 steps are the helper's method body:

1. Check message is blank `message.isBlank()` → throw `Utils.error("Please enter a commit message.")`
2. Read stage → `StagingArea stage = Utils.readObject(Utils.join(".gitlet", "stage"), StagingArea.class)`
3. Check stage is empty `stage.addition.isEmpty() && stage.removal.isEmpty()` → throw `Utils.error("No changes added to the commit.")`
4. Locate current commit:
   - Get current branch name → `String branchName = getCurrentBranchName()`
   - Get current commit ID → `String commitID = getCurrentCommitID()`
   - Get current commit object → `Commit currentCommit = getCurrentCommit()`
5. Construct new map → `Map<String, String> newMap = new HashMap<>(currentCommit.filenameToBlob)`
6. Update newMap by stage removal → `newMap.keySet().removeAll(stage.removal)`
7. Update newMap by stage addition → `newMap.putAll(stage.addition)`
8. Compute timestamp → `Date timestamp = new Date()`
9. Construct new commit object → `Commit newCommit = new Commit(message, timestamp, commitID, secondParentID, newMap)`
10. Construct list for commit ID computation → `List<Object> sha1Vals = new ArrayList<>()`
11. Get sorted newMap → `TreeMap<String, String> sortedMap = new TreeMap<>(newMap)`
12. Fill sha1Vals →

```java
sha1Vals.add("commit");
sha1Vals.add(message);
sha1Vals.add(String.valueOf(timestamp.getTime()));
sha1Vals.add(commitID);
sha1Vals.add(secondParentID == null ? "" : secondParentID);
for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
    sha1Vals.add(entry.getKey());
    sha1Vals.add(entry.getValue());
}
```

13. Compute new commit ID → `String newCommitID = Utils.sha1(sha1Vals)`
14. Write commit → `Utils.writeObject(Utils.join(".gitlet","objects","commits",newCommitID), newCommit)`
15. Write branchName pointer → `Utils.writeContents(Utils.join(".gitlet", "refs", "heads", branchName), newCommitID)`
16. Write stage → `Utils.writeObject(Utils.join(".gitlet", "stage"), new StagingArea())`

## 4. Reuse and Reused By

- Utils methods called: error, readObject, sha1, writeContents, writeObject, join
- Object interfaces called:
  - Commit: read field filenameToBlob, five-arg constructor
  - StagingArea: no-arg constructor; read fields addition, removal; does not modify the read object (clearing is done by writing a new empty object)
- Called by: commit only dispatched by Repository.run; makeCommit called by commit and merge
- Newly extracted helper methods:
  - `static String getCurrentBranchName()` — read HEAD, get current branch name
  - `static String getCurrentCommitID()` — call getCurrentBranchName(), read refs/heads branch pointer, get commit ID
  - `static Commit getCurrentCommit()` — call getCurrentCommitID(), deserialize current commit from objects/commits by ID
  - `static void makeCommit(String message, String secondParentID)` — shared implementation for committing current stage (the 16 steps in §3 are its body), extracted when merge card appeared (same logic second card); when secondParentID is null, it becomes "" per 02 for hashing (see 03 §6)

## 5. State Modification and Failure Protection

Boundary: all checks finish at makeCommit step 3; after that only in-memory construction and computation; first disk write starts at step 14.

Changes:

- working directory: no change
- staging area: changed (overwrite stage)
- `.gitlet`: changed (add one commit object)
- branch pointer: changed (current branch pointer points to new commit)
- HEAD: no change

Failure protection: on disk, write commit first, then move pointer, finally overwrite stage. Two kinds of residue by failure location:

- Commit written, pointer not moved (including mid-move) failure: repo leaves an unreferenced commit
- Pointer moved, stage not cleared (including mid-clear) failure: pointer already points to new commit, stage still has this batch. Re-running commit at most produces a redundant commit with the same map; does not corrupt repo or lose data

## 6. Performance Check

spec requirements:

- runtime constant relative to any commit-count measure
- runtime no worse than linear relative to total size of files tracked by this commit
- memory: after commit, `.gitlet` increase does not exceed total size of files staged-for-addition at commit time, excluding extra metadata

Compliance analysis:

- `getCurrentCommit()` gets current commit; does not read a second historical commit or traverse history, so runtime is independent of commit count (constant)
- Copying/updating map and sorting grow linearly only with this snapshot's file count and filename/blob-ID lengths, so no worse than linear relative to total size of files tracked by this commit
- Unchanged files keep same ID; new map points to existing blobs; no duplicate content. New content bytes come only from staged-for-addition files, whose blobs were already written by add; this command writes no blob. New commit adds only filename → blob-ID map entries, which spec explicitly excludes from the increase limit. So `.gitlet` content increase does not exceed total staged file size.
