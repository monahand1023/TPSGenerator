package io.kunkun.tpsgenerator.request;

import io.kunkun.tpsgenerator.request.parameter.FileParameterSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileParameterSource.
 */
class FileParameterSourceTest {

    @TempDir
    Path tempDir;

    // -------- text file (sequential) --------

    @Test
    @DisplayName("Loads values from a plain text file (one value per line)")
    void loadsValuesFromTextFile() throws IOException {
        Path file = writeLines(tempDir, "values.txt", "alpha", "beta", "gamma");
        FileParameterSource source = new FileParameterSource(file.toString(), null, false);
        assertEquals(3, source.getValueCount());
    }

    @Test
    @DisplayName("Sequential selection cycles through all values")
    void sequentialCyclesThroughValues() throws IOException {
        Path file = writeLines(tempDir, "seq.txt", "a", "b", "c");
        FileParameterSource source = new FileParameterSource(file.toString(), null, false);

        assertEquals("a", source.getValue());
        assertEquals("b", source.getValue());
        assertEquals("c", source.getValue());
        // cycles
        assertEquals("a", source.getValue());
    }

    @Test
    @DisplayName("getValue returns non-null string")
    void getValueNonNull() throws IOException {
        Path file = writeLines(tempDir, "nonnull.txt", "value1");
        FileParameterSource source = new FileParameterSource(file.toString(), null, false);
        assertNotNull(source.getValue());
    }

    @Test
    @DisplayName("Random selection returns values from file")
    void randomSelectionReturnsFileValues() throws IOException {
        Path file = writeLines(tempDir, "rand.txt", "x", "y", "z");
        FileParameterSource source = new FileParameterSource(file.toString(), null, true);
        Set<String> allowed = Set.of("x", "y", "z");
        for (int i = 0; i < 20; i++) {
            assertTrue(allowed.contains(source.getValue()));
        }
    }

    @Test
    @DisplayName("Random selection produces variety over many calls")
    void randomSelectionProducesVariety() throws IOException {
        Path file = writeLines(tempDir, "variety.txt", "p", "q", "r", "s");
        FileParameterSource source = new FileParameterSource(file.toString(), null, true);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) seen.add(source.getValue());
        assertTrue(seen.size() > 1, "Expected variety but only saw: " + seen);
    }

    // -------- CSV file --------

    @Test
    @DisplayName("Loads values from a CSV file (first column when no column name)")
    void loadsValuesFromCsvFirstColumn() throws IOException {
        Path file = tempDir.resolve("data.csv");
        Files.write(file, List.of("name,age", "Alice,30", "Bob,25"));
        FileParameterSource source = new FileParameterSource(file.toString(), null, false);
        assertTrue(source.getValueCount() > 0);
        String value = source.getValue();
        assertTrue("Alice".equals(value) || "Bob".equals(value),
                "Expected Alice or Bob but got: " + value);
    }

    @Test
    @DisplayName("Loads values from CSV by column name")
    void loadsValuesFromCsvByColumn() throws IOException {
        Path file = tempDir.resolve("named.csv");
        Files.write(file, List.of("id,name", "1,Alice", "2,Bob"));
        FileParameterSource source = new FileParameterSource(file.toString(), "name", false);
        assertEquals(2, source.getValueCount());
        String value = source.getValue();
        assertTrue("Alice".equals(value) || "Bob".equals(value));
    }

    // -------- error cases --------

    @Test
    @DisplayName("Throws IllegalArgumentException when file does not exist")
    void throwsOnMissingFile() {
        assertThrows(IllegalArgumentException.class,
                () -> new FileParameterSource("/nonexistent/path/data.txt", null, false));
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when file is empty")
    void throwsOnEmptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.createFile(emptyFile);
        assertThrows(IllegalArgumentException.class,
                () -> new FileParameterSource(emptyFile.toString(), null, false));
    }

    @Test
    @DisplayName("Ignores blank lines in text file")
    void ignoresBlankLines() throws IOException {
        Path file = tempDir.resolve("blanks.txt");
        Files.write(file, List.of("value1", "", "  ", "value2"));
        FileParameterSource source = new FileParameterSource(file.toString(), null, false);
        assertEquals(2, source.getValueCount());
    }

    // -------- getType --------

    @Test
    @DisplayName("getType returns 'file'")
    void getTypeIsFile() throws IOException {
        Path file = writeLines(tempDir, "type.txt", "val");
        FileParameterSource source = new FileParameterSource(file.toString(), null, false);
        assertEquals("file", source.getType());
    }

    // -------- helper --------

    private Path writeLines(Path dir, String name, String... lines) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, List.of(lines));
        return file;
    }
}
