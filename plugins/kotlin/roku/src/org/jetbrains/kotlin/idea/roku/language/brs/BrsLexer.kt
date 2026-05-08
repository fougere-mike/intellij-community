package org.jetbrains.kotlin.idea.roku.language.brs

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Lexer for BrightScript source files.
 *
 * BrightScript is case-insensitive, so all keyword matching is done
 * without regard to case.
 */
class BrsLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var bufferEnd: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var currentToken: IElementType? = null

    // Keyword map (lowercase)
    private val keywords = mapOf(
        "function" to BrsTokenTypes.FUNCTION,
        "sub" to BrsTokenTypes.SUB,
        "end function" to BrsTokenTypes.END_FUNCTION,
        "endsub" to BrsTokenTypes.END_SUB,
        "end sub" to BrsTokenTypes.END_SUB,
        "endfunction" to BrsTokenTypes.END_FUNCTION,
        "if" to BrsTokenTypes.IF,
        "then" to BrsTokenTypes.THEN,
        "else" to BrsTokenTypes.ELSE,
        "elseif" to BrsTokenTypes.ELSE_IF,
        "else if" to BrsTokenTypes.ELSE_IF,
        "end if" to BrsTokenTypes.END_IF,
        "endif" to BrsTokenTypes.END_IF,
        "for" to BrsTokenTypes.FOR,
        "to" to BrsTokenTypes.TO,
        "step" to BrsTokenTypes.STEP,
        "end for" to BrsTokenTypes.END_FOR,
        "endfor" to BrsTokenTypes.END_FOR,
        "next" to BrsTokenTypes.NEXT,
        "for each" to BrsTokenTypes.FOR_EACH,
        "foreach" to BrsTokenTypes.FOR_EACH,
        "in" to BrsTokenTypes.IN,
        "while" to BrsTokenTypes.WHILE,
        "end while" to BrsTokenTypes.END_WHILE,
        "endwhile" to BrsTokenTypes.END_WHILE,
        "return" to BrsTokenTypes.RETURN,
        "print" to BrsTokenTypes.PRINT,
        "dim" to BrsTokenTypes.DIM,
        "as" to BrsTokenTypes.AS,
        "and" to BrsTokenTypes.AND,
        "or" to BrsTokenTypes.OR,
        "not" to BrsTokenTypes.NOT,
        "true" to BrsTokenTypes.TRUE,
        "false" to BrsTokenTypes.FALSE,
        "invalid" to BrsTokenTypes.INVALID,
        "mod" to BrsTokenTypes.MOD,
        "exit" to BrsTokenTypes.EXIT,
        "stop" to BrsTokenTypes.STOP,
        "goto" to BrsTokenTypes.GOTO,
        "try" to BrsTokenTypes.TRY,
        "catch" to BrsTokenTypes.CATCH,
        "end try" to BrsTokenTypes.END_TRY,
        "endtry" to BrsTokenTypes.END_TRY,
        "throw" to BrsTokenTypes.THROW,
        "library" to BrsTokenTypes.LIBRARY,
        // Types
        "integer" to BrsTokenTypes.INTEGER,
        "float" to BrsTokenTypes.FLOAT,
        "double" to BrsTokenTypes.DOUBLE,
        "string" to BrsTokenTypes.STRING,
        "boolean" to BrsTokenTypes.BOOLEAN,
        "object" to BrsTokenTypes.OBJECT,
        "dynamic" to BrsTokenTypes.DYNAMIC,
        "void" to BrsTokenTypes.VOID,
        "longinteger" to BrsTokenTypes.LONGINTEGER
    )

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.currentToken = null
        advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = currentToken

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd

    override fun advance() {
        tokenStart = tokenEnd

        if (tokenStart >= bufferEnd) {
            currentToken = null
            return
        }

        val c = buffer[tokenStart]

        currentToken = when {
            c.isWhitespace() && c != '\n' && c != '\r' -> scanWhitespace()
            c == '\n' || c == '\r' -> scanNewline()
            c == '\'' -> scanComment()
            c == '"' -> scanString()
            c.isDigit() -> scanNumber()
            c.isLetter() || c == '_' -> scanIdentifierOrKeyword()
            else -> scanOperatorOrPunctuation()
        }
    }

    private fun scanWhitespace(): IElementType {
        while (tokenEnd < bufferEnd) {
            val c = buffer[tokenEnd]
            if (!c.isWhitespace() || c == '\n' || c == '\r') break
            tokenEnd++
        }
        return BrsTokenTypes.WHITESPACE
    }

    private fun scanNewline(): IElementType {
        if (tokenEnd < bufferEnd && buffer[tokenEnd] == '\r') {
            tokenEnd++
        }
        if (tokenEnd < bufferEnd && buffer[tokenEnd] == '\n') {
            tokenEnd++
        }
        return BrsTokenTypes.NEWLINE
    }

    private fun scanComment(): IElementType {
        tokenEnd++ // Skip '
        while (tokenEnd < bufferEnd && buffer[tokenEnd] != '\n' && buffer[tokenEnd] != '\r') {
            tokenEnd++
        }
        return BrsTokenTypes.COMMENT
    }

    private fun scanString(): IElementType {
        tokenEnd++ // Skip opening quote
        while (tokenEnd < bufferEnd) {
            val c = buffer[tokenEnd]
            if (c == '"') {
                tokenEnd++
                break
            }
            if (c == '\n' || c == '\r') break // Unclosed string
            tokenEnd++
        }
        return BrsTokenTypes.STRING_LITERAL
    }

    private fun scanNumber(): IElementType {
        var hasDecimal = false
        var hasExponent = false

        while (tokenEnd < bufferEnd) {
            val c = buffer[tokenEnd]
            when {
                c.isDigit() -> tokenEnd++
                c == '.' && !hasDecimal && !hasExponent -> {
                    hasDecimal = true
                    tokenEnd++
                }
                (c == 'e' || c == 'E') && !hasExponent -> {
                    hasExponent = true
                    tokenEnd++
                    // Allow optional sign after exponent
                    if (tokenEnd < bufferEnd && (buffer[tokenEnd] == '+' || buffer[tokenEnd] == '-')) {
                        tokenEnd++
                    }
                }
                c == '#' || c == '!' || c == '%' || c == '&' -> {
                    // Type suffixes
                    tokenEnd++
                    break
                }
                else -> break
            }
        }

        return if (hasDecimal || hasExponent) BrsTokenTypes.FLOAT_LITERAL else BrsTokenTypes.INTEGER_LITERAL
    }

    private fun scanIdentifierOrKeyword(): IElementType {
        val start = tokenEnd

        while (tokenEnd < bufferEnd) {
            val c = buffer[tokenEnd]
            if (!c.isLetterOrDigit() && c != '_') break
            tokenEnd++
        }

        val text = buffer.subSequence(start, tokenEnd).toString().lowercase()

        // Check for REM comment
        if (text == "rem") {
            while (tokenEnd < bufferEnd && buffer[tokenEnd] != '\n' && buffer[tokenEnd] != '\r') {
                tokenEnd++
            }
            return BrsTokenTypes.COMMENT
        }

        // Check for compound keywords (like "end if", "for each")
        if (text == "end" || text == "else" || text == "for") {
            val savedEnd = tokenEnd

            // Skip whitespace
            while (tokenEnd < bufferEnd && (buffer[tokenEnd] == ' ' || buffer[tokenEnd] == '\t')) {
                tokenEnd++
            }

            // Try to read next word
            val wordStart = tokenEnd
            while (tokenEnd < bufferEnd && (buffer[tokenEnd].isLetter() || buffer[tokenEnd] == '_')) {
                tokenEnd++
            }

            if (tokenEnd > wordStart) {
                val nextWord = buffer.subSequence(wordStart, tokenEnd).toString().lowercase()
                val compound = "$text $nextWord"
                val compoundToken = keywords[compound]
                if (compoundToken != null) {
                    return compoundToken
                }
            }

            // No compound match, restore position
            tokenEnd = savedEnd
        }

        // Check for simple keyword
        return keywords[text] ?: BrsTokenTypes.IDENTIFIER
    }

    private fun scanOperatorOrPunctuation(): IElementType {
        val c = buffer[tokenEnd]
        tokenEnd++

        return when (c) {
            '=' -> BrsTokenTypes.EQ
            '<' -> {
                if (tokenEnd < bufferEnd) {
                    when (buffer[tokenEnd]) {
                        '>' -> { tokenEnd++; BrsTokenTypes.NE }
                        '=' -> { tokenEnd++; BrsTokenTypes.LE }
                        else -> BrsTokenTypes.LT
                    }
                } else BrsTokenTypes.LT
            }
            '>' -> {
                if (tokenEnd < bufferEnd && buffer[tokenEnd] == '=') {
                    tokenEnd++
                    BrsTokenTypes.GE
                } else BrsTokenTypes.GT
            }
            '+' -> BrsTokenTypes.PLUS
            '-' -> BrsTokenTypes.MINUS
            '*' -> BrsTokenTypes.MULT
            '/' -> BrsTokenTypes.DIV
            '\\' -> BrsTokenTypes.BACKSLASH
            '^' -> BrsTokenTypes.CARET
            '&' -> BrsTokenTypes.AMP
            '(' -> BrsTokenTypes.LPAREN
            ')' -> BrsTokenTypes.RPAREN
            '[' -> BrsTokenTypes.LBRACKET
            ']' -> BrsTokenTypes.RBRACKET
            '{' -> BrsTokenTypes.LBRACE
            '}' -> BrsTokenTypes.RBRACE
            '.' -> BrsTokenTypes.DOT
            ',' -> BrsTokenTypes.COMMA
            ':' -> BrsTokenTypes.COLON
            ';' -> BrsTokenTypes.SEMICOLON
            '?' -> BrsTokenTypes.QUESTION
            '@' -> BrsTokenTypes.AT
            else -> BrsTokenTypes.BAD_CHARACTER
        }
    }
}
