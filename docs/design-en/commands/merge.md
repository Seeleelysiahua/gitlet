# `merge`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void merge(String branchName)`

## 2. Dependent Data

Common preconditions (all three paths read):

- stage: addition + removal
- Merged branch pointer file (refs/heads/<branchName>, if missing fall back to refs/remotes/<branchName>): existence, content
- HEAD: current branch name
- refs/heads/<current branch> pointer file: commit ID of the current branch head (commit pointed by current branch pointer)
- Split point: firstParent/secondParent fields of each commit along parent backtrack from both heads

Boundary two fast-forward (including what replaceWorkingDir reads): filenameToBlob of current (current branch head) and given (merged branch head) commits, given's blob bytes, all normal file names in the working directory.

Normal merge: filenameToBlob of current / given / split point three commits, blob contents on both sides of conflict files, read objects/blobs bytes by blob ID when writing working files, working directory file existence for checks.

## 3. Execution Steps

1. Read stage → `StagingArea stage = Utils.readObject(Utils.join(".gitlet", "stage"), StagingArea.class)`
2. Check stage not empty `!stage.addition.isEmpty() || !stage.removal.isEmpty()` → throw `Utils.error("You have uncommitted changes.")`
3. Resolve the merged branch pointer file (first refs/heads, if missing then refs/remotes; the latter serves pull merging a remote tracking branch):

```java
File branchFile = Utils.join(".gitlet", "refs", "heads", branchName);
if (!branchFile.exists()) {
    branchFile = Utils.join(".gitlet", "refs", "remotes", branchName);
}
```

4. Check merged branch does not exist `!branchFile.exists()` → throw `Utils.error("A branch with that name does not exist.")`
5. Get current branch name → `String currentBranchName = getCurrentBranchName()`
6. Check merged branch is current branch `currentBranchName.equals(branchName)` → throw `Utils.error("Cannot merge a branch with itself.")`
7. Get current commit ID → `String currentCommitID = getCurrentCommitID()`
8. Get current commit object → `Commit currentCommit = getCurrentCommit()`
9. Get merged branch head commit ID → `String givenCommitID = Utils.readContentsAsString(branchFile)`
10. Get merged branch head commit object → `Commit givenCommit = Utils.readObject(Utils.join(".gitlet", "objects", "commits", givenCommitID), Commit.class)`
11. Get split point → `String splitPointID = findSplitPoint(currentCommitID, givenCommitID)`
12. Dispatch by split point (top-down; hit one, handle and end, otherwise continue):
    - split point is merged branch head `splitPointID.equals(givenCommitID)` → boundary one (step 13)
    - split point is current branch head `splitPointID.equals(currentCommitID)` → boundary two fast-forward (steps 14–17)
    - otherwise → normal merge (decision table + steps 18–24)
13. Boundary one (split point is merged branch head):
    - Print message and end → `Utils.message("Given branch is an ancestor of the current branch.");`, no state change
14. Boundary two (split point is current branch head, fast-forward to merged branch head):
    - Update working directory to merged branch snapshot → `replaceWorkingDir(currentCommit, givenCommit)`
15. Move current branch pointer to merged branch head → `Utils.writeContents(Utils.join(".gitlet", "refs", "heads", currentBranchName), givenCommitID)`
16. Clear stage → `Utils.writeObject(Utils.join(".gitlet", "stage"), new StagingArea())`
17. Print fast-forward message → `Utils.message("Current branch fast-forwarded.")`

### Normal merge (split point is neither given nor current head) — per-file decision table

Let s / c / g be file f's blob ID in split point / current head / given head filenameToBlob (null = that commit does not contain this file), `cChanged = !Objects.equals(c, s)`, `gChanged = !Objects.equals(g, s)`. Content addressing: compare blob ID = compare content; except the conflict branch reads blob content, no file content is read throughout.

| Branch | Condition | Action |
| --- | --- | --- |
| 1 | `!gChanged` | no-op (given unchanged, keep current state; includes both unchanged) |
| 2 | `gChanged && !cChanged && g != null` | adopt given version: write working file as g's blob content + `stage.addition.put(f, g)` |
| 3 | `gChanged && !cChanged && g == null` | given deleted: delete working file + `stage.removal.add(f)` |
| 4 | `gChanged && cChanged && Objects.equals(c, g)` | no-op (both changed the same, including both deleted) |
| 5 | `gChanged && cChanged && !Objects.equals(c, g)` | conflict: write five-segment content to working file + write new blob + `stage.addition.put(f, newBlobID)` + set conflict flag |

Conflict content is `"<<<<<<< HEAD\n" + cText + "=======\n" + gText + ">>>>>>>\n"`.

Steps 18–24 are the body of `mergeSnapshots` (merge calls it in the normal merge branch):

18. Read split point commit object → `Commit splitCommit = Utils.readObject(Utils.join(".gitlet", "objects", "commits", splitPointID), Commit.class)`
19. Per-file analysis:

```java
TreeSet<String> names = new TreeSet<>(splitCommit.filenameToBlob.keySet());
names.addAll(currentCommit.filenameToBlob.keySet());
names.addAll(givenCommit.filenameToBlob.keySet());

Map<String, String> writeBlob = new HashMap<>();
Map<String, String> newBlobContents = new HashMap<>();
List<String> deletions = new ArrayList<>();
boolean conflict = false;

for (String f : names) {
    String s = splitCommit.filenameToBlob.get(f);
    String c = currentCommit.filenameToBlob.get(f);
    String g = givenCommit.filenameToBlob.get(f);
    boolean cChanged = !Objects.equals(c, s);
    boolean gChanged = !Objects.equals(g, s);
    if (!gChanged) {
        continue;
    }
    if (!cChanged) {
        if (g != null) {
            stage.addition.put(f, g);
            writeBlob.put(f, g);
        } else {
            stage.removal.add(f);
            deletions.add(f);
        }
        continue;
    }
    if (Objects.equals(c, g)) {
        continue;
    }
    String cText = c == null ? "" : Utils.readContentsAsString(
        Utils.join(".gitlet", "objects", "blobs", c));
    String gText = g == null ? "" : Utils.readContentsAsString(
        Utils.join(".gitlet", "objects", "blobs", g));
    String merged = "<<<<<<< HEAD\n" + cText + "=======\n" + gText + ">>>>>>>\n";
    String newBlobID = Utils.sha1("blob", merged);
    newBlobContents.put(newBlobID, merged);
    writeBlob.put(f, newBlobID);
    stage.addition.put(f, newBlobID);
    conflict = true;
}
```

20. Blocking check (before any disk write):

```java
Set<String> touched = new HashSet<>(writeBlob.keySet());
touched.addAll(deletions);
for (String f : touched) {
    if (!currentCommit.filenameToBlob.containsKey(f) && Utils.join(f).exists()) {
        throw Utils.error("There is an untracked file in the way; delete it, or add and commit it first.");
    }
}
```

21. Execute writes/deletes (new blobs first, then working files, finally delete files):

```java
for (Map.Entry<String, String> e : newBlobContents.entrySet()) {
    Utils.writeContents(
        Utils.join(".gitlet", "objects", "blobs", e.getKey()), e.getValue());
}
for (Map.Entry<String, String> e : writeBlob.entrySet()) {
    Utils.writeContents(Utils.join(e.getKey()),
        Utils.readContents(Utils.join(".gitlet", "objects", "blobs", e.getValue())));
}
for (String f : deletions) {
    Utils.restrictedDelete(f);
}
```

22. If stage changed `!stage.addition.isEmpty() || !stage.removal.isEmpty()` → `Utils.writeObject(Utils.join(".gitlet", "stage"), stage)`
23. Create merge commit → `makeCommit("Merged " + branchName + " into " + currentBranchName + ".", givenCommitID)`
24. If conflict occurred `conflict` → `Utils.message("Encountered a merge conflict.")`

### Getting the Split Point

`findSplitPoint(currentCommitID, givenCommitID)` backtracks from both heads along parents and returns the latest common ancestor (split point) full ID. First collect current's ancestors and compute each ancestor's longest distance to the initial commit, then walk given's ancestors once and take the one with max distance:

```java
private static String findSplitPoint(String currentCommitID, String givenCommitID) {
    Map<String, Integer> depth = new HashMap<>();
    Set<String> ancestors = new HashSet<>();
    collectAncestors(currentCommitID, depth, ancestors);

    Set<String> visited = new HashSet<>();
    return deepestCommonAncestor(givenCommitID, ancestors, depth, visited);
}
```

Collect current's ancestors (including current) and record each ancestor's longest distance to initial:

```java
private static void collectAncestors(
        String id, Map<String, Integer> depth, Set<String> ancestors) {
    if (ancestors.contains(id)) {
        return;
    }
    ancestors.add(id);
    Commit commit = Utils.readObject(
        Utils.join(".gitlet", "objects", "commits", id), Commit.class);
    String first = commit.firstParent;
    String second = commit.secondParent;
    if (first == null) {
        depth.put(id, 0);
        return;
    }
    collectAncestors(first, depth, ancestors);
    if (second != null) {
        collectAncestors(second, depth, ancestors);
    }
    int d = depth.get(first);
    if (second != null) {
        d = Math.max(d, depth.get(second));
    }
    depth.put(id, d + 1);
}
```

Walk given's ancestors once, pick the one in ancestors with max distance:

```java
private static String deepestCommonAncestor(
        String id, Set<String> ancestors,
        Map<String, Integer> depth, Set<String> visited) {
    if (!visited.add(id)) {
        return null;
    }
    Commit commit = Utils.readObject(
        Utils.join(".gitlet", "objects", "commits", id), Commit.class);
    String first = commit.firstParent;
    String second = commit.secondParent;

    String best = ancestors.contains(id) ? id : null;
    if (first != null) {
        String candidate = deepestCommonAncestor(first, ancestors, depth, visited);
        if (candidate != null
                && (best == null || depth.get(candidate) > depth.get(best))) {
            best = candidate;
        }
    }
    if (second != null) {
        String candidate = deepestCommonAncestor(second, ancestors, depth, visited);
        if (candidate != null
                && (best == null || depth.get(candidate) > depth.get(best))) {
            best = candidate;
        }
    }
    return best;
}
```

## 4. Reuse and Reused By

- Utils methods called: readObject, error, join, readContentsAsString, message, writeContents, writeObject, sha1, readContents, restrictedDelete
- Object interfaces called:
  - Commit: read fields filenameToBlob (get/keySet/containsKey), firstParent, secondParent
  - StagingArea: read fields addition, removal and modify the stage read in memory (step 19's addition.put/removal.add); no-arg constructor `new StagingArea()` creates empty stage for overwrite (step 16)
- Called by: dispatched by Repository.run, and reused by pull (merging remote tracking branch)
- Newly extracted helper methods:
  - `private static void mergeSnapshots(...)` — normal merge per-file analysis, blocking check, write/delete and commit (§3 steps 18–24 body), only serves this card
  - findSplitPoint(String, String) and private recursion collectAncestors, deepestCommonAncestor — split point three methods, only serve this card, not registered in 03§6/interface list (same convention as checkout private dispatch helpers); body see §3 "Getting the Split Point"
- Calls extracted helpers (see 03 §6): getCurrentBranchName(), getCurrentCommitID(), getCurrentCommit(), replaceWorkingDir(Commit, Commit), makeCommit(String, String)

## 5. State Modification and Failure Protection

Boundary: pre-checks (steps 2/4/6) and split point solving are all read-only; boundary one writes nothing; boundary two first disk write at step 14; normal merge blocking check at step 20, empty stage check inside makeCommit, first disk write at step 21.

Changes:

- working directory: changed (boundary two replaces entirely with given snapshot; normal merge writes/deletes per list; boundary one no change)
- staging area: changed (boundary two clears; normal merge applies merge changes then writes back, clears after commit; boundary one no change)
- `.gitlet`: changed (normal merge adds conflict blob and merge commit object; boundary one/two no change)
- branch pointer: changed (boundary two moves to given head; normal merge moves to merge commit; boundary one no change)
- HEAD: no change

Failure protection:

- Normal merge write order: new blobs first, then write/delete working files, then write stage back, finally makeCommit (inside: write commit object, move pointer, clear stage)
- Crash window one (mid-write at step 21 or boundary two step 14): stage unchanged and still empty, rerun passes step 2, but already-written current-untracked files (e.g. split missing, only given has a new file) are blocked as untracked by the blocking check — manually delete residue then retry
- Crash window two (normal merge after stage write-back, before makeCommit completes): stage has merge entries, rerun blocked by step 2 `You have uncommitted changes.` — first reset back to current head then retry
- Crash window three (boundary two after step 15 moves pointer): stage already empty, working directory replaced, pointer already at given head, state consistent; rerun falls into boundary one and prints ancestor message, no side effect
- Zero-action path: steps 20/21 no-op, step 22 condition not satisfied skips write-back, no disk write throughout; then makeCommit's empty stage check throws `No changes added to the commit.`

## 6. Performance Check

spec requirement: runtime O(N log N + D) — N is total ancestor commits of the two branches, D is total file data under these commits; memory not separately required.

Compliance analysis:

- Split point: two DFS passes over the ancestor graph along parents, visited/ancestors dedupe, constant work plus one readObject per node; per-ancestor deserialization is active traversal of many objects, counted O(N)
- Normal merge analysis (steps 18–20): filename union no more than the sum of three snapshot file counts, constant hash lookups per file; only the conflict branch reads blob content
- Write/delete and commit (steps 21–23): total blob I/O no more than D; makeCommit map copy linear in snapshot file count F, TreeMap sort O(F log F), log factor absorbed by upper bound N log N
- Boundary two: replaceWorkingDir linear in target snapshot, blob I/O also bounded by D
- Total O(N) + O(F log F) + O(D), satisfies O(N log N + D) upper bound
- memory: peak = split point three tables (ancestors/depth/visited, O(N)) + analysis containers (writeBlob/deletions O(F), newBlobContents total conflict content no more than D) + constant number of single commit objects
