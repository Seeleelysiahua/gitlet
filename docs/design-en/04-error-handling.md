# 04 Error Handling

## 1. Error Message List

### Local

Text after the dash is the trigger condition; mechanism is not repeated. Abbreviated resolution is in [03 Persistence and File Layout](03-persistence-and-layout.md).

**General (entry checks)**

- `Please enter a command.` — no command input
- `No command with that name exists.` — command name does not exist
- `Incorrect operands.` — operand count or format is wrong
- `Not in an initialized Gitlet directory.` — current directory has no `.gitlet`

**init**

- `A Gitlet version-control system already exists in the current directory.` — current directory already has `.gitlet`

**add**

- `File does not exist.` — file to add does not exist

**commit**

- `No changes added to the commit.` — staging area is empty, no staged changes
- `Please enter a commit message.` — commit message is empty or whitespace-only

**rm**

- `No reason to remove the file.` — file is neither staged nor tracked by current commit

**find**

- `Found no commit with that message.` — no commit message equals the given message

**checkout**

- `File does not exist in that commit.` — file does not exist in the given commit
- `No commit with that id exists.` — no commit matches the ID
- `No such branch exists.` — branch does not exist
- `No need to checkout the current branch.` — target branch is the current branch
- `There is an untracked file in the way; delete it, or add and commit it first.` — an untracked file would be overwritten or deleted

**branch**

- `A branch with that name already exists.` — branch with same name already exists

**rm-branch**

- `A branch with that name does not exist.` — branch to delete does not exist
- `Cannot remove the current branch.` — cannot delete current branch

**reset**

- `No commit with that id exists.` — no commit matches the ID
- `There is an untracked file in the way; delete it, or add and commit it first.` — an untracked file would be overwritten or deleted

**merge**

- `You have uncommitted changes.` — staging area still has uncommitted changes
- `A branch with that name does not exist.` — branch to merge does not exist
- `Cannot merge a branch with itself.` — branch to merge is the current branch
- `There is an untracked file in the way; delete it, or add and commit it first.` — an untracked file would be overwritten or deleted
- `Given branch is an ancestor of the current branch.` — given branch is already an ancestor of current branch; no merge needed (normal notice)
- `Current branch fast-forwarded.` — current branch can fast-forward to given branch (normal notice)
- `Encountered a merge conflict.` — merge conflict; merge commit is still created, message printed after commit (normal notice)

Note: merge may also trigger `No changes added to the commit.` during auto-commit.

log, global-log, status have no failure cases.

### Remote

**add-remote**

- `A remote with that name already exists.` — remote with same name already exists

**rm-remote**

- `A remote with that name does not exist.` — remote to delete does not exist

**push**

- `Please pull down remote changes before pushing.` — remote branch head is not in local current commit history (cannot fast-forward)
- `Remote directory not found.` — remote `.gitlet` directory does not exist

**fetch**

- `That remote does not have that branch.` — remote repo does not have the given branch
- `Remote directory not found.` — remote `.gitlet` directory does not exist

**pull**

- No dedicated message: may trigger all fetch failure messages and all merge failure messages.

## 2. Check and Throw Mechanism

**Entry check pipeline**

Pipeline: before dispatch, check in fixed order; any failure throws and does not proceed.

1. Empty input → `Please enter a command.`
2. Unknown command → `No command with that name exists.`
3. Operand count/format → `Incorrect operands.`
4. Not initialized → `Not in an initialized Gitlet directory.`

- The first three steps do not depend on `.gitlet`; step 4 only checks whether the `.gitlet` directory exists. Only command internals access `.gitlet` contents.
- Step 3 defines the timing; specific rules are dispatched per command and only check the argument count and format.
- init may skip step 4.

**Entry layer vs semantic layer**

- The four-step pipeline only catches entry-shape problems.
- Arguments with correct form but invalid content (blank commit message, missing file/branch, empty staging area, unmatched abbreviated ID) are thrown by command methods during execution.
- Those requiring repo state or command-specific messages belong to the semantic layer.

**Unified reporting chain and ownership**

Whether from the entry pipeline or a command method, all failures use the same reporting chain; the only difference is where the exception is thrown:

```text
Command method / entry check, once failure is determined
│  throw Utils.error("one message from section 1 list")
│    · Utils.error creates GitletException with the message (unchecked)
│    · throw immediately interrupts current flow
▼
Exception propagates outward along the call chain
│  · Passes through any number of helper methods, no need to declare in signatures
│  · Skips all statements after the throw point (so later write steps do not execute)
▼
catch (GitletException e) in Repository.run catches it
│  · This is the only error printing exit in the whole program
│  · Utils.message(e.getMessage())   prints message verbatim (with newline)
│  · System.exit(0)                  exits immediately, argument fixed to 0
▼
Program terminates
```

The whole program prints error messages only in the catch block. All messages come from the section 1 list, not scattered elsewhere, ensuring verbatim consistency and no extra output.

The entry pipeline and try/catch are both in `Repository.run`. Main is very thin and only passes args to run; all command checks and dispatch are done in Repository (see [01 Architecture](01-architecture.md)).

## 3. Failure Atomicity

Failure atomicity requires that a command has only two outcomes: complete success, or as if it never ran. There is no half-done intermediate state. After failure, protected state must be byte-for-byte identical to before the command ran.

**Protected scope**

Five states are protected: working directory, staging area, `.gitlet`, branch pointer, HEAD. On command failure, none of these five may change. Read-only commands (log, global-log, status) write none of them and naturally satisfy atomicity.

**Unified execution order: check → in-memory computation → write to disk**

- Check phase only reads state and validates all business conditions that could throw GitletException.
- Computation phase computes the success result only in memory (objects, maps, temporaries).
- Write phase is last, after everything is confirmed.

Key discipline: any check that may throw GitletException must be before the first write. Once writing starts, there are no business failure points later; mid-way exceptions are structurally impossible.

**Multi-file write order**

When a command writes multiple files, they do not need to appear at the same time. Order by "does it affect visible state" (objects only add, state files overwrite whole, see [03 Persistence and File Layout](03-persistence-and-layout.md)):

1. First write new add-only objects (blob, commit). They use fresh IDs and no pointer references them, so writing them early does not change visible repo state.
2. Then move the branch pointer that represents version switch. Before the pointer moves, the repo's current commit is still old; only when the pointer changes does the version switch as a whole.
3. Finally do cleanup that depends on the new state, e.g. clear stage.

Step 3 must not be moved before step 2. If stage is cleared before the pointer moves, there is a window with "stage empty, branch pointer still at old commit": the add changes neither entered the new commit nor remain in staging, and cannot be recovered.

**Dangerous commands**

rm, checkout, reset, merge overwrite or delete working directory files and are most likely to leave half-done state, so their pre-checks must be the most complete.
