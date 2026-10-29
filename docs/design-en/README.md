# Gitlet Design Document

This directory contains pre-implementation finalized design docs: 6 shared design docs, 18 command cards, plus decision registry and interface list during design. Implementation code is in the repo root [`gitlet/`](../../gitlet/).

## How to Read

1. Read [00 Overview](00-overview.md) first to build the global picture, then read shared base in order 01 → 05;
2. When interested in how a command is implemented, check the corresponding card under [commands/](commands/); remote commands in [remotes/](remotes/);
3. Want to know "why this design", check [decisions.md](decisions.md); want cross-command method signatures, check [interfaces.md](interfaces.md).

## Shared Design

| Doc | Content |
| --- | --- |
| [00 Overview](00-overview.md) | System intro, design goals and non-goals, document navigation |
| [01 Architecture](01-architecture.md) | Class overview, dependency direction, entry check pipeline and command dispatch |
| [02 Object Model](02-object-model.md) | blob, Commit and StagingArea fields, invariants and ID computation |
| [03 Persistence and File Layout](03-persistence-and-layout.md) | `.gitlet` directory tree, serialization format, read/write rules and cross-command helper registry |
| [04 Error Handling](04-error-handling.md) | Verbatim error message list, unified reporting chain and exit behavior |
| [05 Testing and Tradeoffs](05-testing-and-tradeoffs.md) | Three-layer test strategy, coverage checklist, design tradeoffs and known risks |

## Local Command Cards (13)

Local cards have six fixed sections: Method signature / Dependent data / Execution steps / Reuse and reused by / State modification and failure protection / Performance check; remote cards have the first five sections (no performance check).

| Command | Card | One-line |
| --- | --- | --- |
| init | [commands/init.md](commands/init.md) | Create `.gitlet`, initial commit and master |
| add | [commands/add.md](commands/add.md) | Stage file; unstage if content equals current commit |
| commit | [commands/commit.md](commands/commit.md) | Commit staging area and advance current branch |
| rm | [commands/rm.md](commands/rm.md) | Unstage; if tracked mark removal and delete working file |
| log | [commands/log.md](commands/log.md) | Print current branch history along first parent |
| global-log | [commands/global-log.md](commands/global-log.md) | Print all commits in repo |
| find | [commands/find.md](commands/find.md) | Exact search by commit message |
| status | [commands/status.md](commands/status.md) | Five-section status report |
| checkout | [commands/checkout.md](commands/checkout.md) | Current file / historical file / switch branch three usages |
| branch | [commands/branch.md](commands/branch.md) | Create branch pointer |
| rm-branch | [commands/rm-branch.md](commands/rm-branch.md) | Delete branch pointer |
| reset | [commands/reset.md](commands/reset.md) | Roll back current branch and working directory |
| merge | [commands/merge.md](commands/merge.md) | Three-way merge: split point, decision table and conflict handling |

## Remote Command Cards (5)

| Command | Card | One-line |
| --- | --- | --- |
| add-remote | [remotes/add-remote.md](remotes/add-remote.md) | Register remote name and remote `.gitlet` path |
| rm-remote | [remotes/rm-remote.md](remotes/rm-remote.md) | Delete remote registration |
| fetch | [remotes/fetch.md](remotes/fetch.md) | Copy missing objects and update remote tracking branch |
| push | [remotes/push.md](remotes/push.md) | After fast-forward check, push to remote |
| pull | [remotes/pull.md](remotes/pull.md) | Command-level composition of fetch then merge |

## Reference

- [decisions.md](decisions.md): decision / reason / source index, easy to verify against original design
- [interfaces.md](interfaces.md): quick list before writing command cards, prevent redefining constructors or methods

## Notes

- This directory does not contain original course spec and requirement summary (copyright reason). `spec` in text means the course requirement doc; `(spec:665)` style is the line number referenced at the time.
- Docs written before implementation, keep design-stage original appearance; if individual detail differs from final code, code and tests take precedence.
- [05 Testing and Tradeoffs](05-testing-and-tradeoffs.md) section 2 keeps the design-stage coverage checklist (checkboxes unchecked); actually landed cases see [`testing/student_tests/`](../../testing/student_tests/).
- Verbatim error message list in [04 Error Handling](04-error-handling.md) takes precedence; literals in code match that list.
