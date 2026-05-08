package org.jetbrains.kotlin.idea.roku.language.brs

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

/**
 * Syntax highlighter for BrightScript files.
 */
class BrsSyntaxHighlighter : SyntaxHighlighterBase() {

    companion object {
        // Text attribute keys
        val KEYWORD = createTextAttributesKey("BRS_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val TYPE = createTextAttributesKey("BRS_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME)
        val NUMBER = createTextAttributesKey("BRS_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val STRING = createTextAttributesKey("BRS_STRING", DefaultLanguageHighlighterColors.STRING)
        val COMMENT = createTextAttributesKey("BRS_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val OPERATOR = createTextAttributesKey("BRS_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val BRACES = createTextAttributesKey("BRS_BRACES", DefaultLanguageHighlighterColors.BRACES)
        val BRACKETS = createTextAttributesKey("BRS_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
        val PARENTHESES = createTextAttributesKey("BRS_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
        val IDENTIFIER = createTextAttributesKey("BRS_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val CONSTANT = createTextAttributesKey("BRS_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT)
        val BAD_CHARACTER = createTextAttributesKey("BRS_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        // Arrays for quick lookup
        private val KEYWORD_KEYS = arrayOf(KEYWORD)
        private val TYPE_KEYS = arrayOf(TYPE)
        private val NUMBER_KEYS = arrayOf(NUMBER)
        private val STRING_KEYS = arrayOf(STRING)
        private val COMMENT_KEYS = arrayOf(COMMENT)
        private val OPERATOR_KEYS = arrayOf(OPERATOR)
        private val BRACES_KEYS = arrayOf(BRACES)
        private val BRACKETS_KEYS = arrayOf(BRACKETS)
        private val PARENTHESES_KEYS = arrayOf(PARENTHESES)
        private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
        private val CONSTANT_KEYS = arrayOf(CONSTANT)
        private val BAD_CHARACTER_KEYS = arrayOf(BAD_CHARACTER)
        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }

    override fun getHighlightingLexer(): Lexer = BrsLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when {
            // Keywords
            tokenType in BrsTokenTypes.KEYWORDS -> KEYWORD_KEYS

            // Types
            tokenType in BrsTokenTypes.TYPE_KEYWORDS -> TYPE_KEYS

            // Literals
            tokenType == BrsTokenTypes.INTEGER_LITERAL ||
            tokenType == BrsTokenTypes.FLOAT_LITERAL -> NUMBER_KEYS

            // Boolean and invalid literals
            tokenType == BrsTokenTypes.TRUE ||
            tokenType == BrsTokenTypes.FALSE ||
            tokenType == BrsTokenTypes.INVALID -> CONSTANT_KEYS

            // Strings
            tokenType == BrsTokenTypes.STRING_LITERAL -> STRING_KEYS

            // Comments
            tokenType == BrsTokenTypes.COMMENT -> COMMENT_KEYS

            // Operators
            tokenType in BrsTokenTypes.OPERATORS -> OPERATOR_KEYS

            // Braces
            tokenType == BrsTokenTypes.LBRACE ||
            tokenType == BrsTokenTypes.RBRACE -> BRACES_KEYS

            // Brackets
            tokenType == BrsTokenTypes.LBRACKET ||
            tokenType == BrsTokenTypes.RBRACKET -> BRACKETS_KEYS

            // Parentheses
            tokenType == BrsTokenTypes.LPAREN ||
            tokenType == BrsTokenTypes.RPAREN -> PARENTHESES_KEYS

            // Identifiers
            tokenType == BrsTokenTypes.IDENTIFIER -> IDENTIFIER_KEYS

            // Bad character
            tokenType == BrsTokenTypes.BAD_CHARACTER -> BAD_CHARACTER_KEYS

            // Default (whitespace, newlines, etc.)
            else -> EMPTY_KEYS
        }
    }
}

/**
 * Factory for creating BrightScript syntax highlighters.
 */
class BrsSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        return BrsSyntaxHighlighter()
    }
}
