package org.jetbrains.kotlin.idea.roku.language.brs

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

/**
 * Token types for BrightScript lexer.
 */
object BrsTokenTypes {

    // Keywords
    val FUNCTION = BrsElementType("FUNCTION")
    val SUB = BrsElementType("SUB")
    val END_FUNCTION = BrsElementType("END_FUNCTION")
    val END_SUB = BrsElementType("END_SUB")
    val IF = BrsElementType("IF")
    val THEN = BrsElementType("THEN")
    val ELSE = BrsElementType("ELSE")
    val ELSE_IF = BrsElementType("ELSE_IF")
    val END_IF = BrsElementType("END_IF")
    val FOR = BrsElementType("FOR")
    val TO = BrsElementType("TO")
    val STEP = BrsElementType("STEP")
    val END_FOR = BrsElementType("END_FOR")
    val FOR_EACH = BrsElementType("FOR_EACH")
    val IN = BrsElementType("IN")
    val WHILE = BrsElementType("WHILE")
    val END_WHILE = BrsElementType("END_WHILE")
    val RETURN = BrsElementType("RETURN")
    val PRINT = BrsElementType("PRINT")
    val DIM = BrsElementType("DIM")
    val AS = BrsElementType("AS")
    val AND = BrsElementType("AND")
    val OR = BrsElementType("OR")
    val NOT = BrsElementType("NOT")
    val TRUE = BrsElementType("TRUE")
    val FALSE = BrsElementType("FALSE")
    val INVALID = BrsElementType("INVALID")
    val MOD = BrsElementType("MOD")
    val EXIT = BrsElementType("EXIT")
    val NEXT = BrsElementType("NEXT")
    val STOP = BrsElementType("STOP")
    val GOTO = BrsElementType("GOTO")
    val TRY = BrsElementType("TRY")
    val CATCH = BrsElementType("CATCH")
    val END_TRY = BrsElementType("END_TRY")
    val THROW = BrsElementType("THROW")
    val LIBRARY = BrsElementType("LIBRARY")

    // Type keywords
    val INTEGER = BrsElementType("INTEGER")
    val FLOAT = BrsElementType("FLOAT")
    val DOUBLE = BrsElementType("DOUBLE")
    val STRING = BrsElementType("STRING")
    val BOOLEAN = BrsElementType("BOOLEAN")
    val OBJECT = BrsElementType("OBJECT")
    val DYNAMIC = BrsElementType("DYNAMIC")
    val VOID = BrsElementType("VOID")
    val LONGINTEGER = BrsElementType("LONGINTEGER")

    // Literals
    val INTEGER_LITERAL = BrsElementType("INTEGER_LITERAL")
    val FLOAT_LITERAL = BrsElementType("FLOAT_LITERAL")
    val STRING_LITERAL = BrsElementType("STRING_LITERAL")

    // Identifiers
    val IDENTIFIER = BrsElementType("IDENTIFIER")

    // Operators
    val EQ = BrsElementType("EQ")        // =
    val NE = BrsElementType("NE")        // <>
    val LT = BrsElementType("LT")        // <
    val GT = BrsElementType("GT")        // >
    val LE = BrsElementType("LE")        // <=
    val GE = BrsElementType("GE")        // >=
    val PLUS = BrsElementType("PLUS")    // +
    val MINUS = BrsElementType("MINUS")  // -
    val MULT = BrsElementType("MULT")    // *
    val DIV = BrsElementType("DIV")      // /
    val BACKSLASH = BrsElementType("BACKSLASH") // \
    val CARET = BrsElementType("CARET")  // ^
    val AMP = BrsElementType("AMP")      // &

    // Punctuation
    val LPAREN = BrsElementType("LPAREN")       // (
    val RPAREN = BrsElementType("RPAREN")       // )
    val LBRACKET = BrsElementType("LBRACKET")   // [
    val RBRACKET = BrsElementType("RBRACKET")   // ]
    val LBRACE = BrsElementType("LBRACE")       // {
    val RBRACE = BrsElementType("RBRACE")       // }
    val DOT = BrsElementType("DOT")             // .
    val COMMA = BrsElementType("COMMA")         // ,
    val COLON = BrsElementType("COLON")         // :
    val SEMICOLON = BrsElementType("SEMICOLON") // ;
    val QUESTION = BrsElementType("QUESTION")   // ?
    val AT = BrsElementType("AT")               // @

    // Comments
    val COMMENT = BrsElementType("COMMENT")     // ' or REM
    val BLOCK_COMMENT = BrsElementType("BLOCK_COMMENT")

    // Whitespace and newlines
    val WHITESPACE = BrsElementType("WHITESPACE")
    val NEWLINE = BrsElementType("NEWLINE")

    // Error token
    val BAD_CHARACTER = BrsElementType("BAD_CHARACTER")

    // Token sets for syntax highlighter
    val KEYWORDS = TokenSet.create(
        FUNCTION, SUB, END_FUNCTION, END_SUB,
        IF, THEN, ELSE, ELSE_IF, END_IF,
        FOR, TO, STEP, END_FOR, FOR_EACH, IN, NEXT,
        WHILE, END_WHILE,
        RETURN, PRINT, DIM, AS,
        AND, OR, NOT,
        EXIT, STOP, GOTO,
        TRY, CATCH, END_TRY, THROW,
        LIBRARY, MOD
    )

    val TYPE_KEYWORDS = TokenSet.create(
        INTEGER, FLOAT, DOUBLE, STRING, BOOLEAN,
        OBJECT, DYNAMIC, VOID, LONGINTEGER
    )

    val LITERALS = TokenSet.create(
        INTEGER_LITERAL, FLOAT_LITERAL,
        TRUE, FALSE, INVALID
    )

    val STRINGS = TokenSet.create(STRING_LITERAL)

    val COMMENTS = TokenSet.create(COMMENT, BLOCK_COMMENT)

    val OPERATORS = TokenSet.create(
        EQ, NE, LT, GT, LE, GE,
        PLUS, MINUS, MULT, DIV, BACKSLASH, CARET, AMP
    )

    val BRACES = TokenSet.create(LPAREN, RPAREN, LBRACKET, RBRACKET, LBRACE, RBRACE)
}

/**
 * Element type for BrightScript tokens.
 */
class BrsElementType(debugName: String) : IElementType(debugName, BrsLanguage)
