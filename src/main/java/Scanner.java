import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Scanner {

    private BufferedReader reader;
    private int lineNumber;
    private int columnNumber;
    private char currentChar;
    private List<Token> tokens;

    public Scanner(String filename) throws FileNotFoundException {
        this.reader = new BufferedReader(new FileReader(filename));
        this.lineNumber = 1;
        this.columnNumber = 0;
        this.tokens = new ArrayList<>();
        readNextChar(); // Initialize currentChar
    }

    private void readNextChar() {
        try {
            int charCode = reader.read();
            currentChar = (charCode == -1) ? '\0' : (char) charCode;
            if (currentChar == '\n') {
                lineNumber++;
                columnNumber = 0;
            } else {
                columnNumber++;
            }
        } catch (IOException e) {
            System.err.println("Error reading character: " + e.getMessage());
            currentChar = '\0';
        }
    }
    private Optional<Character> peek() {
        try {
            reader.mark(1);
            int nextChar = reader.read();
            reader.reset();
            return (nextChar == -1) ? Optional.empty() : Optional.of((char) nextChar);
        } catch (IOException e) {
            System.err.println("Error peeking: " + e.getMessage());
            return Optional.empty();
        }
    }

    private void skipWhitespaceAndComments() {
        while (Character.isWhitespace(currentChar) || currentChar == '/') {
            if (currentChar == '/') {
                if (peek().orElse('\0') == '/') {
                    skipComment();
                } else {
                    return; // It's a divide operator, not a comment
                }
            } else {
                readNextChar();
            }
        }
    }

    private void skipComment() {
        // Consume characters until newline or EOF
        StringBuilder commentLexeme = new StringBuilder();
        commentLexeme.append("//");
        while (currentChar != '\n' && currentChar != '\0') {
            commentLexeme.append(currentChar);
            readNextChar();
        }
        System.out.println("Next token is: COMMENT"); //keep printing
    }

    public List<Token> scan() {
        while (currentChar != '\0') {
            skipWhitespaceAndComments();
            if (currentChar == '\0') {
                break;
            }

            int tokenStartLine = lineNumber;
            int tokenStartColumn = columnNumber;

            createToken().ifPresent(token -> {
                token.lineNumber = tokenStartLine;
                token.columnNumber = tokenStartColumn;
                tokens.add(token);
            });
        }

        addEOFToken();
        return tokens;
    }

    private Optional<Token> createToken() {
        if (Character.isDigit(currentChar) || (currentChar == '.' && peek().map(Character::isDigit).orElse(false))) {
            return Optional.of(scanNumber());
        } else if (Character.isLetter(currentChar) || currentChar == '_') {
            return Optional.of(scanIdentifierOrKeyword());
        } else if (currentChar == '"') {
            return Optional.of(scanString());
        } else if (currentChar == '\'') {
            return Optional.of(scanCharacterLiteral());
        } else {
            return scanSymbol();
        }
    }
    private Token scanNumber() {
        StringBuilder lexeme = new StringBuilder();
        boolean hasDecimal = false;
        boolean errorOccurred = false;

        // Leading decimal (if any)
        if (currentChar == '.') {
            lexeme.append(currentChar);
            hasDecimal = true;
            readNextChar();
        }

        while (Character.isDigit(currentChar) || currentChar == '\'' || currentChar == '`' || currentChar == '.') {
            if (currentChar == '.') {
                if (hasDecimal) {
                    break; // Second decimal
                }
                hasDecimal = true;
                lexeme.append(currentChar);
                readNextChar();
            } else if (currentChar == '\'' || currentChar == '`') {
                char separator = currentChar;
                lexeme.append(separator);
                readNextChar();
                int digits = 0;
                while (digits < 3 && Character.isDigit(currentChar)) {
                    lexeme.append(currentChar);
                    readNextChar();
                    digits++;
                }
                if (digits != 3) {
                    errorOccurred = true;
                    System.err.printf("ERROR: Invalid noise separators at line %d, col %d%n", lineNumber, columnNumber);
                    break; // Stop processing
                }
            } else {
                lexeme.append(currentChar);
                readNextChar();
            }
        }

        if (!errorOccurred) {
            String temp = "";
            int digitCount = 0;
            boolean strictNoiseValid = true;
            String currentLexeme = lexeme.toString();

            for (int i = currentLexeme.length() - 1; i >= 0; i--) {
                char c = currentLexeme.charAt(i);
                if (Character.isDigit(c)) {
                    temp += c;
                    digitCount++;
                } else if (c == '\'' || c == '`') {
                    if (digitCount != 3) {
                        strictNoiseValid = false;
                        break;
                    }
                    digitCount = 0;
                } else if (c == '.') {
                    temp += c;
                    digitCount = 0;
                }
            }

            if (!strictNoiseValid) {
                System.err.printf("ERROR: Invalid noise separators at line %d, col %d%n", lineNumber, columnNumber);
                return createErrorToken("Invalid noise separators"); // Better error handling
            }

            String cleanedLexeme = new StringBuilder(temp).reverse().toString();
            if (cleanedLexeme.startsWith(".")) {
                cleanedLexeme = "0" + cleanedLexeme;
            }
            if (cleanedLexeme.endsWith(".")) {
                cleanedLexeme = cleanedLexeme + "0";
            }
            return new Token(hasDecimal ? Token.TokenType.FLOAT_LITERAL : Token.TokenType.INTEGER_LITERAL, cleanedLexeme, 0, 0); // Line/col set later
        } else {
            while (Character.isDigit(currentChar) || currentChar == '\'' || currentChar == '`' || currentChar == '.') {
                readNextChar(); //consume rest of invalid number
            }
            return createErrorToken("Invalid Number");
        }
    }

    private Token scanIdentifierOrKeyword() {
        StringBuilder lexeme = new StringBuilder();
        while (Character.isLetterOrDigit(currentChar) || currentChar == '_') {
            lexeme.append(currentChar);
            readNextChar();
        }

        String lexemeStr = lexeme.toString();
        if (lexemeStr.length() > 31) {
            System.err.printf("ERROR - invalid identifier: %s%n", lexemeStr);
            //Do not add the invalid identifier token, and set lexeme to empty. Continue scanning
            return createErrorToken("Invalid Identifier");
        }


        Token.TokenType type = checkKeyword(lexemeStr);
        return new Token(type, lexemeStr, 0, 0);  // Line/col set later
    }

    private Token.TokenType checkKeyword(String lexeme) {
        try {
            return Token.TokenType.valueOf(lexeme.toUpperCase() + "_KW"); // Direct enum conversion
        } catch (IllegalArgumentException e) {
            return Token.TokenType.IDENTIFIER;
        }
    }


    private Token scanString() {
        StringBuilder lexeme = new StringBuilder();
        lexeme.append(currentChar); // Opening quote
        readNextChar();

        while (currentChar != '"' && currentChar != '\0') {
            if (currentChar == '\\') {
                lexeme.append(currentChar);
                readNextChar();
                if (currentChar != '\0' && currentChar != '"') {
                    lexeme.append(currentChar); // Escaped character
                } else {
                    System.err.println("ERROR: unterminated string");
                    return createErrorToken("Unterminated string"); // Stop if EOF or unterminated
                }
            } else {
                lexeme.append(currentChar);
            }
            readNextChar();
        }

        if (currentChar == '"') {
            lexeme.append(currentChar);
            readNextChar();
            return new Token(Token.TokenType.STRING, lexeme.toString(), 0, 0); // Line/col set later
        } else {
            System.err.println("ERROR: unterminated string");
            return createErrorToken("Unterminated String"); // Consistent error
        }
    }

    private Token scanCharacterLiteral() {
        StringBuilder lexeme = new StringBuilder();
        lexeme.append(currentChar); // Opening quote
        readNextChar();

        if (currentChar == '\\') {
            lexeme.append(currentChar);
            readNextChar();
            if (currentChar != '\'' && currentChar != '\0') {
                lexeme.append(currentChar);  // Escaped character
            }
            else {
                System.err.println("ERROR: invalid character literal");
                return createErrorToken("Invalid char literal"); // Stop processing
            }
        } else if (currentChar == '\'' || currentChar == '\0') {
            System.err.println("ERROR: invalid character literal");
            return createErrorToken("Invalid char literal"); // Stop processing
        } else {
            lexeme.append(currentChar);
        }

        readNextChar();
        if (currentChar == '\'') {
            lexeme.append(currentChar);
            readNextChar();
            return new Token(Token.TokenType.CHARACTER_LITERAL, lexeme.toString(), 0, 0); // Line/col set later
        } else {
            System.err.println("ERROR: unterminated character literal");
            return createErrorToken("Unterminated char"); // Stop processing
        }
    }


    private Optional<Token> scanSymbol() {
        switch (currentChar) {
            case '(': readNextChar(); return Optional.of(new Token(Token.TokenType.LEFT_PARENTHESIS, "(", 0, 0));
            case ')': readNextChar(); return Optional.of(new Token(Token.TokenType.RIGHT_PARENTHESIS, ")", 0, 0));
            case '[': readNextChar(); return Optional.of(new Token(Token.TokenType.LEFT_BRACKET, "[", 0, 0));
            case ']': readNextChar(); return Optional.of(new Token(Token.TokenType.RIGHT_BRACKET, "]", 0, 0));
            case '{': readNextChar(); return Optional.of(new Token(Token.TokenType.LEFT_BRACE, "{", 0, 0));
            case '}': readNextChar(); return Optional.of(new Token(Token.TokenType.RIGHT_BRACE, "}", 0, 0));
            case ',': readNextChar(); return Optional.of(new Token(Token.TokenType.COMMA, ",", 0, 0));
            case ';': readNextChar(); return Optional.of(new Token(Token.TokenType.SEMICOLON, ";", 0, 0));
            case '+': readNextChar(); return Optional.of(new Token(Token.TokenType.PLUS, "+", 0, 0));
            case '-': readNextChar(); return Optional.of(new Token(Token.TokenType.MINUS, "-", 0, 0));
            case '*': readNextChar(); return Optional.of(new Token(Token.TokenType.MULTIPLY, "*", 0, 0));
            case '^': readNextChar(); return Optional.of(new Token(Token.TokenType.EXPONENT, "^", 0, 0));
            case '%': readNextChar(); return Optional.of(new Token(Token.TokenType.MODULO, "%", 0, 0));
            case '=':
                readNextChar();
                if (currentChar == '=') {
                    readNextChar();
                    return Optional.of(new Token(Token.TokenType.EQUAL, "==", 0, 0));
                } else {
                    return Optional.of(new Token(Token.TokenType.ASSIGN, "=", 0, 0));
                }
            case '>':
                readNextChar();
                if (currentChar == '=') {
                    readNextChar();
                    return Optional.of(new Token(Token.TokenType.GREATER_EQUAL, ">=", 0, 0));
                } else {
                    return Optional.of(new Token(Token.TokenType.GREATER, ">", 0, 0));
                }
            case '<':
                readNextChar();
                if (currentChar == '=') {
                    readNextChar();
                    return Optional.of(new Token(Token.TokenType.LESS_EQUAL, "<=", 0, 0));
                } else {
                    return Optional.of(new Token(Token.TokenType.LESS, "<", 0, 0));
                }
            case '!':
                readNextChar();
                if (currentChar == '=') {
                    readNextChar();
                    return Optional.of(new Token(Token.TokenType.NOT_EQUAL, "!=", 0, 0));
                } else {
                    return Optional.of(new Token(Token.TokenType.NOT, "!", 0, 0));
                }
            case '&':
                readNextChar();
                if (currentChar == '&') {
                    readNextChar();
                    return Optional.of(new Token(Token.TokenType.AND, "&&", 0, 0));
                } else {
                    return Optional.of(new Token(Token.TokenType.AMPERSAND, "&", 0, 0));
                }
            case '|':
                readNextChar();
                if (currentChar == '|') {
                    readNextChar();
                    return Optional.of(new Token(Token.TokenType.OR, "||", 0, 0));
                } else {
                    System.err.println("ERROR - invalid char |");
                    return Optional.of(createErrorToken("|"));
                }
            case '/':
                readNextChar();
                return Optional.of(new Token(Token.TokenType.DIVIDE, "/", 0, 0));
            default:
                System.err.println("ERROR - invalid char " + currentChar);
                readNextChar(); // Consume the invalid character
                return Optional.of(createErrorToken(String.valueOf(currentChar)));

        }
    }

    private Token createErrorToken(String lexeme) {
        return new Token(Token.TokenType.ERROR_INVALID_CHARACTER, lexeme, lineNumber, columnNumber);
    }

    private void addEOFToken() {
        tokens.add(new Token(Token.TokenType.TOKEN_EOF, "EOF", lineNumber, -1)); // Keep consistent with C output
    }

    public void close() throws IOException {
        reader.close();
    }
}