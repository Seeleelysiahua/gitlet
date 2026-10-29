# Gitlet

A lightweight version control system implemented from scratch in Java — UC Berkeley CS61B Project 2

[![Java](https://img.shields.io/badge/Java-26-orange)](https://openjdk.org/)
[![tests](https://img.shields.io/badge/tests-54%20passed-brightgreen)](https://img.shields.io/badge/tests-54%20passed-brightgreen)
[![commands](https://img.shields.io/badge/commands-18%2F18-blue)](https://img.shields.io/badge/commands-18%2F18-blue)

English | [简体中文](README.md)

Gitlet is a subset of Git: content-addressable storage, immutable commit
snapshots, branches, three-way merge, and remote repository sync. All internal
state is persisted in the .gitlet directory; objects with the same content are
stored only once across the whole repo.

Repository: <https://github.com/Seeleelysiahua/gitlet>

Course project: UC Berkeley CS61B, Project 2 (Gitlet)

Development approach: first complete design docs covering the object model,
disk layout, error handling, and all 18 commands; then implement command by
command per the design with integration tests

Design docs: see [docs/design-en/README.md](docs/design-en/README.md) (6 shared design docs + 18 command cards; Chinese original in [docs/design/](docs/design/))

For self-study record only. Please do not copy.

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [Command Reference](#command-reference)
- [Design and Implementation](#design-and-implementation)
- [Testing](#testing)
- [Project Structure](#project-structure)
- [Development Process](#development-process)
- [Notes and Limitations](#notes-and-limitations)
- [Acknowledgements](#acknowledgements)

## Features

- Complete local workflow: init / add / commit / rm / log /
  global-log / find / status / checkout / branch / rm-branch /
  reset / merge, 13 local commands total
- Remote repositories: add-remote / rm-remote / fetch / push / pull,
  syncing objects and branch pointers between two .gitlet directories via
  filesystem paths
- Content-addressable storage: blobs and commits are named by SHA-1; same
  content gives same object
- Immutable snapshots: commits store full file snapshots, not diffs;
  immutable after construction; objects under objects/ are add-only, never
  modified or deleted
- Three-way merge: deepest common ancestor as split point; supports
  fast-forward, ancestor short-circuit, auto merge commit, and conflict markers
- Abbreviated commit ID: checkout and reset accept hex prefixes; safely
  errors on ambiguous or missing prefixes
- Failure atomicity: write commands follow "check → in-memory compute →
  write to disk"; any business failure happens before the first disk write;
  working directory and .gitlet remain unchanged
- Verbatim error output: only one error printing exit in the whole program;
  messages match design docs verbatim

## Quick Start

### Requirements

| Dependency | Version | Purpose |
| --- | --- | --- |
| JDK | 14+ (verified on 26.0.1 in this repo) | Compile and run |
| Python | 3 (tested with 3.9.6) | Run integration tests |
| GNU Make | any | Build and test entry |

### Clone and Build

```sh
git clone git@github.com:Seeleelysiahua/gitlet.git
cd gitlet
make            # compile gitlet package with -Xlint:unchecked -Xlint:deprecation
```

Build outputs (gitlet/*.class, gitlet/sentinel) are generated in gitlet/
and already ignored by .gitignore. `make clean` removes them.

### Run

Classes are in the gitlet package; the JVM needs the repo root as classpath
root. Recommended: from any playground directory, use -cp pointing to the repo:

```sh
mkdir -p ~/playground/gitlet-demo && cd ~/playground/gitlet-demo
java -cp /absolute/path/to/gitlet gitlet.Main init
```

Or set an alias and use gitlet directly:

```sh
alias gitlet='java -cp /absolute/path/to/gitlet gitlet.Main'
gitlet init
```

Two notes:

1. Do not `cd gitlet` first and then run `java gitlet.Main`; then classpath root
   is gitlet/, and you get `Could not find or load main class gitlet.Main`.
2. Do not run init in the repo root, or .gitlet will mix into the source tree.

### A Minimal Session

```console
$ gitlet init
$ echo "hello wug" > wug.txt
$ gitlet add wug.txt
$ gitlet commit "add wug"
$ gitlet branch dev
$ gitlet checkout dev
$ echo "dev only" > dev.txt
$ gitlet add dev.txt
$ gitlet commit "dev work"
$ gitlet checkout master
$ echo "master only" > master.txt
$ gitlet add master.txt
$ gitlet commit "master work"
$ gitlet merge dev
$ gitlet log
===
commit 4e3da568b73b76cdb0a231de6241222979b3468d
Merge: e401dbc 7369b74
Date: Thu Oct 29 09:50:50 2026 +0800
Merged dev into master.

===
commit e401dbc98c532c589024ee07c7ad673db4bb1849
Date: Thu Oct 29 09:50:50 2026 +0800
master work
...
```

Commit IDs and times in output vary per run; the format matches real output.

## Command Reference

### Local Commands (13)

| Command | Usage | Behavior |
| --- | --- | --- |
| init | `init` | Create .gitlet in current directory; set up initial commit, master branch, empty staging area |
| add | `add <file>` | Stage current file version; if content equals current commit, unstage |
| commit | `commit <message>` | Commit staging area as new snapshot and advance current branch pointer |
| rm | `rm <file>` | Unstage; if file tracked by current commit, also mark removal and delete working file |
| log | `log` | Print current branch history along first parent |
| global-log | `global-log` | Print all commits in repo, not limited to current branch |
| find | `find <message>` | Exact search by commit message; print one full matching commit ID per line |
| status | `status` | Five-section format: branches, staged, removed, unstaged modifications, untracked files |
| checkout | `checkout -- <file>` | Overwrite working file with version from current commit |
| checkout | `checkout <id> -- <file>` | Overwrite working file with version from given commit (abbreviated ID allowed) |
| checkout | `checkout <branch>` | Switch branch: replace working directory, move HEAD, clear staging area |
| branch | `branch <name>` | Create branch pointer pointing to current commit |
| rm-branch | `rm-branch <name>` | Delete branch pointer, delete no commit |
| reset | `reset <id>` | Roll current branch and working directory back to given commit (abbreviated ID allowed) |
| merge | `merge <branch>` | Three-way merge; on no conflict auto-commit `Merged <branch> into <current>.`; on conflict write conflict markers and report |

### Remote Commands (5)

Remote names map to another local .gitlet directory via paths; no network protocol involved.

| Command | Usage | Behavior |
| --- | --- | --- |
| add-remote | `add-remote <name> <path>` | Associate remote name with remote .gitlet directory path |
| rm-remote | `rm-remote <name>` | Delete remote association |
| fetch | `fetch <name> <branch>` | Copy missing commits/blobs and update refs/remotes/<name>/<branch> |
| push | `push <name> <branch>` | After fast-forward check, copy objects to remote and update remote branch pointer (create if remote lacks branch) |
| pull | `pull <name> <branch>` | Equivalent to fetch <name> <branch> then merge <name>/<branch> |

All failures go through the unified exit, print the verbatim error message, then exit
with code 0; successful commands produce no extra output.

## Design and Implementation

Full design docs are in [docs/design-en/README.md](docs/design-en/README.md): 6 shared
design docs (object model, persistence, error handling, test strategy, etc.), 18 command
cards, key decision registry, and object interface list. Below only excerpts the
implementation skeleton and key mechanisms.

### Class Structure

Dependency strictly top-down, no cycles. Main is very thin; the entry check pipeline
(empty input → unknown command → operand count → initialized or not) and command
dispatch are centralized in Repository.run.

### .gitlet Directory Layout

```text
.gitlet/
├── objects/
│   ├── commits/          # commit objects (Java serialization), filename 40-char ID
│   └── blobs/            # blob raw bytes, filename 40-char ID
├── refs/
│   ├── heads/            # local branches: branch name → commit ID (single-line text)
│   └── remotes/<name>/   # remote tracking branches: branch name → commit ID
├── HEAD                  # current branch name (HEAD only refers to branch, not directly commit)
├── stage                 # staging area (serialized: addition map + removal set)
└── remotes               # remote map (serialized HashMap: remote name → remote .gitlet path)
```

### Key Mechanisms

- Commit ID: feed message, timestamp, parent commit IDs and map pairs to SHA-1
  in fixed order; map pairs sorted by filename first, so insertion order does not
  affect the ID; blob ID is SHA-1 of file bytes directly. blob and commit are stored in
  separate dirs, hash spaces do not interfere.
- Write ordering: write add-only new objects first (unreferenced, invisible
  externally) → then move the branch pointer (the only step that switches version) →
  finally clear the staging area. Any check that may throw must finish before the
  first disk write.
- Unified reporting chain: all failures throw Utils.error(...), propagate to
  the only catch in run, print message and exit; Utils.message is the only
  normal output channel.
- merge decision table: based on "same blob ID = same content", only compare
  blob IDs of split point, current commit, and given commit to cover add / modify
  / delete / both same change / conflict; split point is the deepest common ancestor.
- Reuse boundary: extract a helper only when the same logic appears a second time,
  e.g. makeCommit (commit and merge share), replaceWorkingDir (checkout and
  reset share), copyMissingObjects (fetch and push opposite directions share).

## Testing

Tests use the course-provided testing/tester.py: each .in case runs a real
java gitlet.Main command sequence in a temp dir, comparing stdout and filesystem
state verbatim (or with volatile outputs replaced by regexes in definitions.inc).

```sh
make check     # compile and run all integration tests
```

Measured result (macOS + JDK 26.0.1 + Python 3.9.6):

```text
Ran 54 tests. All passed.
```

`make clean && make` compiles with zero warnings; then `make check` all pass.

- 4 official samples: testing/samples/test01–04
- 50 own cases: testing/student_tests/test10–59

| Command | Case | Command | Case |
| --- | --- | --- | --- |
| init | test10 | reset | test19 |
| add | test11 | status | test20–21 |
| commit | test12 | merge | test22–35 |
| log | test13 | add-remote / rm-remote | test36–39 |
| global-log | test14 | fetch | test40–45 |
| find | test15 | push | test46–53 |
| rm | test16 | pull | test54–59 |
| branch / rm-branch | test17 | checkout | test18 |

Own cases cover each command's normal path, each failure message verbatim, state
invariance after failure, and remote multi-repo scenarios (push non-fast-forward
rejection, failure atomicity, pull conflict, etc.). Expected outputs are
hand-written against the spec, not generated from the program's own output.

Run a single case:

```sh
cd testing
python3 tester.py --verbose student_tests/test32-merge-conflict-both.in
```

`--keep` keeps the temp dir to inspect internal state; `--tolerance=0` disables
output tolerance. Run `make` first to generate gitlet/Main.class.

## Project Structure

```text
.
├── Makefile              # top-level entry: make / make check / make clean
├── pom.xml               # course Maven config (depends on course local parent POM, see "Notes and Limitations")
├── README.md             # Chinese README (default)
├── README.en.md          # English README (this file)
├── docs/design/          # design docs (Chinese original)
├── docs/design-en/       # design docs (English version)
├── gitlet/
│   ├── Makefile          # compile .java in this dir and generate sentinel
│   ├── Main.java         # entry, only calls Repository.run
│   ├── Repository.java   # entry pipeline, 18 commands and helpers (~1200 lines)
│   ├── Commit.java       # immutable commit object (five fields, does not compute own ID)
│   ├── StagingArea.java  # staging area (TreeMap addition + TreeSet removal)
│   ├── Utils.java        # course utility: SHA-1, serialization, file I/O
│   ├── GitletException.java
│   └── Dumpable.java / DumpObj.java   # course-provided object debugging tools
└── testing/
    ├── Makefile
    ├── tester.py         # integration test driver
    ├── runner.py         # course test runner
    ├── staff-runner.py   # course grading script
    ├── samples/          # test01–04 official samples
    ├── student_tests/    # test10–59 own cases
    └── src/              # test material files
```

## Development Process

2026-10-25 to 2026-10-29, 20 commits total

1. First build data classes (Commit, StagingArea) and the entry pipeline skeleton,
   then implement command by command in dependency order: init → add → commit
   → history queries → rm → branches → checkout → reset → status → merge
   → remote commands
2. Each command (or each pair of strongly related commands, e.g. branch/rm-branch)
   is committed together with its test cases, using Conventional Commits prefixes

## Notes and Limitations

- pom.xml cannot be used standalone: it inherits the course environment's
  library-sp21 parent POM and cannot resolve outside that environment; build and
  test always use the Makefile.
- Design docs: [docs/design-en/](docs/design-en/) contains 6 shared design
  docs, 18 command cards, key decision registry, and object interface list (English);
  the Chinese original is in [docs/design/](docs/design/); the course
  original spec and requirement summary are not included due to copyright; `spec` in
  text means the course requirement doc.
- Remote limited to local paths: add-remote records a local filesystem path;
  no SSH/HTTP protocol, no concurrency or permission control.
- Course academic policy: this project is a self-study work, for self-study
  record only; please do not copy; follow your course's academic integrity policy
  and do not submit others' code directly.

## Acknowledgements

Course and official skeleton, test framework: UC Berkeley CS61B (Josh Hug and
course staff). Utils.java, GitletException.java, Dumpable.java, DumpObj.java
in gitlet/, and tester.py, runner.py, staff-runner.py in testing/ all
come from the course-provided skeleton.

Design docs and command implementation are primarily my own work; AI tools
participated in doc organization and review.
