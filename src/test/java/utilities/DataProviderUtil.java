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
import java.util.List;
import java.util.Map;

public final class DataProviderUtil {

    private static final Gson GSON = new Gson();

    private static final Type TEST_DATA_TYPE =
            new TypeToken<List<Map<String, String>>>() {}.getType();

    private DataProviderUtil() {
        // Prevent utility-class instantiation
    }

    public static Object[][] getJsonDataToMap(String resourceName)
            throws IOException {

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
            List<Map<String, Object>> testData;

            try {
                testData = GSON.fromJson(reader, TEST_DATA_TYPE);
            } catch (JsonParseException exception) {
                throw new IOException(
                        "Invalid JSON in resource: " + resourceName,
                        exception
                );
            }

            if (testData == null || testData.isEmpty()) {
                throw new IOException(
                        "JSON resource contains no test data: " + resourceName
                );
            }

            return testData.stream()
                    .map(data -> new Object[]{data})
                    .toArray(Object[][]::new);
        }
    }
}