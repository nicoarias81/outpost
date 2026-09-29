# Contributing to Outpost

Start with the [developer guide](docs/development.md), [architecture](docs/architecture.md), [decision register](docs/decisions.md), and [roadmap](docs/roadmap.md).

Use English for maintained code comments, documentation, user-facing default resources, and new primary fixtures. Preserve original languages in imported content, explicit multilingual tests, and archived experimental output.

Keep model execution inside the Android emulator until the project owner expands the execution scope. Select kernels by real CPU/OS capability and compiled support, and retain a tested reference fallback. Pin dependencies and models; changes to backend internals require appropriate numerical and lifecycle checks.

Use focused tests for the behavior being changed. Record input/model/build identity, output, timing scope, and limitations. Preserve failed experiments and distinguish execution success from reviewed task quality. Avoid rerunning every model suite for a documentation-only edit.

Keep weights, AVD disks, SDKs, private documents, signing material, and machine paths out of source control. Configure the developer environment through ignored local settings. Before staging evidence, review it for private data and preserve the release record instead of overwriting it.

The project currently has no configured remote or selected code license. This local repository does not itself authorize public redistribution or upstream submissions. Publication and licensing are separate project decisions.
