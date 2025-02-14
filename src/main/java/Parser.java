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

    private void match(Token.TokenType expectedType) throws ParserException {
        if (currentToken().type == expectedType) {
            consumeToken();
        } else {
            reportError("Expected token " + expectedType + " but found " + currentToken().type, expectedType);
            synchronize();
        }
    }

    private ParseTreeNode createNode(String name) {
        return new ParseTreeNode(name);
    }
    private ParseTreeNode createNode(String name, Token token) {
        return new ParseTreeNode(name, token);
    }

    public ParseTreeNode parse() throws ParserException {
        ParseTreeNode root = parseProgram();
        if (!panicMode) {
            System.out.println("Parsing successful!");
        } else {
            System.err.println("Parsing failed due to errors.");
            throw new ParserException("Parsing failed", currentToken().lineNumber, currentToken().columnNumber);
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
                return null;
            }

        } else {
            return null;
        }
    }

    private ParseTreeNode parseVariableDeclaration() throws ParserException {
        var varDeclNode = createNode("Variable_Declaration");
        varDeclNode.addChild(parseDataType());
        varDeclNode.addChild(createNode("Identifier", currentToken()));
        match(Token.TokenType.IDENTIFIER);

        if (currentToken().type == Token.TokenType.ASSIGN) {
            consumeToken(); // Consume '='
            varDeclNode.addChild(parseExp());
        }

        while (currentToken().type == Token.TokenType.COMMA) {
            consumeToken();
            varDeclNode.addChild(createNode("Identifier", currentToken()));
            match(Token.TokenType.IDENTIFIER);
            if (currentToken().type == Token.TokenType.ASSIGN) {
                consumeToken(); // Consume '='
                varDeclNode.addChild(parseExp());
            }
        }
        match(Token.TokenType.SEMICOLON);
        return varDeclNode;
    }

    private ParseTreeNode parseArrayDeclaration() throws ParserException {
        var arrayDeclNode = createNode("Array_Declaration");
        arrayDeclNode.addChild(parseDataType());
        arrayDeclNode.addChild(createNode("Identifier", currentToken()));

        match(Token.TokenType.IDENTIFIER);
        match(Token.TokenType.LEFT_BRACKET);

        if (currentToken().type == Token.TokenType.INTEGER_LITERAL ||
                currentToken().type == Token.TokenType.FLOAT_LITERAL ||
                currentToken().type == Token.TokenType.CHARACTER_LITERAL ||
                currentToken().type == Token.TokenType.TRUE_KW ||
                currentToken().type == Token.TokenType.FALSE_KW) {
            arrayDeclNode.addChild(parseConst());
        }

        match(Token.TokenType.RIGHT_BRACKET);

        if (currentToken().type == Token.TokenType.ASSIGN) {
            consumeToken();
            match(Token.TokenType.LEFT_BRACE);
            if (currentToken().type != Token.TokenType.RIGHT_BRACE) {
                arrayDeclNode.addChild(parseArgumentList());
            }
            match(Token.TokenType.RIGHT_BRACE);
        }

        match(Token.TokenType.SEMICOLON);
        return arrayDeclNode;
    }

    private ParseTreeNode parseFunctionDeclaration() throws ParserException {
        var funcDeclNode = createNode("Function_Declaration");
        funcDeclNode.addChild(parseDataType());
        funcDeclNode.addChild(createNode("Identifier", currentToken()));

        match(Token.TokenType.IDENTIFIER);
        match(Token.TokenType.LEFT_PARENTHESIS);
        funcDeclNode.addChild(parseParameterList());
        match(Token.TokenType.RIGHT_PARENTHESIS);

        if (currentToken().type == Token.TokenType.LEFT_BRACE) {
            funcDeclNode.addChild(parseBlock());
        } else {
            match(Token.TokenType.SEMICOLON);
        }
        return funcDeclNode;
    }

    private ParseTreeNode parseParameterList() throws ParserException {
        var paramListNode = createNode("Parameter_List");

        if (currentToken().type == Token.TokenType.VOID_KW) {
            consumeToken();
        } else if (currentToken().type == Token.TokenType.INT_KW ||
                currentToken().type == Token.TokenType.FLOAT_KW ||
                currentToken().type == Token.TokenType.CHAR_KW ||
                currentToken().type == Token.TokenType.BOOL_KW) {

            paramListNode.addChild(parseDataType());
            paramListNode.addChild(createNode("Identifier", currentToken()));
            match(Token.TokenType.IDENTIFIER);

            while (currentToken().type == Token.TokenType.COMMA) {
                consumeToken();
                paramListNode.addChild(parseDataType());
                paramListNode.addChild(createNode("Identifier", currentToken()));

                match(Token.TokenType.IDENTIFIER);
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
                match(Token.TokenType.INT_KW);
            }
            case FLOAT_KW -> {
                dataTypeNode.addChild(createNode("float", currentToken()));
                match(Token.TokenType.FLOAT_KW);
            }
            case CHAR_KW -> {
                dataTypeNode.addChild(createNode("char", currentToken()));
                match(Token.TokenType.CHAR_KW);
            }
            case BOOL_KW -> {
                dataTypeNode.addChild(createNode("bool", currentToken()));
                match(Token.TokenType.BOOL_KW);
            }
            default ->
                //Error
                    throw new ParserException("Expected a data type", current.lineNumber, current.columnNumber);

        }
        return dataTypeNode;
    }

    private ParseTreeNode parseArgumentList() throws ParserException {
        var argListNode = createNode("Argument_List");
        argListNode.addChild(parseExp());
        while (currentToken().type == Token.TokenType.COMMA) {
            consumeToken();
            argListNode.addChild(parseExp());
        }
        return argListNode;
    }

    private ParseTreeNode parseBlock() throws ParserException {
        var blockNode = createNode("Block");
        match(Token.TokenType.LEFT_BRACE);
        blockNode.addChild(parseBlockItemList());
        match(Token.TokenType.RIGHT_BRACE);
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
                match(Token.TokenType.SEMICOLON);
            }
            case IF_KW -> statementNode.addChild(parseIfStatement());
            case WHILE_KW -> statementNode.addChild(parseWhileStatement());
            case FOR_KW -> statementNode.addChild(parseForStatement());
            case SCANF_KW -> statementNode.addChild(parseInputStatement());
            case PRINTF_KW -> statementNode.addChild(parseOutputStatement());
            case SEMICOLON -> {
                statementNode.addChild(createNode(";", currentToken()));
                consumeToken();
            }
            case LEFT_BRACE -> statementNode.addChild(parseBlock());
            default -> statementNode.addChild(parseExpressionStatement());
        }
        return statementNode;
    }

    private ParseTreeNode parseForStatement() throws ParserException {
        var forNode = createNode("For_Statement");
        match(Token.TokenType.FOR_KW);
        match(Token.TokenType.LEFT_PARENTHESIS);

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
            match(Token.TokenType.SEMICOLON);
        }

        forNode.addChild(parseExp());
        match(Token.TokenType.SEMICOLON);
        forNode.addChild(parseExp());
        match(Token.TokenType.RIGHT_PARENTHESIS);
        forNode.addChild(parseBlock());

        return forNode;
    }

    private ParseTreeNode parseWhileStatement() throws ParserException {
        var whileNode = createNode("While_Statement");
        match(Token.TokenType.WHILE_KW);
        match(Token.TokenType.LEFT_PARENTHESIS);
        whileNode.addChild(parseExp());
        match(Token.TokenType.RIGHT_PARENTHESIS);
        whileNode.addChild(parseBlock());
        return whileNode;
    }

    private ParseTreeNode parseInputStatement() throws ParserException {
        var inputNode = createNode("Input_Statement");
        match(Token.TokenType.SCANF_KW);
        match(Token.TokenType.LEFT_PARENTHESIS);
        inputNode.addChild(createNode("String", currentToken()));
        match(Token.TokenType.STRING);

        while (currentToken().type == Token.TokenType.COMMA) {
            consumeToken();
            match(Token.TokenType.AMPERSAND);
            inputNode.addChild(createNode("Identifier", currentToken()));
            match(Token.TokenType.IDENTIFIER);
        }
        match(Token.TokenType.RIGHT_PARENTHESIS);
        match(Token.TokenType.SEMICOLON);
        return inputNode;
    }

    private ParseTreeNode parseOutputStatement() throws ParserException {
        var outputNode = createNode("Output_Statement");
        match(Token.TokenType.PRINTF_KW);
        match(Token.TokenType.LEFT_PARENTHESIS);

        if (currentToken().type == Token.TokenType.STRING) {
            outputNode.addChild(createNode("String", currentToken()));
            match(Token.TokenType.STRING);
            while (currentToken().type == Token.TokenType.COMMA) {
                consumeToken();
                outputNode.addChild(parseExp());
            }
        } else if (currentToken().type == Token.TokenType.IDENTIFIER) {
            outputNode.addChild(createNode("Identifier", currentToken()));
            match(Token.TokenType.IDENTIFIER);
        } else {
            throw new ParserException("Expected String or Identifier", currentToken().lineNumber, currentToken().columnNumber);
        }
        match(Token.TokenType.RIGHT_PARENTHESIS);
        match(Token.TokenType.SEMICOLON);
        return outputNode;
    }

    private ParseTreeNode parseIfStatement() throws ParserException {
        var ifNode = createNode("If_Statement");
        match(Token.TokenType.IF_KW);
        match(Token.TokenType.LEFT_PARENTHESIS);
        ifNode.addChild(parseExp());
        match(Token.TokenType.RIGHT_PARENTHESIS);
        ifNode.addChild(parseBlock());

        while (currentToken().type == Token.TokenType.ELSE_KW) {
            ifNode.addChild(parseElseClause());
        }
        return ifNode;
    }

    private ParseTreeNode parseElseClause() throws ParserException {
        var elseNode = createNode("Else_Clause");
        match(Token.TokenType.ELSE_KW);
        if (currentToken().type == Token.TokenType.IF_KW) {
            elseNode.addChild(parseIfStatement());
        } else {
            elseNode.addChild(parseBlock());
        }
        return elseNode;
    }

    private ParseTreeNode parseReturnStatement() throws ParserException {
        var returnNode = createNode("Return_Statement");
        match(Token.TokenType.RETURN_KW);
        returnNode.addChild(parseExp());
        match(Token.TokenType.SEMICOLON);
        return returnNode;
    }

    private ParseTreeNode parseExpressionStatement() throws ParserException {
        var expressionStatementNode = createNode("Expression_Statement");
        expressionStatementNode.addChild(parseExp()); // Wrap in an Exp node
        match(Token.TokenType.SEMICOLON);
        return expressionStatementNode;
    }

    private ParseTreeNode parseExp() throws ParserException {
        if (currentToken().type == Token.TokenType.IDENTIFIER) {
            int lookahead = 1;
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
                match(Token.TokenType.IDENTIFIER);
                if (currentToken().type == Token.TokenType.LEFT_BRACKET) {
                    consumeToken();
                    expNode.addChild(parseConst());
                    match(Token.TokenType.RIGHT_BRACKET);
                }
                consumeToken();
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
                consumeToken();
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
                consumeToken();
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
                Token operator = currentToken();
                consumeToken();
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
                Token operator = currentToken();
                consumeToken();
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
                Token operator = currentToken();
                consumeToken();
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
                Token operator = currentToken();
                consumeToken();
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
            consumeToken();
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
            unaryNode.addChild(createNode("Unary_Op", currentToken()));

            consumeToken();
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
            match(Token.TokenType.IDENTIFIER);
            if (currentToken().type == Token.TokenType.LEFT_PARENTHESIS) {
                consumeToken();
                if (currentToken().type != Token.TokenType.RIGHT_PARENTHESIS) {
                    factorNode.addChild(parseArgumentList());
                }
                match(Token.TokenType.RIGHT_PARENTHESIS);
            } else if (currentToken().type == Token.TokenType.LEFT_BRACKET) {
                consumeToken();
                factorNode.addChild(parseConst());
                match(Token.TokenType.RIGHT_BRACKET);
            }
        }
        case LEFT_PARENTHESIS -> {
            consumeToken();
            var exp = parseExp();
            factorNode.addChild(exp);
            match(Token.TokenType.RIGHT_PARENTHESIS);
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
                consumeToken();
            }
            case FLOAT_LITERAL -> {
                constNode.addChild(createNode("Float", currentToken()));
                consumeToken();
            }
            case CHARACTER_LITERAL -> {
                constNode.addChild(createNode("Char", currentToken()));
                consumeToken();
            }
            case TRUE_KW, FALSE_KW -> {
                constNode.addChild(createNode("Bool", currentToken()));
                consumeToken();
            }
            default ->
                // Error
                    throw new ParserException("Expected constant", currentToken().lineNumber, currentToken().columnNumber);

        }
        return constNode;
    }

    private void reportError(String message, Token.TokenType expected) throws ParserException {
        System.err.println("Error: " + message + ", Expected: " + expected + ", Line: " + currentToken().lineNumber + ", Column: " + currentToken().columnNumber);
        panicMode = true; // Set panic mode flag
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