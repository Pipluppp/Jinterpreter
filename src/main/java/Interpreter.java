import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Interpreter {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <filename.core>");
            return;
        }

        String filename = args[0];
        if (!filename.endsWith(".core")) {
            System.err.println("Input file must have a .core extension.");
            return;
        }

        try {
            Scanner scanner = new Scanner(filename);
            List<Token> tokens = scanner.scan();
            writeSymbolTable(tokens);
        } catch (IOException | LexerException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void writeSymbolTable(List<Token> tokens) {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Paths.get("symbol_table.txt")))) {
            writeSymbolTableHeader(writer);
            tokens.forEach(writer::println);
            writeSymbolTableFooter(writer);
        } catch (IOException e) {
            System.err.println("Error writing symbol table: " + e.getMessage());
        }
    }

    private static void writeSymbolTableHeader(PrintWriter writer) {
        String line = "_".repeat(128); // Use String.repeat (Java 11+)
        writer.println(line);
        writer.println("TOKEN CODE      | TOKEN                    | LINE #          | COLUMN #        | LEXEME");
        writer.println(line);
    }

    private static void writeSymbolTableFooter(PrintWriter writer) {
        writer.println("_".repeat(128));
    }
}