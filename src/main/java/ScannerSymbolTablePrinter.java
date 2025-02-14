import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ScannerSymbolTablePrinter {

    private final String outputFilename;

    public ScannerSymbolTablePrinter(String outputFilename) {
        this.outputFilename = outputFilename;
    }

    public void write(List<Token> tokens) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Paths.get(outputFilename)))) {
            writeHeader(writer);
            tokens.forEach(writer::println);
            writeFooter(writer);
        }
    }

    private void writeHeader(PrintWriter writer) {
        String header = """
            ________________________________________________________________________________________________________________________________
            TOKEN CODE      | TOKEN                    | LINE #          | COLUMN #        | LEXEME
            ________________________________________________________________________________________________________________________________
            """;
        writer.println(header);
    }

    private void writeFooter(PrintWriter writer) {
        writer.println("_".repeat(128));
    }
}