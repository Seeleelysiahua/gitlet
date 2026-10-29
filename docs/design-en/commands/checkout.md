# `checkout`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void checkout(String[] operands)`

- Usage 1: operands = `-- [file name]`
- Usage 2: operands = `[commit id] -- [file name]`
- Usage 3: operands = `[branch name]`

Three private dispatch helpers:

- Usage 1: `private static void checkoutFile(String filename)`
- Usage 2: `private static void checkoutCommit(String givenID, String filename)`
- Usage 3: `private static void checkoutBranch(String branchName)`

Shared private helper for usage 1/2: `private static void checkoutFileFromCommit(Commit commit, String filename)`

## 2. Dependent Data

**Usage 1**

- current commit: filename → blob ID map
- Target blob: file bytes

**Usage 2**

- Commit corresponding to parameter givenID: filename → blob ID map
- Target blob: file bytes
- objects/commits/ directory list (abbreviated resolution)

**Usage 3**

- HEAD: current branch name
- refs/heads: commit pointed by target branch pointer
- Target commit filename → blob ID map
- current commit filename → blob ID map
- Byte contents of each blob in the target commit
- All normal file names in the working directory
- stage

## 3. Execution Steps

### Dispatch

Dispatch to helper by element count of operands and position of `--`:

```java
if (operands.length == 2) {
    if (!operands[0].equals("--")) {
        throw Utils.error("Incorrect operands.");
    }
    else {
        checkoutFile(operands[1]);
    }
}
else if (operands.length == 3) {
    if (!operands[1].equals("--")) {
        throw Utils.error("Incorrect operands.");
    }
    else {
        checkoutCommit(operands[0], operands[2]);
    }
}
else if (operands.length == 1) {
    checkoutBranch(operands[0]);
}
else {
    throw Utils.error("Incorrect operands.");
}
```

### Usage 1

1. Get current commit object → `Commit currentCommit = getCurrentCommit()`
2. Call shared helper to restore file → `checkoutFileFromCommit(currentCommit, filename)`

`checkoutFileFromCommit` is the shared private helper for usage 1/2:

1. Get map → `Map<String, String> filenameToBlob = commit.getFilenameToBlob()`
2. Get blob ID → `String blobID = filenameToBlob.get(filename)`
3. Check target filename does not exist in that commit `blobID == null` → throw `Utils.error("File does not exist in that commit.")`
4. Read blob content → `byte[] fileContent = Utils.readContents(Utils.join(".gitlet", "objects", "blobs", blobID))`
5. Write to working directory (overwrite if exists) → `Utils.writeContents(new File(filename), fileContent)`

### Usage 2

1. Resolve abbreviated ID to full commit ID → `String commitID = resolveCommitID(givenID)`

`resolveCommitID` is a cross-command helper (shared by checkout and reset, see 03 §6), defined as:

```java
static String resolveCommitID(String givenID) {
    List<String> allCommitID = Utils.plainFilenamesIn(
        Utils.join(".gitlet", "objects", "commits"));
    String commitID = null;
    for (String id : allCommitID) {
        if (id.startsWith(givenID.toLowerCase())) {
            if (commitID != null) {
                throw Utils.error("No commit with that id exists.");
            }
            commitID = id;
        }
    }
    if (commitID == null) {
        throw Utils.error("No commit with that id exists.");
    }
    return commitID;
}
```

2. Get target commit object → `Commit targetedCommit = Utils.readObject(Utils.join(".gitlet", "objects", "commits", commitID), Commit.class)`
3. Call shared helper to restore file → `checkoutFileFromCommit(targetedCommit, filename)`

### Usage 3

1. Get all branch names → `List<String> allBranchName = Utils.plainFilenamesIn(Utils.join(".gitlet", "refs", "heads"))`
2. Check target branch does not exist `!allBranchName.contains(branchName)` → throw `Utils.error("No such branch exists.")`
3. Get current branch name → `String currentBranchName = getCurrentBranchName()`
4. Check target branch is current branch `currentBranchName.equals(branchName)` → throw `Utils.error("No need to checkout the current branch.")`
5. Get target branch current commit ID → `String commitID = Utils.readContentsAsString(Utils.join(".gitlet", "refs", "heads", branchName))`
6. Get current commit object → `Commit currentCommit = getCurrentCommit()`
7. Get target branch current commit object → `Commit targetedCommit = Utils.readObject(Utils.join(".gitlet", "objects", "commits", commitID), Commit.class)`
8. Replace working directory entirely with target commit tracked files → `replaceWorkingDir(currentCommit, targetedCommit)`

`replaceWorkingDir` is a cross-command helper (shared by checkout and reset, see 03 §6), defined as:

```java
static void replaceWorkingDir(Commit currentCommit, Commit targetedCommit) {
    StagingArea stage = Utils.readObject(
        Utils.join(".gitlet", "stage"), StagingArea.class);
    List<String> allFileName = Utils.plainFilenamesIn(".");
    for (String name : allFileName) {
        if (stage.addition.containsKey(name)) {
            continue;
        }
        if (currentCommit.filenameToBlob.containsKey(name)
                && !stage.removal.contains(name)) {
            continue;
        }
        if (targetedCommit.filenameToBlob.containsKey(name)) {
            throw Utils.error("There is an untracked file in the way; delete it, or add and commit it first.");
        }
    }
    for (Map.Entry<String, String> e
            : targetedCommit.filenameToBlob.entrySet()) {
        String afileName = e.getKey();
        String aBlobID = e.getValue();
        byte[] afileContent = Utils.readContents(
            Utils.join(".gitlet", "objects", "blobs", aBlobID));
        Utils.writeContents(new File(afileName), afileContent);
    }
    for (String name : allFileName) {
        if (!targetedCommit.filenameToBlob.containsKey(name)
                && currentCommit.filenameToBlob.containsKey(name)) {
            Utils.restrictedDelete(name);
        }
    }
}
```

9. Update HEAD → `Utils.writeContents(Utils.join(".gitlet", "HEAD"), branchName)`
10. Clear stage → `Utils.writeObject(Utils.join(".gitlet", "stage"), new StagingArea())`

## 4. Reuse and Reused By

- Utils methods called: plainFilenamesIn, join, readContents, writeContents, readContentsAsString, readObject, writeObject, restrictedDelete, error
- Object interfaces called:
  - Commit: read filenameToBlob (containsKey/get/entrySet)
  - StagingArea: read addition, removal; `new StagingArea()` no-arg constructor creates empty stage for overwrite
- Called by: only dispatched by Repository.run by operand shape to this card's three private dispatch branches (single-file restore goes through shared checkoutFileFromCommit); currently no other command reuses it
- Newly extracted helper methods: three private dispatch helpers checkoutFile(String), checkoutCommit(String, String), checkoutBranch(String) and shared private helper checkoutFileFromCommit(Commit, String), only serve this card; cross-command helpers resolveCommitID(String) (abbreviated ID resolution, returns unique full ID) and replaceWorkingDir(Commit, Commit) (whole working directory replacement, blocking check inside) are package-private, shared by checkout and reset, registered in 03 §6/interface list

## 5. State Modification and Failure Protection

Boundary: usage 1/2 finish checks at the file existence check (usage 2 also abbreviated resolution); after that only read objects, first disk write at the last step; usage 3 all checks finish at steps 2–8 (blocking check inside replaceWorkingDir before write/delete), first disk write starts at step 8 working directory write.

Changes:

- working directory: changed (usage 1/2 overwrite single file; usage 3 overwrite/create files per target commit, delete files tracked by current commit but not target commit)
- staging area: unchanged (usage 1/2); changed (usage 3 clears)
- `.gitlet`: no change
- branch pointer: no change
- HEAD: no change (usage 1/2); changed (usage 3 points to target branch name)

Failure protection: all business errors thrown before first disk write, zero change on failure. Usage 3 write order: write working files, delete files, write HEAD, clear stage; a system-level crash may stop half-switched:

- Working files written/deleted, HEAD not moved: repo still belongs to the original branch; retry checkout completes the branch switch
- HEAD moved, stage not cleared: branch already switched to target, stage has residual old-branch entries; retry checkout exits at the current branch check. Blobs referenced by entries were written to objects at add, data not lost; the repo can be fixed by reset to the same commit ID (verified by the reset card: reset has no "current branch" check; on retry residual stage entries pass the blocking check, can complete clearing and restore consistency)

## 6. Performance Check

spec requirement: usage 1 runtime linear in checked-out file size; usage 2/3 runtime linear in total size of files in that commit snapshot, constant relative to any commit-count measure, constant relative to branch count; commands using abbreviated ID exempt from timing; memory not separately required.

Compliance analysis:

- runtime:
  - Usage 1: read/write one blob, I/O equals that file size, linear in file size
  - Usage 2: read/write one blob, linear in file size; read commit directly by ID, constant in commit count; abbreviated prefix match lists objects/commits/ and scans, linear in commit count, exempt per rule
  - Usage 3: read/write all blobs recorded in the target commit's map entries, linear in total size of files in that commit snapshot; read commit ID by branch name, constant in branch count; read commit directly by ID, constant in commit count
- memory: not separately required; peak is a single blob's bytes plus directory filename list, two commits, and one stage object; scale grows only with current snapshot and working directory
