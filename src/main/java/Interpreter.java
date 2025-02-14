import java.io.*;
import java.util.List;

public class Interpreter {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Interpreter <filename.core>");
            return;
        }

        String filename = args[0];
        if (!filename.endsWith(".core")) {
            System.err.println("Input file must have a .core extension.");
            return;
        }

        try {
            // Scanning phase
            Scanner scanner = new Scanner(filename);
            List<Token> tokens = scanner.scan();
            ScannerSymbolTablePrinter symbolTableWriter = new ScannerSymbolTablePrinter("symbol_table.txt");
            symbolTableWriter.write(tokens);

            // Parsing phase
            Parser parser = new Parser(tokens);
            ParseTreeNode parseTree = parser.parse();

            // Print Parse Tree.
            ParseTreePrinter printer = new ParseTreePrinter("parse_tree_output.ebnf");
            printer.print(parseTree);


        } catch (IOException | ScannerException | Parser.ParserException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    private static void writeSymbolTable(List<Token> tokens) {
        try (PrintWriter writer = new PrintWriter("symbol_table.txt")) {
            String header = """
                    ________________________________________________________________________________________________________________________________
                    TOKEN CODE      | TOKEN                    | LINE #          | COLUMN #        | LEXEME
                    ________________________________________________________________________________________________________________________________
                    """;
            writer.println(header);
            tokens.forEach(writer::println);
            writer.println("_".repeat(128));
        } catch (IOException e) {
            System.err.println("Error writing symbol table: " + e.getMessage());
        }
    }
}