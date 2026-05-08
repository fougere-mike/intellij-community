package org.jetbrains.kotlin.idea.roku.language.brs

import com.intellij.lang.Language

/**
 * BrightScript language definition.
 *
 * BrightScript is a case-insensitive scripting language used for Roku development.
 */
object BrsLanguage : Language("BrightScript") {

    override fun isCaseSensitive(): Boolean = false

    override fun getDisplayName(): String = "BrightScript"
}
