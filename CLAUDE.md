# RokuStudio - BrightScript Platform Support for IntelliJ

This fork of intellij-community adds first-class **BRS (BrightScript)** platform support to the bundled Kotlin IDE plugin, enabling development of Roku applications using Kotlin compiled to BrightScript.

## Why This Fork Exists

The standard IntelliJ Kotlin plugin has hardcoded `when` statements with `is` checks for known platforms (JVM, JS, Native, Wasm). When encountering the BRS platform from our forked Kotlin compiler, it throws:

```
Unsupported platform kind: BRS
```

The extension point system exists but the consuming code uses exhaustive `is` checks rather than polymorphic dispatch, making a plugin-only solution impossible.

## Related Projects

- **Forked Kotlin Compiler**: `../Kotlin` - Full BRS backend and platform support
- **Kotlin/Roku Gradle Plugin**: `../kotlin-roku` - Build tooling for Roku apps
- **Test Application**: `../roku-test-app` - Sample Roku application

## Changes Made

### 1. IdePlatformKindProjectStructure.kt

**File:** `plugins/kotlin/base/platforms/src/org/jetbrains/kotlin/idea/base/platforms/IdePlatformKindProjectStructure.kt`

Added BRS platform support to all `when` statements:

```kotlin
// Added imports
import org.jetbrains.kotlin.platform.brs.BrsPlatforms
import org.jetbrains.kotlin.platform.impl.BrsIdePlatformKind

// In getCompilerArguments():
is BrsIdePlatformKind -> null

// In getLibraryVersionProvider():
is WasmIdePlatformKind, is NativeIdePlatformKind, is BrsIdePlatformKind -> { _ -> null }

// In getLibraryPlatformKind():
file.isKlibLibraryRootForPlatform(BrsPlatforms.defaultBrsPlatform) -> BrsIdePlatformKind

// In getLibraryKind():
is BrsIdePlatformKind -> KotlinBrsLibraryKind
```

### 2. KotlinLibraryKind.kt

**File:** `plugins/kotlin/base/platforms/src/org/jetbrains/kotlin/idea/base/platforms/library/KotlinLibraryKind.kt`

Added BRS library kind for proper library classification in the Project view:

```kotlin
import org.jetbrains.kotlin.platform.brs.BrsPlatforms

object KotlinBrsLibraryKind : PersistentLibraryKind<DummyLibraryProperties>("kotlin.brs"), KotlinLibraryKind {
    override val compilerPlatform: TargetPlatform
        get() = BrsPlatforms.defaultBrsPlatform

    override fun createDefaultProperties(): DummyLibraryProperties {
        return DummyLibraryProperties.INSTANCE
    }
}
```

### 3. Library Definitions

Updated `.idea/libraries/` XML files to use the forked Kotlin version `2.2.255-SNAPSHOT`:

- `kotlinc_kotlin_compiler_common.xml` - Uses `kotlin-compiler-common-for-ide:2.2.255-SNAPSHOT`
- `kotlinc_kotlin_jps_common.xml` - Uses `kotlin-jps-common-for-ide:2.2.255-SNAPSHOT`

## Forked Kotlin Compiler Setup

The forked Kotlin compiler (`../Kotlin`) must be built and published to Maven local before building this IDE:

```bash
cd ../Kotlin

# Build IDE artifacts with BRS module included
./gradlew :prepare:ide-plugin-dependencies:kotlin-compiler-common-for-ide:jar \
          :prepare:ide-plugin-dependencies:kotlin-jps-common-for-ide:jar \
          -Ppublish.ide.plugin.dependencies=true \
          --no-configuration-cache

# Publish to Maven local
./gradlew publishToMavenLocal -Pversion=2.2.255-SNAPSHOT --no-configuration-cache
```

The forked Kotlin's `build.gradle.kts` was modified to include `:core:compiler.common.brightscript` in:
- `commonCompilerModules` array
- `kotlinJpsPluginEmbeddedDependencies` array

## Building RokuStudio

RokuStudio is based on intellij-community, which uses IntelliJ's internal build system rather than Gradle. Builds must be performed from within a standalone IntelliJ IDEA instance:

1. Open this project in IntelliJ IDEA
2. Use **Build > Build Project** to compile

**Note:** Ensure the forked Kotlin compiler artifacts have been published to Maven local (see "Forked Kotlin Compiler Setup" above) before building.

## Verification

After building, open `roku-test-app` in RokuStudio and verify:

1. No "Unsupported platform kind: BRS" errors in IDE log
2. Symbol resolution works in BRS source sets (go-to-definition on stdlib types)
3. Code completion shows BRS stdlib functions
4. No red squiggles on valid BRS code
5. Libraries show as "Kotlin/BRS" kind in Project view

## Branch

All changes are on the `brs-platform-support-new` branch, based on tag `idea/243.25659.59` (IntelliJ IDEA 2024.3.4).

This version was chosen for compatibility with the forked Kotlin compiler which is based on Kotlin 2.2.0. IntelliJ 2024.3.4 bundles Kotlin 2.1.20-ij243-60, making it API-compatible with our forked compiler's `2.2.255-SNAPSHOT` version.

## Maintenance Notes

When updating to newer IntelliJ versions:

1. Check the Kotlin version bundled in the target IntelliJ tag (look at `.idea/libraries/kotlinc_kotlin_compiler_common.xml`)
2. Ensure the bundled Kotlin major.minor version matches your forked Kotlin compiler
3. Rebase onto the new tag
4. Re-apply BRS cases to any modified `when` statements in `IdePlatformKindProjectStructure.kt`
5. Ensure `KotlinBrsLibraryKind` is still present in `KotlinLibraryKind.kt`
6. Update library definitions if needed
7. Rebuild forked Kotlin IDE artifacts if needed
