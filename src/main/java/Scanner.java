import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

class LexerException extends Exception {
    public LexerException(String message, int lineNumber, int columnNumber) {
        super(String.format("%s (line: %d, column: %d)", message, lineNumber, columnNumber));
    }
}

public class Scanner {
    private final BufferedReader reader;
    private int lineNumber;
    private int columnNumber;
    private char currentChar;
    private final List<Token> tokens = new ArrayList<>();

    public Scanner(String filename) throws IOException {
        this.reader = Files.newBufferedReader(Paths.get(filename));
        this.lineNumber = 1;
        this.columnNumber = 0;
        readNextChar();
    }

    private void readNextChar() throws IOException {
        int charCode = reader.read();
        if (charCode == -1) {
            currentChar = '\0';
        } else {
            currentChar = (char) charCode;
            if (currentChar == '\n') {
                lineNumber++;
                columnNumber = 0;
            } else {
                columnNumber++;
            }
        }
    }
    private char peek() throws IOException{
        reader.mark(1);
        int nextChar = reader.read();
        reader.reset();
        return (nextChar == -1) ? '\0' : (char) nextChar;
    }

    private void skipWhitespaceAndComments() throws IOException {
        while (Character.isWhitespace(currentChar) || currentChar == '/') {
            if (currentChar == '/') {
                if (peek() == '/') {
                    skipComment();
                } else {
                    return; // It's a divide operator
                }
            } else {
                readNextChar();
            }
        }
    }

    private void skipComment() throws IOException {
        while (currentChar != '\n' && currentChar != '\0') {
            readNextChar();
        }
    }

    public List<Token> scan() throws LexerException, IOException {
        while (currentChar != '\0') {
            skipWhitespaceAndComments();
            if (currentChar == '\0') {
                break;
            }
            int tokenStartLine = lineNumber;
            int tokenStartColumn = columnNumber;

            Token token = createToken();
            token.lineNumber = tokenStartLine;
            token.columnNumber = tokenStartColumn;
            tokens.add(token);
        }

        addEOFToken();
        return tokens;
    }

    private Token createToken() throws LexerException, IOException {
        if (Character.isDigit(currentChar) || (currentChar == '.' && Character.isDigit(peek()))) {
            return scanNumber();
        } else if (Character.isLetter(currentChar) || currentChar == '_') {
            return scanIdentifierOrKeyword();
        } else if (currentChar == '"') {
            return scanString();
        } else if (currentChar == '\'') {
            return scanCharacterLiteral();
        } else {
            return scanSymbol();
        }
    }

    private Token scanNumber() throws LexerException, IOException {
        StringBuilder lexeme = new StringBuilder();
        boolean hasDecimal = false;

        // Leading decimal (if any)
        if (currentChar == '.') {
            lexeme.append(currentChar);
            hasDecimal = true;
            readNextChar();
        }

        while (Character.isDigit(currentChar) || currentChar == '\'' || currentChar == '`' || currentChar == '.') {
            if (currentChar == '.') {
                if (hasDecimal) {
                    break; // Second decimal - invalid
                }
                hasDecimal = true;
            } else if (currentChar == '\'' || currentChar == '`') {
                // Simplified noise separator handling
                readNextChar();
                int digits = 0;
                while (digits < 3 && Character.isDigit(currentChar)) {
                    lexeme.append(currentChar);
                    readNextChar();
                    digits++;
                }
                if (digits != 3) {
                    throw new LexerException("Invalid noise separators", lineNumber, columnNumber);
                }
                continue; // Skip appending the separator itself
            }
            lexeme.append(currentChar);
            readNextChar();

        }

        String cleanedLexeme = lexeme.toString().replaceAll("['`]", "");

        if (cleanedLexeme.startsWith(".")) {
            cleanedLexeme = "0" + cleanedLexeme;
        }
        if (cleanedLexeme.endsWith(".")) {
            cleanedLexeme = cleanedLexeme + "0";
        }

        return new Token(hasDecimal ? Token.TokenType.FLOAT_LITERAL : Token.TokenType.INTEGER_LITERAL, cleanedLexeme, 0, 0);
    }

    private Token scanIdentifierOrKeyword() throws LexerException, IOException {
        StringBuilder lexeme = new StringBuilder();
        while (Character.isLetterOrDigit(currentChar) || currentChar == '_') {
            lexeme.append(currentChar);
            readNextChar();
        }

        String lexemeStr = lexeme.toString();
        if (lexemeStr.length() > 31) {
            throw new LexerException("Invalid identifier: exceeds maximum length", lineNumber, columnNumber);
        }

        Token.TokenType type = checkKeyword(lexemeStr);
        return new Token(type, lexemeStr, 0, 0);
    }

    private Token.TokenType checkKeyword(String lexeme) {
        return switch (lexeme.toLowerCase()) {
            case "char" -> Token.TokenType.CHAR_KW;
            case "int" -> Token.TokenType.INT_KW;
            case "float" -> Token.TokenType.FLOAT_KW;
            case "bool" -> Token.TokenType.BOOL_KW;
            case "if" -> Token.TokenType.IF_KW;
            case "else" -> Token.TokenType.ELSE_KW;
            case "for" -> Token.TokenType.FOR_KW;
            case "while" -> Token.TokenType.WHILE_KW;
            case "return" -> Token.TokenType.RETURN_KW;
            case "printf" -> Token.TokenType.PRINTF_KW;
            case "scanf" -> Token.TokenType.SCANF_KW;
            case "true" -> Token.TokenType.TRUE_KW;
            case "false" -> Token.TokenType.FALSE_KW;
            case "void" -> Token.TokenType.VOID_KW;
            default -> Token.TokenType.IDENTIFIER;
        };
    }

    private Token scanString() throws LexerException, IOException {
        StringBuilder lexeme = new StringBuilder();
        readNextChar();

        while (currentChar != '"' && currentChar != '\0') {
            if (currentChar == '\\') {
                readNextChar();
                switch (currentChar) {
                    case 'n':  lexeme.append('\n'); break;
                    case 't':  lexeme.append('\t'); break;
                    case 'r':  lexeme.append('\r'); break;
                    case '"':  lexeme.append('"');  break;
                    case '\\': lexeme.append('\\'); break;
                    default:
                        throw new LexerException("Invalid escape sequence", lineNumber, columnNumber);
                }
            } else {
                lexeme.append(currentChar);
            }
            readNextChar();
        }

        if (currentChar == '"') {
            readNextChar();
            return new Token(Token.TokenType.STRING, lexeme.toString(), 0, 0);
        } else {
            throw new LexerException("Unterminated string", lineNumber, columnNumber);
        }
    }

    private Token scanCharacterLiteral() throws LexerException, IOException {
        readNextChar();

        char charValue;
        if (currentChar == '\\') {
            readNextChar();
            charValue = switch (currentChar) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                case '\'' -> '\'';
                case '\\' -> '\\';
                default -> throw new LexerException("Invalid escape sequence in character literal", lineNumber, columnNumber);
            };
        } else if (currentChar == '\'' || currentChar == '\0') {
            throw new LexerException("Invalid character literal", lineNumber, columnNumber);
        } else {
            charValue = currentChar;
        }
        readNextChar();

        if(currentChar != '\'') {
            throw new LexerException("Unterminated character literal", lineNumber, columnNumber);
        }

        readNextChar();
        return new Token(Token.TokenType.CHARACTER_LITERAL, String.valueOf(charValue), 0, 0); // Store the actual character
    }

    private Token scanSymbol() throws LexerException, IOException {
        Token token = switch (currentChar) {
            case '(' -> new Token(Token.TokenType.LEFT_PARENTHESIS, "(", 0, 0);
            case ')' -> new Token(Token.TokenType.RIGHT_PARENTHESIS, ")", 0, 0);
            case '[' -> new Token(Token.TokenType.LEFT_BRACKET, "[", 0, 0);
            case ']' -> new Token(Token.TokenType.RIGHT_BRACKET, "]", 0, 0);
            case '{' -> new Token(Token.TokenType.LEFT_BRACE, "{", 0, 0);
            case '}' -> new Token(Token.TokenType.RIGHT_BRACE, "}", 0, 0);
            case ',' -> new Token(Token.TokenType.COMMA, ",", 0, 0);
            case ';' -> new Token(Token.TokenType.SEMICOLON, ";", 0, 0);
            case '+' -> new Token(Token.TokenType.PLUS, "+", 0, 0);
            case '-' -> new Token(Token.TokenType.MINUS, "-", 0, 0);
            case '*' -> new Token(Token.TokenType.MULTIPLY, "*", 0, 0);
            case '^' -> new Token(Token.TokenType.EXPONENT, "^", 0, 0);
            case '%' -> new Token(Token.TokenType.MODULO, "%", 0, 0);
            case '=' -> {
                if (peek() == '=') {
                    readNextChar();
                    yield new Token(Token.TokenType.EQUAL, "==", 0, 0); // Use yield in switch expression
                } else {
                    yield new Token(Token.TokenType.ASSIGN, "=", 0, 0);
                }
            }
            case '>' -> {
                if (peek() == '=') {
                    readNextChar();
                    yield new Token(Token.TokenType.GREATER_EQUAL, ">=", 0, 0);
                } else {
                    yield new Token(Token.TokenType.GREATER, ">", 0, 0);
                }
            }
            case '<' -> {
                if (peek() == '=') {
                    readNextChar();
                    yield new Token(Token.TokenType.LESS_EQUAL, "<=", 0, 0);
                } else {
                    yield new Token(Token.TokenType.LESS, "<", 0, 0);
                }
            }
            case '!' -> {
                if (peek() == '=') {
                    readNextChar();
                    yield new Token(Token.TokenType.NOT_EQUAL, "!=", 0, 0);
                } else {
                    yield new Token(Token.TokenType.NOT, "!", 0, 0);
                }
            }
            case '&' -> {
                if (peek() == '&') {
                    readNextChar();
                    yield new Token(Token.TokenType.AND, "&&", 0, 0);
                } else {
                    yield new Token(Token.TokenType.AMPERSAND, "&", 0, 0);
                }
            }
            case '|' -> {
                if (peek() == '|') {
                    readNextChar();
                    yield new Token(Token.TokenType.OR, "||", 0, 0);
                } else {
                    throw new LexerException("Invalid character '|'", lineNumber, columnNumber);
                }
            }
            case '/' -> new Token(Token.TokenType.DIVIDE, "/", 0, 0);
            default -> throw new LexerException("Invalid character: " + currentChar, lineNumber, columnNumber);
        };
        readNextChar();
        return token;
    }

    private void addEOFToken() {
        tokens.add(new Token(Token.TokenType.TOKEN_EOF, "EOF", lineNumber, 0));
    }
}