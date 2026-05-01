# MyHooks Agent Handoff

This document is the fast-start handoff for any agent taking over work on `MyHooks`.
It complements `AIDE_COMPAT.md` and should be read first.

See also:
- `AIDE_COMPAT.md`

## Project Identity
- Project name: `MyHooks`
- Type: standalone Xposed/LSPosed hook container for multiple apps
- Primary maintenance repo: `/data/user/0/com.termux/files/home/projects/android/MyHooks`
- AIDE compile project: `/storage/emulated/0/Projects/android/MyHooks`

## Source Of Truth
- The main repo is the maintained source of truth.
- The AIDE project exists so the user can compile on Android with AIDE.
- Logic changes usually need to be synced to both places.
- Build-chain and packaging adaptations should be kept in the AIDE project unless there is a strong reason to mirror them back.

## Mandatory Reading Order
1. Read this file.
2. Read `AIDE_COMPAT.md`.
3. Inspect current hook registry and entry points.
4. Only then start changing code.

## Current Architectural Rules
- Language default: Java.
- Build script default: Groovy Gradle DSL.
- Avoid Kotlin unless there is a concrete reason and AIDE compatibility is verified.
- Keep implementation conservative and AIDE-friendly.
- Do not assume desktop Gradle behavior matches AIDE behavior.

## Key Paths In Main Repo
- Hook entry: `/data/user/0/com.termux/files/home/projects/android/MyHooks/app/src/main/java/moe/frisk/myhooks/HookEntry.java`
- Hook registry: `/data/user/0/com.termux/files/home/projects/android/MyHooks/app/src/main/java/moe/frisk/myhooks/HookRegistry.java`
- Hook interface: `/data/user/0/com.termux/files/home/projects/android/MyHooks/app/src/main/java/moe/frisk/myhooks/AppHook.java`
- DexKit host: `/data/user/0/com.termux/files/home/projects/android/MyHooks/app/src/main/java/moe/frisk/myhooks/dexkit/DexKitHost.java`
- DexKit locator interface: `/data/user/0/com.termux/files/home/projects/android/MyHooks/app/src/main/java/moe/frisk/myhooks/dexkit/DexKitMethodLocator.java`

## Key Paths In AIDE Project
- Hook init: `/storage/emulated/0/Projects/android/MyHooks/app/src/main/java/moe/frisk/myhooks/HookInit.java`
- Hook registry: `/storage/emulated/0/Projects/android/MyHooks/app/src/main/java/moe/frisk/myhooks/HookRegistry.java`
- Hook interface: `/storage/emulated/0/Projects/android/MyHooks/app/src/main/java/moe/frisk/myhooks/AppHook.java`
- Hook preferences: `/storage/emulated/0/Projects/android/MyHooks/app/src/main/java/moe/frisk/myhooks/HookPreferences.java`
- UI entry: `/storage/emulated/0/Projects/android/MyHooks/app/src/main/java/moe/frisk/myhooks/MainActivity.java`

## How Hooks Are Organized
Each hook should be an independent class.

Main repo hook contract:
- implements `AppHook`
- exposes:
  - `getKey()`
  - `getTargetPackages()`
  - `onPackageLoaded(...)`

AIDE project hook contract:
- implements `AppHook`
- exposes:
  - `getKey()`
  - `getTitle()`
  - `getDescription()`
  - `getTargetPackages()`
  - `onPackageLoaded(...)`

Every new hook must be added to:
- main repo `HookRegistry.java`
- AIDE project `HookRegistry.java`

If the hook should be user-switchable in AIDE, it must work with the existing preferences/UI flow.

## Current Product Rules
- Main repo is for clean maintenance and commits.
- AIDE project is for practical Android-side compilation.
- Prefer minimal hook points.
- Avoid broad global hooks if a narrower lifecycle or view hook can work.
- Temporary diagnostics should be removed after verification.
- Normal-path success logging should be minimal or silent.

## Xposed Scope Rules
If a hook targets a new app package, scope must be updated where needed.

Typical places:
- main repo manifest metadata resources if present
- AIDE project `app/src/main/res/values/arrays.xml`

Do not assume a new target package will be injected automatically.

## DexKit Rules
- DexKit is integrated in `MyHooks` as a reusable helper layer.
- It is not copied from QAuxiliary internals; it is an independent integration using the DexKit library.
- Use DexKit when host internals are unstable enough that simple class-name reflection is too brittle.
- Do not reach for DexKit by default if a stable public/Android-side hook point already exists.

## AIDE-Specific Constraints
These are non-negotiable unless revalidated:
- Prefer Java over Kotlin.
- Prefer conservative syntax over newer Java features.
- Anonymous inner class captures should be `final` when needed.
- Do not rely on AIDE to package ABIs exactly like desktop Gradle.
- Keep dependencies simple and explicit.
- If a dependency is problematic in AIDE, vendor jars/so files manually in the AIDE project.

For more detail, read `AIDE_COMPAT.md`.

## Sync Workflow
When adding or changing a hook, follow this order:
1. Change main repo logic first.
2. Mirror the logic change into the AIDE project.
3. Adjust AIDE-specific compile/build details only in the AIDE project when possible.
4. Compile and validate in AIDE.
5. Remove temporary debug logging or probes.
6. Keep the main repo clean.

## Existing Hook Categories
At minimum, assume the project already contains hooks across multiple app targets, including:
- QQ/TIM-related hooks
- Bilibili-related hooks

Before editing, inspect current registry rather than assuming exact inventory from this document.

## Known Working Mental Model
Think of the project as:
- one lightweight Xposed container
- many app-specific hooks
- one maintained source repo
- one Android-side compile mirror

Do not treat the AIDE project as the long-term canonical source unless the user explicitly changes that rule.

## What Not To Do
- Do not restructure randomly just because desktop Gradle would tolerate it.
- Do not add noisy permanent debug logs.
- Do not leave one repo updated and the other stale when the logic is meant to stay aligned.
- Do not move manifest/resources/assets/jni-related files casually.
- Do not assume compatibility without testing in AIDE if the code path is intended for the Android compile project.

## If You Are Unsure
If there is ambiguity, default to these assumptions:
- main repo is canonical
- AIDE project is compile mirror
- Java is preferred
- Groovy Gradle is preferred
- narrower hooks are preferred
- lower runtime overhead is preferred

## Handoff Checklist
Before ending your turn, verify mentally that you know:
- where the canonical repo is
- where the AIDE compile repo is
- which files register hooks
- whether scope must be updated
- whether the change must be mirrored into AIDE
- whether temporary debug code should be removed

If any of those is unclear, you are not ready to hand off yet.
