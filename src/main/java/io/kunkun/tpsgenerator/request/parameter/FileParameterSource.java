package io.kunkun.tpsgenerator.request.parameter;

import io.kunkun.tpsgenerator.config.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parameter source that reads values from a file.
 */
@Slf4j
public class FileParameterSource implements ParameterSource {

    /**
     * Maximum number of lines to load from a file to prevent memory issues.
     */
    private static final int MAX_LINES = Constants.MAX_PARAMETER_FILE_LINES;

    private final String filePath;
    private final String columnName;
    private final boolean randomSelection;
    private final List<String> values = new ArrayList<>();
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final Random random = new Random();
    private volatile boolean initialized = false;

    /**
     * Creates a new file parameter source.
     *
     * @param filePath the path to the file
     * @param columnName the name of the column to read (null for single-column files)
     * @param randomSelection whether to select values randomly or sequentially
     */
    public FileParameterSource(String filePath, String columnName, boolean randomSelection) {
        this.filePath = filePath;
        this.columnName = columnName;
        this.randomSelection = randomSelection;

        try {
            loadValues();
            initialized = true;
            log.info("Loaded {} values from file '{}'", values.size(), filePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load values from file '" + filePath + "': " + e.getMessage(), e);
        }
    }

    /**
     * Loads values from the file.
     *
     * @throws IOException if reading the file fails
     */
    private void loadValues() throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }

        String fileExtension = getFileExtension(filePath);

        if ("csv".equalsIgnoreCase(fileExtension)) {
            loadFromCsv();
        } else {
            loadFromTextFile();
        }

        if (values.isEmpty()) {
            throw new IOException("No values loaded from file: " + filePath);
        }
    }

    /**
     * Gets the file extension.
     *
     * @param fileName the file name
     * @return the file extension
     */
    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Loads values from a CSV file.
     * Limits loading to MAX_LINES to prevent memory issues with large files.
     *
     * @throws IOException if reading the file fails
     */
    private void loadFromCsv() throws IOException {
        try (CSVParser parser = CSVParser.parse(
                new FileReader(filePath),
                CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .build())) {

            int loadedCount = 0;
            for (CSVRecord record : parser) {
                if (loadedCount >= MAX_LINES) {
                    log.warn("CSV file '{}' has more than {} records, truncating. Consider using a smaller file.",
                            filePath, MAX_LINES);
                    break;
                }

                String value;

                if (columnName != null) {
                    // Get value from the specified column
                    value = record.get(columnName);
                } else if (record.size() > 0) {
                    // Get value from the first column
                    value = record.get(0);
                } else {
                    continue;
                }

                if (value != null && !value.trim().isEmpty()) {
                    values.add(value.trim());
                    loadedCount++;
                }
            }
        }
    }

    /**
     * Loads values from a text file (one value per line).
     * Limits loading to MAX_LINES to prevent memory issues with large files.
     *
     * @throws IOException if reading the file fails
     */
    private void loadFromTextFile() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath));

        int loadedCount = 0;
        for (String line : lines) {
            if (loadedCount >= MAX_LINES) {
                log.warn("File '{}' has more than {} lines, truncating. Consider using a smaller file.",
                        filePath, MAX_LINES);
                break;
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
                loadedCount++;
            }
        }
    }

    @Override
    public String getValue() {
        if (!initialized) {
            throw new IllegalStateException("FileParameterSource not initialized: " + filePath);
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("No values available from file parameter source: " + filePath);
        }

        if (randomSelection) {
            int index = random.nextInt(values.size());
            return values.get(index);
        } else {
            // Use getAndUpdate to prevent integer overflow issues
            int index = currentIndex.getAndUpdate(i -> (i + 1) % values.size());
            return values.get(index);
        }
    }

    @Override
    public String getType() {
        return "file";
    }

    /**
     * Gets the number of available values.
     *
     * @return the number of values
     */
    public int getValueCount() {
        return values.size();
    }

    @Override
    public String toString() {
        return String.format("FileParameterSource(file='%s', column='%s', random=%s, values=%d)",
                filePath, columnName, randomSelection, values.size());
    }
}