# `push` (bonus)

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void push(String remoteName, String remoteBranchName)`

## 2. Dependent Data

- `.gitlet/remotes`: map from remote name to remote `.gitlet` directory path
- Remote `.gitlet` directory: existence, refs/heads/<remoteBranchName> pointer file (existence + content = remote branch head commit ID), existence of each object file under objects
- Local `.gitlet`: current branch name stored in HEAD, current branch pointer commit ID, each commit object along parent backtrack (firstParent/secondParent/filenameToBlob) and each blob's bytes

## 3. Execution Steps

1. Read remote map → `HashMap<String, String> remotes = readRemotes()`
2. Get remote `.gitlet` directory path → `String remoteGitlet = remotes.get(remoteName)`
3. Check remote directory does not exist `remoteGitlet == null || !Utils.join(remoteGitlet).exists()` → throw `Utils.error("Remote directory not found.")`
4. Get current branch head commit ID → `String localHeadID = getCurrentCommitID()`
5. Construct remote branch pointer file object → `File remoteBranchFile = Utils.join(remoteGitlet, "refs", "heads", remoteBranchName)`
6. If remote branch already exists, read its head and do fast-forward check (remote head must be an ancestor of local head, equality included; traversal only reads both parents along local COMMITS_DIR, independent of whether objects have been copied, no pruning); if it does not exist, skip this check, step 8 creates the branch:

```java
if (remoteBranchFile.exists()) {
    String remoteHeadID = Utils.readContentsAsString(remoteBranchFile);
    if (!isAncestor(remoteHeadID, localHeadID)) {
        throw Utils.error("Please pull down remote changes before pushing.");
    }
}
```

7. Copy commits and referenced blobs not yet present remotely → `copyMissingObjects(GITLET_DIR.getPath(), remoteGitlet, localHeadID)`
8. Write remote branch pointer to local head → `Utils.writeContents(remoteBranchFile, localHeadID)`

### isAncestor

`private static boolean isAncestor(String ancestorID, String descendantID)`: determines whether ancestorID equals descendantID or is in its ancestor set. Explicit stack + `HashSet<String> visited` dedupe; from descendantID traverse both parents via getFirstParent()/getSecondParent() (commit files read via Utils.join(COMMITS_DIR, id)); hit ancestorID returns true, empty stack returns false. Registered in this card, body in the single-card private helper area.

## 4. Reuse and Reused By

- Utils methods called: join, error, readContentsAsString, writeContents
- Object interfaces called: none (parent traversal encapsulated in isAncestor, accessed via getters)
- Called by: only dispatched by Repository.run; currently no other command reuses it
- Newly extracted helper methods: `private static boolean isAncestor(String ancestorID, String descendantID)` (single-card private helper). Calls extracted readRemotes(), copyMissingObjects(String, String, String)

## 5. State Modification and Failure Protection

Boundary: two business checks at steps 3 and 6; local never writes disk, first write to remote at step 7.

Changes:

- working directory: no change
- staging area: no change
- `.gitlet`: no change (this command only writes the remote repo)
- branch pointer: no change (local branch unmoved; the moved one is the remote branch pointer)
- HEAD: no change

Failure protection: the two business failures (remote directory missing, remote head not in local history) are both before the first write to remote; remote write order: add-only copy objects first, then branch pointer — a crash mid-copy leaves only unreferenced add-only orphan objects on remote, pointer unmoved; rerun is idempotent (remote existing objects skipped by exists, pointer rewritten to the same ID). Local state zero change throughout.
