package io.kunkun.tpsgenerator.request.parameter;

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

    private final String filePath;
    private final String columnName;
    private final boolean randomSelection;
    private final List<String> values = new ArrayList<>();
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final Random random = new Random();

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
            log.info("Loaded {} values from file '{}'", values.size(), filePath);
        } catch (Exception e) {
            log.error("Failed to load values from file '{}': {}", filePath, e.getMessage(), e);
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

            for (CSVRecord record : parser) {
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
                }
            }
        }
    }

    /**
     * Loads values from a text file (one value per line).
     *
     * @throws IOException if reading the file fails
     */
    private void loadFromTextFile() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath));

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
    }

    @Override
    public String getValue() {
        if (values.isEmpty()) {
            return "error_empty_source";
        }

        if (randomSelection) {
            int index = random.nextInt(values.size());
            return values.get(index);
        } else {
            int index = currentIndex.getAndIncrement() % values.size();
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