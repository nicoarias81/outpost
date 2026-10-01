# Pre-execution screen precondition

This early harness revision required an unlocked keyguard before every foreground run. It stopped before numeric execution and restored the original private app directories. Later revisions use an isolated status-only Activity with Android's standard show-when-locked API and verify actual resume/focus/interactive state; they do not disable authentication or load personal UI state. Preserve this record as the original precondition stop.
