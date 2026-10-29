# `log`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void log()`

## 2. Dependent Data

- Start point: current commit (backtrack chain start) and its commit ID
- Backtrack chain: every commit from current commit along firstParent to the initial commit (both ends inclusive); each commit reads message, timestamp, firstParent, secondParent
- Merge commit: when secondParent is non-null, take first 7 chars of both parent IDs for the Merge: line

## 3. Execution Steps

1. Define helper printCommit to print one commit block (reused by global-log, see 03 §6):

```java
static void printCommit(Commit commit, String commitID) {
    Utils.message("===");
    Utils.message("commit %s", commitID);
    if (commit.secondParent != null) {
        Utils.message("Merge: %s %s",
            commit.firstParent.substring(0, 7),
            commit.secondParent.substring(0, 7));
    }
    String dateStr = new SimpleDateFormat(
        "EEE MMM d HH:mm:ss yyyy Z", Locale.US).format(commit.timestamp);
    Utils.message("Date: %s", dateStr);
    Utils.message(commit.message);
    Utils.message("");
}
```

2. Get current commit ID → `String commitID = getCurrentCommitID()`
3. Get current commit object → `Commit currentCommit = getCurrentCommit()`
4. Loop along firstParent while commit ID is non-null; each round print the current node via printCommit, then advance to firstParent, until no parent:

```java
while (commitID != null) {
    printCommit(currentCommit, commitID);
    commitID = currentCommit.firstParent;
    if (commitID != null) {
        currentCommit = Utils.readObject(
            Utils.join(".gitlet", "objects", "commits", commitID),
            Commit.class);
    }
}
```

## 4. Reuse and Reused By

- Utils methods called: message, readObject, join
- JDK API called: `new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US).format(timestamp)` formats time (d not zero-padded, English abbreviation, local timezone). Note the date pattern must be given to SimpleDateFormat, not String.format (the latter only recognizes % conversions and prints the pattern literally)
- Object interfaces called:
  - Commit: read fields message, timestamp, firstParent, secondParent
- Called by: printCommit called by this command and global-log; log itself only dispatched by Repository.run
- Newly extracted helper methods: `static void printCommit(Commit commit, String commitID)` — print one commit block in log format (including merge commit Merge: line and blank line after block), first extracted by this card (see 03 §6); also calls commit-card-extracted getCurrentCommitID(), getCurrentCommit()

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

spec requirement: runtime linear in number of commit nodes from current commit backtracking along first parent; memory not separately required.

Compliance analysis:

- runtime: advance along firstParent node by node; each node reads one object and prints a fixed number of lines, all constant relative to total history
- memory: loop holds only the current one Commit object and its ID; peak is a single commit object, does not grow with total history
