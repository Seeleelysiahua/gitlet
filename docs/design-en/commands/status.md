# `status`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void status()`

## 2. Dependent Data

- All normal files in the working directory (ignore subdirectories): read bytes and compute blob ID for content comparison
- refs/heads/ directory: list all local branch names
- stage: addition (filename → blob ID) and removal (filename) two groups
- current commit: filename → blob ID map
- HEAD: get current branch name to mark `*`

## 3. Execution Steps

1. Read all branch names in refs/heads/ → `List<String> allBranches = Utils.plainFilenamesIn(Utils.join(".gitlet", "refs", "heads"))`
2. Get current branch name → `String branchName = getCurrentBranchName()`
3. Read stage → `StagingArea stage = Utils.readObject(Utils.join(".gitlet", "stage"), StagingArea.class)`
4. Get current commit object → `Commit currentCommit = getCurrentCommit()`
5. Get each working directory file name → `List<String> allFileName = Utils.plainFilenamesIn(".")`
6. Loop working directory files, read bytes and compute blob ID, collect into map:

```java
Map<String, String> allWID = new HashMap<>();
for (String filename : allFileName) {
    byte[] fileContent = Utils.readContents(new File(filename));
    String id = Utils.sha1("blob", fileContent);
    allWID.put(filename, id);
}
```

7. Merge filenames from current commit, stage, and working directory to build traversal range:

```java
Set<String> union = new HashSet<>();
union.addAll(allFileName);
union.addAll(stage.addition.keySet());
union.addAll(stage.removal);
union.addAll(currentCommit.filenameToBlob.keySet());
```

8. Construct modifiedNotStaged → `TreeMap<String, Boolean> modifiedNotStaged = new TreeMap<>()` (value convention: true=modified, false=deleted)
9. Construct untracked → `TreeSet<String> untracked = new TreeSet<>()`
10. Traverse union and classify:

```java
for (String name : union) {
    boolean inW = allWID.containsKey(name);
    boolean inA = stage.addition.containsKey(name);
    boolean inR = stage.removal.contains(name);
    boolean inC = currentCommit.filenameToBlob.containsKey(name);

    boolean expectedExists = inA || (inC && !inR);

    if (inW) {
        if (expectedExists) {
            String expectedID = inA
                ? stage.addition.get(name)
                : currentCommit.filenameToBlob.get(name);
            String wID = allWID.get(name);
            if (!wID.equals(expectedID)) {
                modifiedNotStaged.put(name, true);
            }
        } else {
            untracked.add(name);
        }
    } else {
        if (inA) {
            modifiedNotStaged.put(name, false);
        } else if (inC && !inR) {
            modifiedNotStaged.put(name, false);
        }
    }
}
```

11. Print in five-section format:

```java
Utils.message("=== Branches ===");
for (String branch : allBranches) {
    if (branchName.equals(branch)) {
        Utils.message("*%s", branch);
    } else {
        Utils.message("%s", branch);
    }
}
Utils.message("");

Utils.message("=== Staged Files ===");
for (String filename : stage.addition.keySet()) {
    Utils.message("%s", filename);
}
Utils.message("");

Utils.message("=== Removed Files ===");
for (String filename : stage.removal) {
    Utils.message("%s", filename);
}
Utils.message("");

Utils.message("=== Modifications Not Staged For Commit ===");
for (String name : modifiedNotStaged.keySet()) {
    if (modifiedNotStaged.get(name)) {
        Utils.message("%s (modified)", name);
    } else {
        Utils.message("%s (deleted)", name);
    }
}
Utils.message("");

Utils.message("=== Untracked Files ===");
for (String name : untracked) {
    Utils.message("%s", name);
}
Utils.message("");
```

## 4. Reuse and Reused By

- Utils methods called: plainFilenamesIn, join, readObject, readContents, sha1, message
- Object interfaces called:
  - Commit: read filenameToBlob
  - StagingArea: read addition, removal
- Called by: only dispatched by Repository.run; currently no other command reuses it
- Newly extracted helper methods: none; calls commit-card-extracted getCurrentBranchName(), getCurrentCommit()

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

spec requirement: runtime depends only on working directory data amount, number of staged-for-addition/removal files, and branch count; memory not separately required.

Compliance analysis:

- runtime: listing refs/heads/ linear in branch count; listing working directory and reading bytes/hashing each file linear in working directory data amount; reading/traversing stage linear in addition/removal entry count; classification traversal does a constant number of hash containsKey/get per file. Also traverses current commit's filenameToBlob, which is inherent input: it only deserializes the current one commit (exempt per rule), size depends only on current working state, independent of total historical commits; even if it contains filenames already deleted from working dir and not staged, they do not introduce whole-repo growth. So it meets spec's "only" constraint
- memory: not separately required; peak is the working directory blob-ID map, union set, and two ordered result containers (modifiedNotStaged, untracked), linear in "working directory file count + stage entry count + filenameToBlob entry count"; per loop also one file's bytes and several booleans/IDs; holds no historical commit objects
