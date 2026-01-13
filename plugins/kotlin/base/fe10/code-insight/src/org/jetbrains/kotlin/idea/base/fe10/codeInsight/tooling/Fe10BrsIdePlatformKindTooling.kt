// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight.tooling

import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractBrsIdePlatformKindTooling
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.AbstractGenericTestIconProvider
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.Icon

class Fe10BrsIdePlatformKindTooling : AbstractBrsIdePlatformKindTooling() {
    override val testIconProvider: AbstractGenericTestIconProvider
        get() = object : AbstractGenericTestIconProvider() {
            override fun isKotlinTestDeclaration(declaration: KtNamedDeclaration): Boolean = false
        }

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? = null
}
