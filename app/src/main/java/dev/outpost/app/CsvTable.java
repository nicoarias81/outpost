package dev.outpost.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Bounded CSV reader. Quoted commas/newlines are preserved; no formula execution. */
final class CsvTable {
    static final int MAX_RECORDS = 2000, MAX_COLUMNS = 64, MAX_CELL_CHARS = 16384;
    record Row(int number, List<String> cells, String original) {}
    record Table(List<String> headers, List<Row> rows) {
        String passage(Row row) {
            StringBuilder text = new StringBuilder("CSV record ").append(row.number()).append('\n');
            for (int i = 0; i < headers.size(); i++) text.append(headers.get(i)).append(": ").append(row.cells().get(i)).append('\n');
            return text.toString().stripTrailing();
        }
    }
    static Table parse(String body) {
        if (body == null || body.isBlank() || body.indexOf('\0') >= 0) throw new IllegalArgumentException("CSV is empty or contains NUL");
        List<Row> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false, closedQuote = false;
        int recordStart = 0;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < body.length() && body.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else { quoted = false; closedQuote = true; }
                } else cell.append(ch);
            } else if (ch == ',') {
                addCell(cells, cell); closedQuote = false;
            } else if (ch == '\r' || ch == '\n') {
                addCell(cells, cell);
                addRecord(records, cells, body.substring(recordStart, i));
                if (ch == '\r' && i + 1 < body.length() && body.charAt(i + 1) == '\n') i++;
                recordStart = i + 1; closedQuote = false;
            } else if (ch == '"' && cell.length() == 0 && !closedQuote) {
                quoted = true;
            } else {
                if (closedQuote || ch == '"') throw new IllegalArgumentException("Malformed CSV quoting");
                // A UTF-8 BOM belongs to the file encoding, not to its first header.
                if (!(i == 0 && ch == '\ufeff')) cell.append(ch);
            }
            if (cell.length() > MAX_CELL_CHARS) throw new IllegalArgumentException("CSV cell is too large");
        }
        if (quoted) throw new IllegalArgumentException("Unclosed CSV quoted field");
        if (recordStart < body.length() || closedQuote || !cells.isEmpty() || cell.length() > 0) {
            addCell(cells, cell); addRecord(records, cells, body.substring(recordStart));
        }
        if (records.size() < 2) throw new IllegalArgumentException("CSV requires a header and at least one data record");
        List<String> headers = new ArrayList<>();
        for (String cellValue : records.get(0).cells()) headers.add(cellValue.trim());
        HashSet<String> unique = new HashSet<>();
        for (String header : headers) if (header.isEmpty() || header.length() > 120 || !unique.add(header.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("CSV headers must be non-empty and distinct");
        long indexedCharacters = 0;
        for (Row row : records.subList(1, records.size())) {
            int renderedLength = 32;
            for (int i = 0; i < Math.min(row.cells().size(), headers.size()); i++)
                renderedLength += headers.get(i).length() + row.cells().get(i).length() + 3;
            if (renderedLength > 16384) throw new IllegalArgumentException("CSV record is too large to index");
            indexedCharacters += renderedLength;
            if (indexedCharacters > 2_097_152) throw new IllegalArgumentException("CSV index expansion exceeds the limit");
        }
        for (Row row : records) if (row.cells().size() != headers.size())
            throw new IllegalArgumentException("CSV record " + row.number() + " has a different column count");
        return new Table(List.copyOf(headers), List.copyOf(records.subList(1, records.size())));
    }
    private static void addCell(List<String> cells, StringBuilder cell) {
        if (cells.size() >= MAX_COLUMNS) throw new IllegalArgumentException("CSV has too many columns");
        cells.add(cell.toString()); cell.setLength(0);
    }
    private static void addRecord(List<Row> rows, List<String> cells, String original) {
        if (rows.size() >= MAX_RECORDS) throw new IllegalArgumentException("CSV has too many records");
        rows.add(new Row(rows.size() + 1, List.copyOf(cells), original)); cells.clear();
    }
}
