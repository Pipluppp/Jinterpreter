class ScannerException extends Exception {
    public ScannerException(String message, int lineNumber, int columnNumber) {
        super(String.format("%s (line: %d, column: %d)", message, lineNumber, columnNumber));
    }
}