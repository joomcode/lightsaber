# AI Agent Guidelines — Lightsaber

## Scope and compatibility

These instructions apply to the entire repository.

Lightsaber has two processing backends:

- `KOTLIN_COMPILER_PLUGIN` is the native Kotlin IR implementation used to remove the separate Android bytecode-transform cost.
- `BYTECODE` is the legacy ASM implementation and remains the compatibility and emergency-rollback backend.

The Kotlin compiler plugin supports Kotlin declarations only. Do not add partial Java-source support or claim Java parity. Preserve the bytecode backend for Java and rollback compatibility unless a task explicitly removes it.

The library currently defaults to `BYTECODE`; consumers may select KCP through `lightsaber.processing.mode`. Do not change the library default or remove the fallback as an incidental part of another change.

## Implementation map

- Compiler entry point and option parsing:
  `lightsaber/compiler-plugin/src/main/kotlin/com/joom/lightsaber/compiler/LightsaberCompilerPlugin.kt`
- IR discovery, validation, and generation:
  `lightsaber/compiler-plugin/src/main/kotlin/com/joom/lightsaber/compiler/LightsaberNativeIrGenerationExtension.kt`
- Android Gradle wiring:
  `lightsaber/gradle-plugin/src/main/java/com/joom/lightsaber/plugin/AndroidLightsaberPlugin.kt`
- JVM Gradle wiring:
  `lightsaber/gradle-plugin/src/main/java/com/joom/lightsaber/plugin/JavaLightsaberPlugin.kt`
- Compiler task inputs and graph fingerprinting:
  `lightsaber/gradle-plugin/src/main/java/com/joom/lightsaber/plugin/KotlinCompilerPluginOptions.kt`
- Post-compile validation:
  `lightsaber/gradle-plugin/src/main/java/com/joom/lightsaber/plugin/LightsaberCompilerValidationTask.kt`
- Shared bytecode validation implementation:
  `lightsaber/processor/src/main/java/com/joom/lightsaber/processor/LightsaberValidator.kt`
- Runtime ABI used by generated IR:
  `NativeProvider`, `NativeProvisioner`, and `NativeQualifier` under
  `lightsaber/core/src/main/java/com/joom/lightsaber/internal/`

Treat changes across the compiler plugin, Gradle plugin, and runtime helpers as one compatibility surface. Generated IR must match the runtime ABI shipped in the same release.

## Semantic parity

- Preserve observable behavior rather than byte-for-byte ASM output.
- Cover constructor, field, and method injection; visibility; nullability; generics; objects and companions; inheritance; contracts; imports; qualifiers; scopes; and eager dependencies.
- Keep diagnostics deterministic and attach them to useful source locations.
- Fail compilation for unsupported declarations instead of generating subtly incorrect code.
- Do not reintroduce ASM frame/max computation in the IR backend. JVM frame generation belongs to the Kotlin backend.

### Eager initialization order

The legacy processor accumulated injection targets in a `HashSet` keyed by ASM `Type`. Some applications came to observe that iteration order through eager singleton side effects.

`localClassesInLegacyInjectionOrder()` deliberately reproduces the legacy ASM hash and `HashSet` iteration semantics. Do not replace it with source order, file order, or alphabetical sorting without an explicit compatibility decision and corresponding migration plan. Keep the native smoke test assertion for eager order.

## Gradle integration

- In KCP mode, do not register or wire the legacy Transform/ScopedArtifact task. A disabled transform still adds configuration and artifact-pipeline overhead.
- Use `KotlinCompilerPluginSupportPlugin` and provider-backed configuration. Every value that can change generated output must be a stable compiler task input.
- Keep compiler plugin and Kotlin compiler versions aligned with `kotlinVersion` from `gradle/common.gradle`; do not upgrade Kotlin incidentally.
- Apply KCP only to intended Kotlin compilations and Android variants.
- KAPT stub generation is a separate compiler invocation with incomplete IR. It must be detected and skipped; only the real Kotlin compilation may emit Lightsaber runtime code.
- Avoid absolute checkout paths, timestamps, unordered iteration, and eagerly resolved classpaths in compiler options.

## Incremental compilation

Lightsaber generates an aggregated injector configurator. An incremental Kotlin invocation may otherwise compile only a subset of declarations and leave that aggregate stale.

The `graphFingerprint` compiler option is therefore a correctness input:

- Local binding declarations, annotations, signatures, and other DI graph structure must affect it.
- Executable body-only changes, comments, formatting, and unrelated declarations must not affect it.
- Relevant structural changes in project dependencies must invalidate downstream generation.
- Body-only changes in dependencies must not force downstream recompilation.
- Fingerprints must be deterministic, path-stable, and independent of source enumeration order.
- Do not replace the structural fingerprint with a hash of complete source files or the full compile/runtime classpath; that destroys local and cross-module incrementality.

Any change to fingerprint tokenization or dependency traversal requires tests for:

- local body-only edits;
- adding and removing a binding;
- unrelated source edits;
- upstream body-only edits;
- upstream ABI, annotation, or binding-graph edits;
- configuration-cache reuse.

## Validation behavior

Keep these legacy features compatible in KCP mode:

- `validateUsage`;
- `validateUnusedImports`;
- `validateUnusedImportsVerbose`;
- debug report generation.

Validations that require complete compiled project output belong in `LightsaberCompilerValidationTask`, not in an IR pass over broad classpaths. Keep the task cacheable, declare all flags and class collections as inputs, give it a stable output, and ensure it does not run after failed compilation or when no validation/report is requested.

Do not fork validation semantics between backends when the shared processor validator can be reused.

## Tests and fixtures

Functional test projects and incremental edit steps belong under:

- `lightsaber/src/functionalTest/resources/incremental/`
- `lightsaber/src/functionalTest/resources/validation/`

Do not embed complete Gradle projects or large Kotlin source files as strings in test code. Keep reusable projects in `project/` directories and mutations in `steps/`.

For KCP changes, run the same pipeline as CI:

```bash
./gradlew -p lightsaber check -Plightsaber.shadow.enabled=true
./gradlew -p lightsaber publishToMavenLocal -Plightsaber.shadow.enabled=true
./gradlew -p lightsaber functionalTest -Plightsaber.shadow.enabled=true
./gradlew check -Pdevelopment=false -Dorg.gradle.unsafe.configuration-cache=true --stacktrace
```

At minimum, preserve coverage in:

- `KotlinCompilerPluginIncrementalTest`;
- `KotlinCompilerPluginValidationTest`;
- `samples/native-ir-smoke`;
- existing processor integration and validation tests.

Port existing processor cases to the KCP path when adding semantic coverage; do not rely only on new happy-path smoke tests.

## Consumer and performance verification

- Publish experiments to Maven Local and test them in a real consumer with identical Gradle/JVM settings.
- Compare `BYTECODE` and `KOTLIN_COMPILER_PLUGIN` using clean, warm no-change, body-only, structural, and cross-module edits.
- Warm up after switching artifacts or processing modes, take multiple samples, and compare medians.
- Report processor-specific overhead separately from end-to-end build time.
- When a consumer test fails, reproduce it under BYTECODE before attributing it to KCP. An identical failure in both modes is environment/shared-test evidence, not a KCP regression.

## Change checklist

Before considering a KCP change complete, verify:

- KCP mode creates no legacy transform task.
- Clean and incremental JVM/Android fixtures pass.
- KAPT coexistence still works.
- Eager initialization order remains compatible.
- Local and cross-module invalidation remains selective and correct.
- Validation flags and diagnostics retain legacy behavior.
- Runtime helpers and generated IR remain ABI-compatible.
- Maven Local artifacts work in a representative consumer.
