import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ParseTreePrinter {

    private final PrintWriter writer;

    public ParseTreePrinter(String outputFilename) throws IOException {
        this.writer = new PrintWriter(Files.newBufferedWriter(Paths.get(outputFilename)));
    }

    public void print(ParseTreeNode node) {
        printNode(node, 0);
        writer.close();
    }


    private void printNode(ParseTreeNode node, int indentLevel) {
        if (node == null) {
            return;
        }

        printIndent(indentLevel);

        if (node.token != null) {
            // Terminal node
            //string must not be in ""
            if (node.token.type == Token.TokenType.STRING) {
                writer.print(node.token.type.name() + ": " + node.token.lexeme);
            }
            //keywords must not have ""
            else if (node.token.type.isKeyword()) {
                writer.print(node.token.lexeme);
            }
            // nodes like int, char, and bool in data type
            else if(node.name.equals("int") || node.name.equals("bool") || node.name.equals("char") || node.name.equals("float") ){
                writer.print(node.token.lexeme);
            }
            // other tokens print type: "lexeme"
            else {
                writer.print(node.token.type.name() + ": \"" + node.token.lexeme + "\"");
            }
        } else {
            // Non-terminal node
            writer.print(node.name + "(");
            if (!node.children.isEmpty()) {
                writer.println();
                for (int i = 0; i < node.children.size(); i++) {
                    printNode(node.children.get(i), indentLevel + 1);
                    if (i < node.children.size() - 1) {
                        writer.println(",");
                    }
                }
                writer.println();
                printIndent(indentLevel);
            }
            writer.print(")");
        }
    }
    private void printIndent(int indentLevel) {
        for (int i = 0; i < indentLevel; i++) {
            writer.print("  ");
        }
    }
}