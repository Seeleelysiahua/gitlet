# `rm-remote` (bonus)

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void rmRemote(String remoteName)`

## 2. Dependent Data

- `.gitlet/remotes` single state file: map from remote name to remote `.gitlet` directory path (missing = empty map)
- Operand remote name is pure literal, not listed as dependent data

## 3. Execution Steps

1. Read remote map → `HashMap<String, String> remotes = readRemotes()`
2. Check remote name does not exist `!remotes.containsKey(remoteName)` → throw `Utils.error("A remote with that name does not exist.")`
3. Delete that map entry → `remotes.remove(remoteName)`
4. Overwrite remotes whole → `Utils.writeObject(Utils.join(".gitlet", "remotes"), remotes)`

## 4. Reuse and Reused By

- Utils methods called: error, join, writeObject (Utils inside helper not repeated)
- Object interfaces called: none (remotes is a bare HashMap<String, String> serialized whole, no object class)
- Called by: only dispatched by Repository.run; currently no other command reuses it
- Newly extracted helper methods: none. Calls readRemotes() extracted by the add-remote card (see 03 §6)

## 5. State Modification and Failure Protection

Boundary: only business check at step 2, first disk write at step 4.

Changes:

- working directory: no change
- staging area: no change
- `.gitlet`: changed (remotes deletes one map entry)
- branch pointer: no change
- HEAD: no change

Failure protection: only business failure (name does not exist) before the only write step, zero change on failure; single-step whole overwrite, no multi-step write, no crash window.
