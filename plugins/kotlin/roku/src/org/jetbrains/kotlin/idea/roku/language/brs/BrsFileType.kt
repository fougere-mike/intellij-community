package org.jetbrains.kotlin.idea.roku.language.brs

import org.jetbrains.kotlin.idea.roku.RokuIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * File type for BrightScript (.brs) files.
 */
object BrsFileType : LanguageFileType(BrsLanguage) {

    override fun getName(): String = "BrightScript"

    override fun getDescription(): String = "BrightScript source file"

    override fun getDefaultExtension(): String = "brs"

    override fun getIcon(): Icon = RokuIcons.BRS_FILE
}
