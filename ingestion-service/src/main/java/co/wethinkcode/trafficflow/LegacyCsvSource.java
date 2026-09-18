package co.wethinkcode.trafficflow;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

/**
 * Reads the legacy export off the classpath into raw cells. Kept separate from
 * {@link CsvCleaner} so the cleaning rules stay testable without a file or a CSV library.
 */
public final class LegacyCsvSource {

    public static final String DEFAULT_RESOURCE = "intersections-legacy.csv";

    private LegacyCsvSource() {
    }

    /** @return every row including the header, with quoting and embedded commas handled by OpenCSV */
    public static List<String[]> read(String resourceName) throws IOException, CsvException {
        try (InputStream in = LegacyCsvSource.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("resource not found on classpath: " + resourceName);
            }
            try (CSVReader reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.readAll();
            }
        }
    }
}
