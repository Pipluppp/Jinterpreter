public class Token {
    public enum TokenType {
        // Single-character tokens
        LEFT_PARENTHESIS, RIGHT_PARENTHESIS, LEFT_BRACKET, RIGHT_BRACKET,
        LEFT_BRACE, RIGHT_BRACE, COMMA, SEMICOLON, MULTIPLY, EXPONENT, AMPERSAND,

        // Multi-character operators
        PLUS, MINUS, DIVIDE, EQUAL, NOT_EQUAL, ASSIGN, LESS, LESS_EQUAL,
        GREATER, GREATER_EQUAL, NOT, OR, AND,

        // Other tokens
        MODULO, IDENTIFIER, STRING, INTEGER_LITERAL, FLOAT_LITERAL, CHARACTER_LITERAL,

        // Keywords (keep these separate for clarity and potential future use)
        CHAR_KW, INT_KW, FLOAT_KW, BOOL_KW, IF_KW, ELSE_KW, FOR_KW,
        WHILE_KW, RETURN_KW, PRINTF_KW, SCANF_KW, TRUE_KW, FALSE_KW, VOID_KW,

        // Error tokens (consider a separate ErrorToken class if you have many)
        ERROR_INVALID_CHARACTER, ERROR_INVALID_IDENTIFIER,

        // End of file token
        TOKEN_EOF;

        // Helper method to check if a token type is a keyword
        public boolean isKeyword() {
            return this.ordinal() >= CHAR_KW.ordinal() && this.ordinal() <= VOID_KW.ordinal();
        }
    }

    public TokenType type;
    public String lexeme;
    public int lineNumber;
    public int columnNumber;

    public Token(TokenType type, String lexeme, int lineNumber, int columnNumber) {
        this.type = type;
        this.lexeme = lexeme;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    @Override
    public String toString() {
        String typeName = type.name();

        return String.format("%-15d | %-24s | %-15d | %-15d | %s",
                type.ordinal(), typeName, lineNumber, columnNumber, lexeme);

    }
}