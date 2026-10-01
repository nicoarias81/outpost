# Pre-execution wrapper failure

The first Pixel admission attempt stopped while copying source evidence. PowerShell variable names are case-insensitive: the prior local `$target` path collided with the new validated `-Target` parameter. No instrumentation/model call started. Both APK installations succeeded. The fix renames the local destination to `$sourceTarget`; raw run metadata is preserved. This is a harness failure, not a phone/model failure.
