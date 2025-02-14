import java.util.List;

public class Parser {

    private final List<Token> tokens;
    private int currentTokenIndex;
    private boolean panicMode = false;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.currentTokenIndex = 0;
    }

    private Token currentToken() {
        if (currentTokenIndex < tokens.size()) {
            return tokens.get(currentTokenIndex);
        }
        return new Token(Token.TokenType.TOKEN_EOF, "EOF", -1, -1);
    }

    private Token peekToken(int offset) {
        int index = currentTokenIndex + offset;
        if (index < tokens.size()) {
            return tokens.get(index);
        }
        return new Token(Token.TokenType.TOKEN_EOF, "EOF", -1, -1);
    }

    private void consumeToken() {
        if (currentTokenIndex < tokens.size()) {
            currentTokenIndex++;
        }
    }

    private ParseTreeNode matchAndCreate(Token.TokenType expectedType) throws ParserException {
        if (currentToken().type == expectedType) {
            ParseTreeNode node = new ParseTreeNode(expectedType.toString(), currentToken());
            consumeToken();
            return node;
        } else {
            reportError("Expected token " + expectedType + " but found " + currentToken().type, expectedType);
            synchronize(); // This will throw an exception due to panic mode, need to catch at top level parse().
            return null; // synchronize() method will not return
        }
    }

    private ParseTreeNode createNode(String name) {
        return new ParseTreeNode(name);
    }
    private ParseTreeNode createNode(String name, Token token) {
        return new ParseTreeNode(name, token);
    }

    public ParseTreeNode parse() throws ParserException {
        ParseTreeNode root = null;
        try {
            root = parseProgram();
            if (!panicMode) {
                System.out.println("Parsing successful!");
            } else {
                System.err.println("Parsing failed due to errors.");
                throw new ParserException("Parsing failed", currentToken().lineNumber, currentToken().columnNumber);
            }
        } catch (ParserException e){
            // System.err.println("Caught a parser exception: " + e.getMessage());
            // rethrow the exception for top-level handling
            throw e;
        }

        return root;
    }

    private ParseTreeNode parseProgram() throws ParserException {
        var programNode = createNode("Program");
        while (currentToken().type != Token.TokenType.TOKEN_EOF) {
            var declarationNode = parseDeclaration();
            if (declarationNode != null) {
                programNode.addChild(declarationNode);
            } else {
                synchronize();
            }
        }
        return programNode;
    }

    private ParseTreeNode parseDeclaration() throws ParserException {
        var declarationNode = createNode("Declaration");

        if (currentToken().type.isKeyword() && (
                currentToken().type == Token.TokenType.INT_KW ||
                        currentToken().type == Token.TokenType.FLOAT_KW ||
                        currentToken().type == Token.TokenType.CHAR_KW ||
                        currentToken().type == Token.TokenType.BOOL_KW)) {
            if (peekToken(1).type == Token.TokenType.IDENTIFIER) {
                switch (peekToken(2).type) {
                    case LEFT_PARENTHESIS:
                        declarationNode.addChild(parseFunctionDeclaration());
                        break;
                    case LEFT_BRACKET:
                        declarationNode.addChild(parseArrayDeclaration());
                        break;
                    default:
                        declarationNode.addChild(parseVariableDeclaration());
                        break;
                }
                return declarationNode;
            } else {
                return null; // Or throw an exception if this is unexpected
            }

        } else {
            return null;
        }
    }

    private ParseTreeNode parseVariableDeclaration() throws ParserException {
        var varDeclNode = createNode("Variable_Declaration");
        varDeclNode.addChild(parseDataType());
        varDeclNode.addChild(createNode("Identifier", currentToken()));
        matchAndCreate(Token.TokenType.IDENTIFIER);

        if (currentToken().type == Token.TokenType.ASSIGN) {
            varDeclNode.addChild(matchAndCreate(Token.TokenType.ASSIGN));
            varDeclNode.addChild(parseExp());
        }

        while (currentToken().type == Token.TokenType.COMMA) {
            varDeclNode.addChild(matchAndCreate(Token.TokenType.COMMA));
            varDeclNode.addChild(createNode("Identifier", currentToken()));
            matchAndCreate(Token.TokenType.IDENTIFIER);
            if (currentToken().type == Token.TokenType.ASSIGN) {
                varDeclNode.addChild(matchAndCreate(Token.TokenType.ASSIGN));
                varDeclNode.addChild(parseExp());
            }
        }
        varDeclNode.addChild(matchAndCreate(Token.TokenType.SEMICOLON));
        return varDeclNode;
    }

    private ParseTreeNode parseArrayDeclaration() throws ParserException {
        var arrayDeclNode = createNode("Array_Declaration");
        arrayDeclNode.addChild(parseDataType());
        arrayDeclNode.addChild(createNode("Identifier", currentToken()));

        matchAndCreate(Token.TokenType.IDENTIFIER);
        arrayDeclNode.addChild(matchAndCreate(Token.TokenType.LEFT_BRACKET));

        if (currentToken().type == Token.TokenType.INTEGER_LITERAL ||
                currentToken().type == Token.TokenType.FLOAT_LITERAL ||
                currentToken().type == Token.TokenType.CHARACTER_LITERAL ||
                currentToken().type == Token.TokenType.TRUE_KW ||
                currentToken().type == Token.TokenType.FALSE_KW) {
            arrayDeclNode.addChild(parseConst());
        }

        arrayDeclNode.addChild(matchAndCreate(Token.TokenType.RIGHT_BRACKET));


        if (currentToken().type == Token.TokenType.ASSIGN) {
            arrayDeclNode.addChild(matchAndCreate(Token.TokenType.ASSIGN));
            arrayDeclNode.addChild(matchAndCreate(Token.TokenType.LEFT_BRACE));

            if (currentToken().type != Token.TokenType.RIGHT_BRACE) {
                arrayDeclNode.addChild(parseArgumentList());
            }
            arrayDeclNode.addChild(matchAndCreate(Token.TokenType.RIGHT_BRACE));

        }

        arrayDeclNode.addChild(matchAndCreate(Token.TokenType.SEMICOLON));

        return arrayDeclNode;
    }

    private ParseTreeNode parseFunctionDeclaration() throws ParserException {
        var funcDeclNode = createNode("Function_Declaration");
        funcDeclNode.addChild(parseDataType());
        funcDeclNode.addChild(createNode("Identifier", currentToken()));

        matchAndCreate(Token.TokenType.IDENTIFIER);
        funcDeclNode.addChild(matchAndCreate(Token.TokenType.LEFT_PARENTHESIS));
        funcDeclNode.addChild(parseParameterList());
        funcDeclNode.addChild(matchAndCreate(Token.TokenType.RIGHT_PARENTHESIS));

        if (currentToken().type == Token.TokenType.LEFT_BRACE) {
            funcDeclNode.addChild(parseBlock());
        } else {
            funcDeclNode.addChild(matchAndCreate(Token.TokenType.SEMICOLON));
        }
        return funcDeclNode;
    }

    private ParseTreeNode parseParameterList() throws ParserException {
        var paramListNode = createNode("Parameter_List");

        if (currentToken().type == Token.TokenType.VOID_KW) {
            consumeToken(); // consume void
        } else if (currentToken().type == Token.TokenType.INT_KW ||
                currentToken().type == Token.TokenType.FLOAT_KW ||
                currentToken().type == Token.TokenType.CHAR_KW ||
                currentToken().type == Token.TokenType.BOOL_KW) {

            paramListNode.addChild(parseDataType());
            paramListNode.addChild(createNode("Identifier", currentToken()));
            matchAndCreate(Token.TokenType.IDENTIFIER);

            while (currentToken().type == Token.TokenType.COMMA) {
                paramListNode.addChild(matchAndCreate(Token.TokenType.COMMA));
                paramListNode.addChild(parseDataType());
                paramListNode.addChild(createNode("Identifier", currentToken()));
                matchAndCreate(Token.TokenType.IDENTIFIER);
            }
        }
        return paramListNode;
    }

    private ParseTreeNode parseDataType() throws ParserException {
        var dataTypeNode = createNode("Data_Type");
        Token current = currentToken();
        switch (current.type) {
            case INT_KW -> {
                dataTypeNode.addChild(createNode("int", currentToken()));
                matchAndCreate(Token.TokenType.INT_KW);
            }
            case FLOAT_KW -> {
                dataTypeNode.addChild(createNode("float", currentToken()));
                matchAndCreate(Token.TokenType.FLOAT_KW);
            }
            case CHAR_KW -> {
                dataTypeNode.addChild(createNode("char", currentToken()));
                matchAndCreate(Token.TokenType.CHAR_KW);
            }
            case BOOL_KW -> {
                dataTypeNode.addChild(createNode("bool", currentToken()));
                matchAndCreate(Token.TokenType.BOOL_KW);
            }
            default ->
                    throw new ParserException("Expected a data type", current.lineNumber, current.columnNumber);

        }
        return dataTypeNode;
    }
    private ParseTreeNode parseArgumentList() throws ParserException {
        var argListNode = createNode("Argument_List");
        var exp = createNode("Exp");
        exp.addChild(parseExp());
        argListNode.addChild(exp);
        while (currentToken().type == Token.TokenType.COMMA) {
            argListNode.addChild(matchAndCreate(Token.TokenType.COMMA));
            exp = createNode("Exp");
            exp.addChild(parseExp());
            argListNode.addChild(exp);
        }
        return argListNode;
    }

    private ParseTreeNode parseBlock() throws ParserException {
        var blockNode = createNode("Block");
        blockNode.addChild(matchAndCreate(Token.TokenType.LEFT_BRACE));
        blockNode.addChild(parseBlockItemList());
        blockNode.addChild(matchAndCreate(Token.TokenType.RIGHT_BRACE));
        return blockNode;
    }

    private ParseTreeNode parseBlockItemList() throws ParserException {
        var blockItemList = createNode("Block_Item_List");
        while (currentToken().type != Token.TokenType.RIGHT_BRACE && currentToken().type != Token.TokenType.TOKEN_EOF) {
            var blockItem = parseBlockItem();
            if (blockItem != null) {
                blockItemList.addChild(blockItem);
            } else {
                synchronize();
            }
        }
        return blockItemList;
    }

    private ParseTreeNode parseBlockItem() throws ParserException {
        var blockItemNode = createNode("Block_Item");

        if (currentToken().type == Token.TokenType.INT_KW ||
                currentToken().type == Token.TokenType.FLOAT_KW ||
                currentToken().type == Token.TokenType.CHAR_KW ||
                currentToken().type == Token.TokenType.BOOL_KW) {

            if (peekToken(2).type == Token.TokenType.LEFT_BRACKET) {
                blockItemNode.addChild(parseArrayDeclaration());
            } else {
                blockItemNode.addChild(parseVariableDeclaration());
            }
        } else {
            blockItemNode.addChild(parseStatement());
        }
        return blockItemNode;

    }

    private ParseTreeNode parseStatement() throws ParserException {
        var statementNode = createNode("Statement");

        switch (currentToken().type) {
            case RETURN_KW -> statementNode.addChild(parseReturnStatement());
            case IDENTIFIER -> {
                statementNode.addChild(parseExp());
                statementNode.addChild(matchAndCreate(Token.TokenType.SEMICOLON));
            }
            case IF_KW -> statementNode.addChild(parseIfStatement());
            case WHILE_KW -> statementNode.addChild(parseWhileStatement());
            case FOR_KW -> statementNode.addChild(parseForStatement());
            case SCANF_KW -> statementNode.addChild(parseInputStatement());
            case PRINTF_KW -> statementNode.addChild(parseOutputStatement());
            case SEMICOLON -> {
                statementNode.addChild(matchAndCreate(Token.TokenType.SEMICOLON));

            }
            case LEFT_BRACE -> statementNode.addChild(parseBlock());
            default -> statementNode.addChild(parseExpressionStatement());
        }
        return statementNode;
    }

    private ParseTreeNode parseForStatement() throws ParserException {
        var forNode = createNode("For_Statement");
        forNode.addChild(matchAndCreate(Token.TokenType.FOR_KW));
        forNode.addChild(matchAndCreate(Token.TokenType.LEFT_PARENTHESIS));

        if (currentToken().type == Token.TokenType.INT_KW ||
                currentToken().type == Token.TokenType.FLOAT_KW ||
                currentToken().type == Token.TokenType.CHAR_KW ||
                currentToken().type == Token.TokenType.BOOL_KW) {
            if (peekToken(2).type == Token.TokenType.LEFT_BRACKET) {
                forNode.addChild(parseArrayDeclaration());
            } else {
                forNode.addChild(parseVariableDeclaration());
            }
        } else {
            forNode.addChild(parseExp());
            forNode.addChild(matchAndCreate(Token.TokenType.SEMICOLON));
        }

        forNode.addChild(parseExp());
        forNode.addChild(matchAndCreate(Token.TokenType.SEMICOLON));
        forNode.addChild(parseExp());
        forNode.addChild(matchAndCreate(Token.TokenType.RIGHT_PARENTHESIS));
        forNode.addChild(parseBlock());

        return forNode;
    }

    private ParseTreeNode parseWhileStatement() throws ParserException {
        var whileNode = createNode("While_Statement");
        whileNode.addChild(matchAndCreate(Token.TokenType.WHILE_KW));
        whileNode.addChild(matchAndCreate(Token.TokenType.LEFT_PARENTHESIS));
        whileNode.addChild(parseExp());
        whileNode.addChild(matchAndCreate(Token.TokenType.RIGHT_PARENTHESIS));
        whileNode.addChild(parseBlock());
        return whileNode;
    }

    private ParseTreeNode parseInputStatement() throws ParserException {
        var inputNode = createNode("Input_Statement");
        inputNode.addChild(matchAndCreate(Token.TokenType.SCANF_KW));
        inputNode.addChild(matchAndCreate(Token.TokenType.LEFT_PARENTHESIS));
        inputNode.addChild(createNode("String", currentToken()));
        matchAndCreate(Token.TokenType.STRING);

        while (currentToken().type == Token.TokenType.COMMA) {
            inputNode.addChild(matchAndCreate(Token.TokenType.COMMA));
            inputNode.addChild(matchAndCreate(Token.TokenType.AMPERSAND));
            inputNode.addChild(createNode("Identifier", currentToken()));
            matchAndCreate(Token.TokenType.IDENTIFIER);
        }
        inputNode.addChild(matchAndCreate(Token.TokenType.RIGHT_PARENTHESIS));
        inputNode.addChild(matchAndCreate(Token.TokenType.SEMICOLON));
        return inputNode;
    }

    private ParseTreeNode parseOutputStatement() throws ParserException {
        var outputNode = createNode("Output_Statement");
        outputNode.addChild(matchAndCreate(Token.TokenType.PRINTF_KW));
        outputNode.addChild(matchAndCreate(Token.TokenType.LEFT_PARENTHESIS));

        if (currentToken().type == Token.TokenType.STRING) {
            outputNode.addChild(createNode("String", currentToken()));
            matchAndCreate(Token.TokenType.STRING);
            while (currentToken().type == Token.TokenType.COMMA) {
                outputNode.addChild(matchAndCreate(Token.TokenType.COMMA));
                outputNode.addChild(parseExp());
            }
        } else if (currentToken().type == Token.TokenType.IDENTIFIER) {
            outputNode.addChild(createNode("Identifier", currentToken()));
            matchAndCreate(Token.TokenType.IDENTIFIER);
        } else {
            throw new ParserException("Expected String or Identifier", currentToken().lineNumber, currentToken().columnNumber);
        }
        outputNode.addChild(matchAndCreate(Token.TokenType.RIGHT_PARENTHESIS));
        outputNode.addChild(matchAndCreate(Token.TokenType.SEMICOLON));

        return outputNode;

    }

    private ParseTreeNode parseIfStatement() throws ParserException {
        var ifNode = createNode("If_Statement");
        ifNode.addChild(matchAndCreate(Token.TokenType.IF_KW));
        ifNode.addChild(matchAndCreate(Token.TokenType.LEFT_PARENTHESIS));
        ifNode.addChild(parseExp());
        ifNode.addChild(matchAndCreate(Token.TokenType.RIGHT_PARENTHESIS));
        ifNode.addChild(parseBlock());

        while (currentToken().type == Token.TokenType.ELSE_KW) {
            ifNode.addChild(parseElseClause());
        }
        return ifNode;
    }

    private ParseTreeNode parseElseClause() throws ParserException {
        var elseNode = createNode("Else_Clause");
        elseNode.addChild(matchAndCreate(Token.TokenType.ELSE_KW));
        if (currentToken().type == Token.TokenType.IF_KW) {
            elseNode.addChild(parseIfStatement());
        } else {
            elseNode.addChild(parseBlock());
        }
        return elseNode;
    }

    private ParseTreeNode parseReturnStatement() throws ParserException {
        var returnNode = createNode("Return_Statement");
        returnNode.addChild(matchAndCreate(Token.TokenType.RETURN_KW));
        returnNode.addChild(parseExp());
        returnNode.addChild(matchAndCreate(Token.TokenType.SEMICOLON));
        return returnNode;
    }

    private ParseTreeNode parseExpressionStatement() throws ParserException {
        var expressionStatementNode = createNode("Expression_Statement");
        var expNode = createNode("Exp");
        expNode.addChild(parseExp());
        expressionStatementNode.addChild(expNode);
        expressionStatementNode.addChild(matchAndCreate(Token.TokenType.SEMICOLON));
        return expressionStatementNode;
    }

    private ParseTreeNode parseExp() throws ParserException {
        if (currentToken().type == Token.TokenType.IDENTIFIER) {
            int lookahead = 1;
            // Check for array access
            if (peekToken(1).type == Token.TokenType.LEFT_BRACKET) {
                lookahead = 2;
                while (peekToken(lookahead).type != Token.TokenType.RIGHT_BRACKET && peekToken(lookahead).type != Token.TokenType.TOKEN_EOF) {
                    lookahead++;
                }
                if (peekToken(lookahead).type == Token.TokenType.RIGHT_BRACKET) {
                    lookahead++;
                } else {
                    throw new ParserException("Expected ] but got" + peekToken(lookahead).lexeme, currentToken().lineNumber, currentToken().columnNumber);
                }
            }


            if (peekToken(lookahead).type == Token.TokenType.ASSIGN) {
                var expNode = createNode("Exp");
                expNode.addChild(createNode("Identifier", currentToken()));
                matchAndCreate(Token.TokenType.IDENTIFIER);
                // Handle array access
                if (currentToken().type == Token.TokenType.LEFT_BRACKET) {
                    expNode.addChild(matchAndCreate(Token.TokenType.LEFT_BRACKET));
                    expNode.addChild(parseConst());
                    matchAndCreate(Token.TokenType.RIGHT_BRACKET);
                }

                expNode.addChild(matchAndCreate(Token.TokenType.ASSIGN));
                expNode.addChild(parseExp());
                return expNode;
            }
        }

        return parseLogicalOrExp();
    }

    private ParseTreeNode parseLogicalOrExp() throws ParserException {
        var left = parseLogicalAndExp();
        if (currentToken().type == Token.TokenType.OR) {
            var orNode = createNode("Logical_Or");
            orNode.addChild(left);
            while (currentToken().type == Token.TokenType.OR) {
                orNode.addChild(matchAndCreate(Token.TokenType.OR));
                orNode.addChild(parseLogicalAndExp());

                if (currentToken().type == Token.TokenType.OR) {
                    var newOrNode = createNode("Logical_Or");
                    newOrNode.addChild(orNode);
                    orNode = newOrNode;
                }
            }
            return orNode;
        }
        return left;
    }

    private ParseTreeNode parseLogicalAndExp() throws ParserException {
        var left = parseEqualityExp();
        if (currentToken().type == Token.TokenType.AND) {
            var andNode = createNode("Logical_And");
            andNode.addChild(left);
            while (currentToken().type == Token.TokenType.AND) {
                andNode.addChild(matchAndCreate(Token.TokenType.AND));
                andNode.addChild(parseEqualityExp());
                if (currentToken().type == Token.TokenType.AND) {
                    var newAndNode = createNode("Logical_And");
                    newAndNode.addChild(andNode);
                    andNode = newAndNode;
                }
            }
            return andNode;
        }
        return left;
    }

    private ParseTreeNode parseEqualityExp() throws ParserException {
        var left = parseRelationalExp();
        if (currentToken().type == Token.TokenType.EQUAL || currentToken().type == Token.TokenType.NOT_EQUAL) {
            var equalityNode = createNode("Equality");
            equalityNode.addChild(left);
            while (currentToken().type == Token.TokenType.EQUAL || currentToken().type == Token.TokenType.NOT_EQUAL) {
                equalityNode.addChild(matchAndCreate(currentToken().type));
                equalityNode.addChild(parseRelationalExp());
                if (currentToken().type == Token.TokenType.EQUAL || currentToken().type == Token.TokenType.NOT_EQUAL) {
                    var newEqualityNode = createNode("Equality");
                    newEqualityNode.addChild(equalityNode);
                    equalityNode = newEqualityNode;
                }
            }
            return equalityNode;
        }
        return left;
    }

    private ParseTreeNode parseRelationalExp() throws ParserException {
        var left = parseAdditiveExp();
        if (currentToken().type == Token.TokenType.LESS || currentToken().type == Token.TokenType.GREATER ||
                currentToken().type == Token.TokenType.LESS_EQUAL || currentToken().type == Token.TokenType.GREATER_EQUAL) {

            var relationalNode = createNode("Relational");
            relationalNode.addChild(left);
            while (currentToken().type == Token.TokenType.LESS || currentToken().type == Token.TokenType.GREATER ||
                    currentToken().type == Token.TokenType.LESS_EQUAL || currentToken().type == Token.TokenType.GREATER_EQUAL) {

                relationalNode.addChild(matchAndCreate(currentToken().type));
                relationalNode.addChild(parseAdditiveExp());

                if (currentToken().type == Token.TokenType.LESS || currentToken().type == Token.TokenType.GREATER ||
                        currentToken().type == Token.TokenType.LESS_EQUAL || currentToken().type == Token.TokenType.GREATER_EQUAL) {
                    var newRelationalNode = createNode("Relational");
                    newRelationalNode.addChild(relationalNode);
                    relationalNode = newRelationalNode;
                }
            }
            return relationalNode;
        }
        return left;
    }

    private ParseTreeNode parseAdditiveExp() throws ParserException {
        var left = parseMultiplicativeExp();

        if (currentToken().type == Token.TokenType.PLUS || currentToken().type == Token.TokenType.MINUS) {
            var additiveNode = createNode("Additive");
            additiveNode.addChild(left);

            while (currentToken().type == Token.TokenType.PLUS || currentToken().type == Token.TokenType.MINUS) {
                additiveNode.addChild(matchAndCreate(currentToken().type));
                additiveNode.addChild(parseMultiplicativeExp());

                if (currentToken().type == Token.TokenType.PLUS || currentToken().type == Token.TokenType.MINUS) {
                    var newAdditiveNode = createNode("Additive");
                    newAdditiveNode.addChild(additiveNode);
                    additiveNode = newAdditiveNode;
                }
            }
            return additiveNode;
        }
        return left;
    }

    private ParseTreeNode parseMultiplicativeExp() throws ParserException {
        var left = parsePowerExp();

        if (currentToken().type == Token.TokenType.MULTIPLY || currentToken().type == Token.TokenType.DIVIDE ||
                currentToken().type == Token.TokenType.MODULO) {

            var multiplicativeNode = createNode("Multiplicative");
            multiplicativeNode.addChild(left);

            while (currentToken().type == Token.TokenType.MULTIPLY || currentToken().type == Token.TokenType.DIVIDE ||
                    currentToken().type == Token.TokenType.MODULO) {
                multiplicativeNode.addChild(matchAndCreate(currentToken().type));
                multiplicativeNode.addChild(parsePowerExp());

                if (currentToken().type == Token.TokenType.MULTIPLY || currentToken().type == Token.TokenType.DIVIDE ||
                        currentToken().type == Token.TokenType.MODULO) {
                    var newMultiplicativeNode = createNode("Multiplicative");
                    newMultiplicativeNode.addChild(multiplicativeNode);
                    multiplicativeNode = newMultiplicativeNode;
                }
            }
            return multiplicativeNode;
        }

        return left;
    }

    private ParseTreeNode parsePowerExp() throws ParserException {
        var left = parseUnaryExp();

        if (currentToken().type == Token.TokenType.EXPONENT) {
            var powerNode = createNode("Exponent");
            powerNode.addChild(left);
            powerNode.addChild(matchAndCreate(Token.TokenType.EXPONENT));
            powerNode.addChild(parsePowerExp());
            return powerNode;
        }
        return left;
    }

    private ParseTreeNode parseUnaryExp() throws ParserException {
        if (currentToken().type == Token.TokenType.NOT ||
                currentToken().type == Token.TokenType.PLUS ||
                currentToken().type == Token.TokenType.MINUS) {
            var unaryNode = createNode("Unary_Exp");
            unaryNode.addChild(matchAndCreate(currentToken().type));
            unaryNode.addChild(parseUnaryExp());
            return unaryNode;
        } else {
            return parseFactor();
        }
    }

    private ParseTreeNode parseFactor() throws ParserException {
        var factorNode = createNode("Factor");

        switch (currentToken().type) {
            case INTEGER_LITERAL, FLOAT_LITERAL, CHARACTER_LITERAL, TRUE_KW, FALSE_KW ->
                    factorNode.addChild(parseConst());
            case IDENTIFIER -> {
                factorNode.addChild(createNode("Identifier", currentToken()));
                matchAndCreate(Token.TokenType.IDENTIFIER);
                if (currentToken().type == Token.TokenType.LEFT_PARENTHESIS) {
                    factorNode.addChild(matchAndCreate(Token.TokenType.LEFT_PARENTHESIS));
                    if (currentToken().type != Token.TokenType.RIGHT_PARENTHESIS) {
                        factorNode.addChild(parseArgumentList());
                    }
                    matchAndCreate(Token.TokenType.RIGHT_PARENTHESIS);
                } else if (currentToken().type == Token.TokenType.LEFT_BRACKET) {
                    consumeToken();
                    factorNode.addChild(parseConst());
                    matchAndCreate(Token.TokenType.RIGHT_BRACKET);
                }
            }
            case LEFT_PARENTHESIS -> {
                consumeToken();
                var exp = parseExp();
                factorNode.addChild(exp);
                matchAndCreate(Token.TokenType.RIGHT_PARENTHESIS);
            }
            default -> throw new ParserException("Unexpected token in factor", currentToken().lineNumber, currentToken().columnNumber);
        }
        return factorNode;
    }

    private ParseTreeNode parseConst() throws ParserException {
        var constNode = createNode("Const");

        switch (currentToken().type) {
            case INTEGER_LITERAL -> {
                constNode.addChild(createNode("Int", currentToken()));
                matchAndCreate(Token.TokenType.INTEGER_LITERAL);
            }
            case FLOAT_LITERAL -> {
                constNode.addChild(createNode("Float", currentToken()));
                matchAndCreate(Token.TokenType.FLOAT_LITERAL);
            }
            case CHARACTER_LITERAL -> {
                constNode.addChild(createNode("Char", currentToken()));
                matchAndCreate(Token.TokenType.CHARACTER_LITERAL);
            }
            case TRUE_KW, FALSE_KW -> {
                constNode.addChild(createNode("Bool", currentToken()));
                matchAndCreate(currentToken().type); // match true or false
            }
            default ->
                    throw new ParserException("Expected constant", currentToken().lineNumber, currentToken().columnNumber);

        }
        return constNode;
    }

    private void reportError(String message, Token.TokenType expected) throws ParserException {
        System.err.println("Error: " + message + ", Expected: " + expected + ", Line: " + currentToken().lineNumber + ", Column: " + currentToken().columnNumber);
        panicMode = true;
        throw new ParserException(message, currentToken().lineNumber, currentToken().columnNumber);
    }

    private void synchronize() {
        panicMode = true;
        consumeToken();

        while (currentToken().type != Token.TokenType.TOKEN_EOF) {
            switch (currentToken().type) {
                case SEMICOLON, INT_KW, FLOAT_KW, CHAR_KW, BOOL_KW, RETURN_KW, WHILE_KW, FOR_KW, LEFT_BRACE -> {
                    panicMode = false;
                    return;
                }
            }
            consumeToken();
        }
    }

    public static class ParserException extends Exception {
        public ParserException(String message, int lineNumber, int columnNumber) {
            super(String.format("%s (line: %d, column: %d)", message, lineNumber, columnNumber));
        }
    }
}