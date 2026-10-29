# `rm-branch`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void rmBranch(String branchName)`

## 2. Dependent Data

- Branch files under refs/heads/
- Current branch name in HEAD

## 3. Execution Steps

1. Construct branch file object → `File branchFile = Utils.join(".gitlet", "refs", "heads", branchName)`
2. Check branch to delete does not exist `!branchFile.exists()` → throw `Utils.error("A branch with that name does not exist.")`
3. Get current branch name → `String currentBranchName = getCurrentBranchName()`
4. Check branch to delete is current branch `currentBranchName.equals(branchName)` → throw `Utils.error("Cannot remove the current branch.")`
5. Delete branch file → `branchFile.delete()`

Note: cannot use Utils.restrictedDelete. That method requires the deleted file's directory to contain `.gitlet` (see Utils.java), designed only to protect working directory files; branch pointers are internal files under `.gitlet/refs/heads/`, using it throws IllegalArgumentException at runtime. Internal files use native File.delete() directly.

## 4. Reuse and Reused By

- Utils methods called: join, error (branch deletion uses native File.delete(), not Utils)
- Object interfaces called: none
- Called by: only dispatched by Repository.run; currently no other command reuses it
- Newly extracted helper methods: none. Calls extracted getCurrentBranchName() (extracted by commit card, see 03 §6)

## 5. State Modification and Failure Protection

Boundary: two checks finish at steps 2 and 4; first disk write at step 5.

Changes:

- working directory: no change
- staging area: no change
- `.gitlet`: no change
- branch pointer: changed (delete one pointer)
- HEAD: no change

Failure protection: both business errors thrown before deletion; zero change on failure.

## 6. Performance Check

spec requirement: runtime constant relative to any significant measure; memory not separately required.

Compliance analysis:

- runtime: exists() directly checks a single path, delete single file, constant throughout
- memory: not separately required; peak is one File reference and one branch name string
