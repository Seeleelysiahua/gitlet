# `add-remote` (bonus)

> Object structure see [02 Object Model](../02-object-model.md), disk I/O see [03 Persistence and File Layout](../03-persistence-and-layout.md), error messages and throw mechanism see [04 Error Handling](../04-error-handling.md).

## 1. Method Signature

`static void addRemote(String remoteName, String remotePath)`

## 2. Dependent Data

- `.gitlet/remotes` single state file: map from remote name to remote `.gitlet` directory path (init does not create this file; missing = empty map)
- Both operands are pure literals (remote name and path string), not listed as dependent data

## 3. Execution Steps

1. Read remote map → `HashMap<String, String> remotes = readRemotes()`
2. Check remote name already exists `remotes.containsKey(remoteName)` → throw `Utils.error("A remote with that name already exists.")`
3. Convert path separators and add map entry → `remotes.put(remoteName, remotePath.replace("/", File.separator))`
4. Overwrite remotes whole → `Utils.writeObject(Utils.join(".gitlet", "remotes"), remotes)`

### readRemotes

`static HashMap<String, String> readRemotes()`: read `.gitlet/remotes` whole deserialize; if file missing return empty map:

```java
static HashMap<String, String> readRemotes() {
    File remotesFile = Utils.join(".gitlet", "remotes");
    return remotesFile.exists()
        ? Utils.readObject(remotesFile, HashMap.class)
        : new HashMap<>();
}
```

## 4. Reuse and Reused By

- Utils methods called: error, join, writeObject
- Object interfaces called: none
- Called by: only dispatched by Repository.run; currently no other command reuses it
- Newly extracted helper methods: `static HashMap<String, String> readRemotes()` — read `.gitlet/remotes`, missing returns empty map; extracted when the rm-remote card triggered it (same logic second card), belongs to this card, body see §3 "readRemotes" (see 03 §6)

## 5. State Modification and Failure Protection

Boundary: only business check at step 2, first disk write at step 4.

Changes:

- working directory: no change
- staging area: no change
- `.gitlet`: changed (remotes adds one map entry; first run creates the file)
- branch pointer: no change
- HEAD: no change

Failure protection: only business failure (duplicate name) before the only write step, zero change on failure; single-step whole overwrite, no multi-step write, no crash window.
