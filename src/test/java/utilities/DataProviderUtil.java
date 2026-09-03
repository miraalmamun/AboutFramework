package utilities;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class DataProviderUtil {

    private static final Gson GSON = new Gson();

    private static final Type TEST_DATA_TYPE =
            new TypeToken<List<Map<String, String>>>() {}.getType();

    private DataProviderUtil() {
        // Prevent utility-class instantiation
    }

    /*
     * Use for files under src/test/resources.
     */
    public static Object[][] getJsonDataFromClasspath(
            String resourceName
    ) throws IOException {

        InputStream inputStream = DataProviderUtil.class
                .getClassLoader()
                .getResourceAsStream(resourceName);

        if (inputStream == null) {
            throw new IOException(
                    "JSON resource not found on classpath: " + resourceName
            );
        }

        try (Reader reader = new InputStreamReader(
                inputStream,
                StandardCharsets.UTF_8
        )) {
            return convertJsonToDataProvider(
                    reader,
                    "classpath resource: " + resourceName
            );
        }
    }

    /*
     * Use for files under the project folder but outside the classpath.
     */
    public static Object[][] getJsonDataFromProject(
            String first,
            String... more
    ) throws IOException {

        Path projectRoot = Path.of(
                System.getProperty("user.dir")
        ).toAbsolutePath().normalize();

        Path jsonPath = projectRoot
                .resolve(Path.of(first, more))
                .normalize();

        if (!jsonPath.startsWith(projectRoot)) {
            throw new IOException(
                    "JSON file must be inside the project folder: " + jsonPath
            );
        }

        if (!Files.isRegularFile(jsonPath)) {
            throw new IOException(
                    "JSON file not found: " + jsonPath
            );
        }

        try (Reader reader = Files.newBufferedReader(
                jsonPath,
                StandardCharsets.UTF_8
        )) {
            return convertJsonToDataProvider(
                    reader,
                    "file: " + jsonPath
            );
        }
    }

    private static Object[][] convertJsonToDataProvider(
            Reader reader,
            String source
    ) throws IOException {

        List<Map<String, String>> testData;

        try {
            testData = GSON.fromJson(reader, TEST_DATA_TYPE);
        } catch (JsonParseException exception) {
            throw new IOException(
                    "Invalid JSON in " + source,
                    exception
            );
        }

        if (testData == null || testData.isEmpty()) {
            throw new IOException(
                    "JSON contains no test data in " + source
            );
        }

        return testData.stream()
                .map(data -> new Object[]{data})
                .toArray(Object[][]::new);
    }
}