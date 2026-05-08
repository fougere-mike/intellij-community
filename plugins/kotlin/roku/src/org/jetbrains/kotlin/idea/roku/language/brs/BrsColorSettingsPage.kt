package org.jetbrains.kotlin.idea.roku.language.brs

import org.jetbrains.kotlin.idea.roku.RokuIcons
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

/**
 * Color settings page for BrightScript syntax highlighting customization.
 */
class BrsColorSettingsPage : ColorSettingsPage {

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keyword", BrsSyntaxHighlighter.KEYWORD),
            AttributesDescriptor("Type", BrsSyntaxHighlighter.TYPE),
            AttributesDescriptor("Number", BrsSyntaxHighlighter.NUMBER),
            AttributesDescriptor("String", BrsSyntaxHighlighter.STRING),
            AttributesDescriptor("Comment", BrsSyntaxHighlighter.COMMENT),
            AttributesDescriptor("Operator", BrsSyntaxHighlighter.OPERATOR),
            AttributesDescriptor("Braces", BrsSyntaxHighlighter.BRACES),
            AttributesDescriptor("Brackets", BrsSyntaxHighlighter.BRACKETS),
            AttributesDescriptor("Parentheses", BrsSyntaxHighlighter.PARENTHESES),
            AttributesDescriptor("Identifier", BrsSyntaxHighlighter.IDENTIFIER),
            AttributesDescriptor("Constant (true/false/invalid)", BrsSyntaxHighlighter.CONSTANT),
            AttributesDescriptor("Bad character", BrsSyntaxHighlighter.BAD_CHARACTER)
        )

        private val DEMO_TEXT = """
' BrightScript example
' This is a comment

sub Main()
    print "Hello, Roku!"

    ' Variables
    myNumber = 42
    myFloat = 3.14
    myString = "BrightScript"
    myBool = true
    myInvalid = invalid

    ' Control flow
    if myNumber > 0 then
        print "Positive"
    else if myNumber < 0 then
        print "Negative"
    else
        print "Zero"
    end if

    ' Loop
    for i = 0 to 10 step 2
        print i
    end for

    ' Array
    myArray = [1, 2, 3]

    ' Associative array
    myAA = {
        name: "Roku",
        version: 12.5
    }
end sub

function Add(a as integer, b as integer) as integer
    return a + b
end function
        """.trimIndent()
    }

    override fun getIcon(): Icon = RokuIcons.BRS_FILE

    override fun getHighlighter(): SyntaxHighlighter = BrsSyntaxHighlighter()

    override fun getDemoText(): String = DEMO_TEXT

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "BrightScript"
}
