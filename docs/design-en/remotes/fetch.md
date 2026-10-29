# `fetch` (bonus)

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void fetch(String remoteName, String remoteBranchName)`

## 2. Dependent Data

- `.gitlet/remotes`: map from remote name to remote `.gitlet` directory path
- Remote `.gitlet` directory: existence, refs/heads/<remoteBranchName> pointer file, each commit object along parent backtrack (firstParent/secondParent/filenameToBlob) and each blob's bytes
- Local `.gitlet`: existence of each object file under objects/commits and objects/blobs

## 3. Execution Steps

1. Read remote map → `HashMap<String, String> remotes = readRemotes()`
2. Get remote `.gitlet` directory path → `String remoteGitlet = remotes.get(remoteName)`
3. Check remote directory does not exist `remoteGitlet == null || !Utils.join(remoteGitlet).exists()` → throw `Utils.error("Remote directory not found.")`
4. Construct remote branch pointer file object → `File remoteBranchFile = Utils.join(remoteGitlet, "refs", "heads", remoteBranchName)`
5. Check remote branch does not exist `!remoteBranchFile.exists()` → throw `Utils.error("That remote does not have that branch.")`
6. Read remote branch head commit ID → `String remoteHeadID = Utils.readContentsAsString(remoteBranchFile)`
7. Copy commits and referenced blobs not yet present locally → `copyMissingObjects(remoteGitlet, GITLET_DIR.getPath(), remoteHeadID)`
8. Create remote tracking branch directory → `Utils.join(".gitlet", "refs", "remotes", remoteName).mkdirs()`
9. Write remote tracking branch pointer → `Utils.writeContents(Utils.join(".gitlet", "refs", "remotes", remoteName, remoteBranchName), remoteHeadID)`

### copyMissingObjects

`static void copyMissingObjects(String srcGitlet, String dstGitlet, String headID)`: from the source repo to the target repo, copy commits reachable from headID along parents that are not yet in target, and their referenced blobs; stop the whole branch when target already has the commit. src/dst are `.gitlet` directory path strings: the remote path read from map at runtime is used directly, the local side passes `GITLET_DIR.getPath()` (fetch remote → local, push local → remote, opposite directions share the same method):

```java
static void copyMissingObjects(String srcGitlet, String dstGitlet, String headID) {
    ArrayDeque<String> stack = new ArrayDeque<>();
    stack.push(headID);

    while (!stack.isEmpty()) {
        String id = stack.pop();
        File dstCommit = Utils.join(dstGitlet, "objects", "commits", id);
        if (dstCommit.exists()) {
            continue;
        }
        Commit commit = Utils.readObject(
            Utils.join(srcGitlet, "objects", "commits", id), Commit.class);
        Utils.writeObject(dstCommit, commit);
        for (String blobID : commit.filenameToBlob.values()) {
            File dstBlob = Utils.join(dstGitlet, "objects", "blobs", blobID);
            if (!dstBlob.exists()) {
                Utils.writeContents(dstBlob,
                    Utils.readContents(
                        Utils.join(srcGitlet, "objects", "blobs", blobID)));
            }
        }
        if (commit.firstParent != null) {
            stack.push(commit.firstParent);
        }
        if (commit.secondParent != null) {
            stack.push(commit.secondParent);
        }
    }
}
```

## 4. Reuse and Reused By

- Utils methods called: join, error, readContentsAsString, writeContents
- Object interfaces called: none
- Called by: dispatched by Repository.run, and reused by pull
- Newly extracted helper methods: `static void copyMissingObjects(String srcGitlet, String dstGitlet, String headID)`

## 5. State Modification and Failure Protection

Boundary: two business checks at steps 3/5, read-only throughout; first disk write at step 7 (add-only copy).

Changes:

- working directory: no change
- staging area: no change
- `.gitlet`: changed (adds locally missing commit and blob objects)
- branch pointer: changed (add or update refs/remotes/<remote>/<branch> pointer)
- HEAD: no change

Failure protection: the two business failures (remote directory / remote branch missing) are both before the first disk write; write order: add-only copy objects first, then create directory, finally write pointer — a crash mid-copy leaves only unreferenced add-only orphan objects, pointer unmoved, remote repo never written; rerun is idempotent.
