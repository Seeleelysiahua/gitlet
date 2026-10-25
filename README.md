# Gitlet

用 Java 实现的轻量版本控制系统（类 Git 子集）：内容寻址存储 commit 与 blob，
支持提交历史、分支、合并与远程仓库。

## 功能

TODO: 实现完成后填写（init / add / commit / rm / log / global-log / find /
status / checkout / branch / rm-branch / reset / merge，及远程命令
add-remote / rm-remote / fetch / push / pull）。

## 构建与测试

```sh
make              # 编译
make check        # 运行集成测试
java gitlet.Main <command> [operands]   # 运行（在含 .gitlet 的目录下）
```

## 设计

TODO: 链接或摘录设计文档（对象模型、.gitlet 文件布局、错误处理约定）。

---

Based on UC Berkeley CS61B Project 2 (Gitlet). Skeleton code and testing
framework by P. N. Hilfinger and the CS61B staff.
