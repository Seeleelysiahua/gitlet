# 00 Design Overview

> This file is the overview of the Gitlet design docs. The full index is in [README](README.md). The requirement doc answers "what to do"; this design doc set answers "how to do it".

## 1. System Introduction

Gitlet is a version control system written in Java. It supports saving the current project version, restoring old versions, developing on branches in parallel, and merging branch changes. The system stores Commits and Blobs by content address (same content shares one object and is not stored twice). All internal state is persisted in the `.gitlet` directory.

## 2. Design Goals

- All internal data of Gitlet (such as commits, blobs) must be stored in `.gitlet`.
- All Gitlet commands must meet the runtime and memory usage requirements in the requirement doc.
- All error messages printed by Gitlet must follow the spec format exactly. Keep case, spaces, punctuation, and the final period.
- When a Gitlet command fails, it must not modify, add, or delete any data, including working directory files and data inside `.gitlet`.
- Gitlet does not store duplicate data. If a file is the same in two commits, only one blob is stored. So after commit, the `.gitlet` increase should not exceed the total size of files staged in this commit.

## 3. Non-Goals

- Gitlet does not recursively handle subdirectories.
- A merge commit saved by Gitlet has at most two parents. It cannot have more than two.
- Gitlet does not handle error cases not listed in the spec.
- Gitlet's HEAD points to a branch, not to a commit ID.

## 4. Document Navigation

| Topic | Document |
| --- | --- |
| Class split and command dispatch | [01 Architecture](01-architecture.md) |
| Object fields, collection choices, SHA-1 rules | [02 Object Model](02-object-model.md) |
| `.gitlet` directory layout, serialization, abbreviated ID resolution | [03 Persistence and File Layout](03-persistence-and-layout.md) |
| Error messages and entry checks | [04 Error Handling](04-error-handling.md) |
| Local command implementation (13 commands) | [commands/](commands/) |
| Remote commands (bonus, 5 commands) | [remotes/](remotes/) |
| Test strategy, tradeoffs, and risks | [05 Testing and Tradeoffs](05-testing-and-tradeoffs.md) |

## 5. Global Conventions

- Terms follow the course requirement glossary. This doc set does not redefine them.
- Object field structure is defined in [02 Object Model](02-object-model.md). On-disk layout is defined in [03 Persistence and File Layout](03-persistence-and-layout.md). When a command card needs this info, it links to these two docs instead of repeating it. Otherwise the same content appears in many places, and it is easy to miss an update and make the docs contradict each other. For example, when `checkout.md` needs to explain commit fields, it only says "see [02 Object Model](02-object-model.md)".
