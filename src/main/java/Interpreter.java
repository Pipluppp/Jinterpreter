import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class Interpreter {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Main <filename.core>");
            return;
        }

        String filename = args[0];
        if (!filename.endsWith(".core")) {
            System.out.println("Input file must have a .core extension.");
            return;
        }

        try {
            Scanner scanner = new Scanner(filename);
            List<Token> tokens = scanner.scan();
            scanner.close();
            writeSymbolTable(tokens);
        } catch (FileNotFoundException e) {
            System.err.println("Error: File not found: " + filename);
        } catch (IOException e) {
            System.err.println("Error: I/O Exception: " + e.getMessage());
        }
    }

    private static void writeSymbolTable(List<Token> tokens) {
        try (PrintWriter writer = new PrintWriter("symbol_table.txt")) {
            writeSymbolTableHeader(writer);
            for (Token token : tokens) {
                System.out.println("Next token is: " + token.type.name() + "          Next lexeme: is " + token.lexeme);
                writer.println(token);
            }
            writeSymbolTableFooter(writer);
        } catch (FileNotFoundException e) {
            System.err.println("Error writing symbol table: " + e.getMessage());
        }
    }

    private static void writeSymbolTableHeader(PrintWriter writer) {
        for (int i = 0; i < 128; i++) writer.print("_");
        writer.println();
        writer.println("TOKEN CODE      | TOKEN                    | LINE #          | COLUMN #        | LEXEME");
        for (int i = 0; i < 128; i++) writer.print("_");
        writer.println();
    }

    private static void writeSymbolTableFooter(PrintWriter writer) {
        for (int i = 0; i < 128; i++) writer.print("_");
        writer.println();
    }
}