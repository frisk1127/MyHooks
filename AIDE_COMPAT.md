# AIDE Compatibility Guide

## Role Split
- Main repo: maintain source, structure, and git history.
- AIDE project: compile, package, and validate on-device.
- Logic should stay aligned across both, but build-chain adaptations may exist only in the AIDE project.

## Language Rules
- Prefer Java over Kotlin.
- Prefer conservative Java syntax.
- Avoid lambda, method references, and newer syntax unless already proven to compile in AIDE.
- Variables captured by anonymous inner classes should be declared `final`.

## Gradle Rules
- Prefer Groovy DSL (`build.gradle`) over Kotlin DSL (`build.gradle.kts`).
- Avoid complex Gradle features:
  - custom tasks
  - multi-module refactors unless necessary
  - annotation processing chains
  - advanced variant/flavor logic
- Keep the build graph simple and explicit.

## Dependency Rules
- Minimize third-party dependencies.
- If a dependency is unstable in AIDE, vendor it manually into `app/libs/`.
- For native dependencies, keep only the ABI actually used for packaging.
- Do not rely on AIDE to respect ABI filters perfectly; verify packaged outputs when needed.

## Project Layout Rules
- Logic source files may be reorganized when needed.
- Do not casually move these without updating all references:
  - `AndroidManifest.xml`
  - `res/`
  - `assets/`
  - `jniLibs/`
  - `xposed_init`
- Reflective entry points, manifest metadata, and hook registration points must stay synchronized.

## Hook Design Rules
- One hook per class.
- Register every hook through `HookRegistry`.
- Every hook should provide stable metadata:
  - key
  - title
  - description
  - target packages
- Prefer the narrowest hook point that works.
- Avoid global high-frequency hooks unless there is no cheaper alternative.
- Temporary diagnostics should be removed or reduced after validation.

## Performance Rules
- Avoid polling loops when a lifecycle or layout callback can do the same job.
- Avoid broad hooks such as global `Dialog.show()` unless strictly necessary.
- Prefer event-driven scans over repeated delayed scans.
- Keep normal-path logging silent; reserve logs for failures or temporary debugging.

## AIDE Sync Workflow
1. Implement or refactor in the main repo first.
2. Sync logic changes to the AIDE compile project.
3. Compile in AIDE and fix compatibility issues there.
4. Remove temporary probes and debug logs.
5. Keep the maintained source in the main repo clean.

## Practical Defaults For MyHooks
- Main repo is the maintained source of truth.
- AIDE project is the Android-side compile mirror.
- Java is the default implementation language.
- Groovy Gradle scripts are the default build script format.
- DexKit/native integrations should be kept as simple and explicit as possible.
