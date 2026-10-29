# `branch`

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void branch(String name)`

## 2. Dependent Data

- Branch files under refs/heads/
- Commit ID pointed by current branch pointer

## 3. Execution Steps

1. Construct branch file object → `File branchFile = Utils.join(".gitlet", "refs", "heads", name)`
2. Check same-name branch already exists `branchFile.exists()` → throw `Utils.error("A branch with that name already exists.")`
3. Get current commit ID → `String commitID = getCurrentCommitID()`
4. Create new branch → `Utils.writeContents(branchFile, commitID)`

## 4. Reuse and Reused By

- Utils methods called: join, writeContents, error
- Object interfaces called: none
- Called by: only dispatched by Repository.run; currently no other command reuses it
- Newly extracted helper methods: none. Calls extracted getCurrentCommitID() (extracted by commit card, see 03 §6)

## 5. State Modification and Failure Protection

Boundary: the only check finishes at step 2; step 3 only reads commit ID; first disk write at step 4.

Changes:

- working directory: no change
- staging area: no change
- `.gitlet`: no change
- branch pointer: changed (add one pointer pointing to current commit ID)
- HEAD: no change

Failure protection: the only business error is thrown at step 2 before disk write; zero change on failure.

## 6. Performance Check

spec requirement: runtime constant relative to any significant measure; memory not separately required.

Compliance analysis:

- runtime: exists() directly checks a single path, constant throughout
- memory: not separately required; peak is one File reference and one commit ID string, both fixed length
