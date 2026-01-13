// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinBrsLibraryKind
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.platform.impl.BrsIdePlatformKind
import org.jetbrains.kotlin.psi.KtFunction

abstract class AbstractBrsIdePlatformKindTooling : IdePlatformKindTooling() {
    override val kind: BrsIdePlatformKind = BrsIdePlatformKind

    override val mavenLibraryIds: List<String> get() = emptyList()
    override val gradlePluginId: String get() = "com.example.kotlin-roku"
    override val gradlePlatformIds: List<KotlinPlatform> get() = listOf(KotlinPlatform.BRS)

    override val libraryKind: PersistentLibraryKind<*> get() = KotlinBrsLibraryKind

    override fun acceptsAsEntryPoint(function: KtFunction): Boolean = false
}
