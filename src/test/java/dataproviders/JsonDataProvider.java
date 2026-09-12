package dataproviders;

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

/**
 * Reads test data from JSON files and converts it into the
 * Object[][] format expected by TestNG data providers.
 *
 * <h2>Why is this class called JsonDataProvider?</h2>
 *
 * <p>The name describes the responsibility of the class:</p>
 *
 * <ul>
 *     <li>JsonData means that JSON is the source of the test data.</li>
 *     <li>Provider means that the class prepares and provides the JSON data
 *     to a TestNG data-provider method.</li>
 * </ul>
 *
 * <p>Technically, this class is a JSON reader/helper. Its methods are not
 * automatically discovered by TestNG because they do not have the
 * {@code @DataProvider} annotation.</p>
 *
 * <p>The actual TestNG data-provider method is normally placed in a separate
 * class such as {@code TestDataProviders}. That method calls this class and
 * returns its {@code Object[][]} result.</p>
 *
 * <h2>Why can this class be used by a TestNG data provider?</h2>
 *
 * <p>TestNG data-provider methods can return an {@code Object[][]}. This class
 * returns exactly that type.</p>
 *
 * <p>For example, a JSON file may contain:</p>
 *
 * <pre>{@code
 * [
 *   {
 *     "username": "validUser",
 *     "password": "validPassword"
 *   },
 *   {
 *     "username": "invalidUser",
 *     "password": "wrongPassword"
 *   }
 * ]
 * }</pre>
 *
 * <p>The JSON objects are converted into this TestNG-compatible structure:</p>
 *
 * <pre>{@code
 * Object[][] {
 *     { row1Map },
 *     { row2Map }
 * }
 * }</pre>
 *
 * <p>Each JSON object becomes one test invocation. The test method receives
 * one {@code Map<String, String>} for each invocation.</p>
 *
 * <h2>Example using a classpath JSON file</h2>
 *
 * <pre>{@code
 * // TestDataProviders.java
 *
 * package dataproviders;
 *
 * import org.testng.annotations.DataProvider;
 *
 * import java.io.IOException;
 *
 * public final class TestDataProviders {
 *
 *     private TestDataProviders() {
 *     }
 *
 *     @DataProvider(name = "jsonLoginData", parallel = false)
 *     public static Object[][] jsonLoginData() throws IOException {
 *         return JsonDataProvider.getJsonDataFromClasspath(
 *                 "testdata/loginData.json"
 *         );
 *     }
 * }
 *
 *
 * // LoginTest.java
 *
 * package tests;
 *
 * import dataproviders.TestDataProviders;
 * import org.testng.annotations.Test;
 *
 * import java.util.Map;
 *
 * public final class LoginTest {
 *
 *     @Test(
 *             dataProvider = "jsonLoginData",
 *             dataProviderClass = TestDataProviders.class
 *     )
 *     public void loginTest(Map<String, String> data) {
 *         String username = data.get("username");
 *         String password = data.get("password");
 *
 *         // Use the values in the test.
 *     }
 * }
 * }</pre>
 *
 * <h2>Classpath files and project files</h2>
 *
 * <p>Use {@link #getJsonDataFromClasspath(String)} when the JSON file is
 * stored under {@code src/test/resources}. This is the recommended approach
 * for Maven and Jenkins because the file is available on the test runtime
 * classpath.</p>
 *
 * <p>Use {@link #getJsonDataFromProject(String, String...)} only when the JSON
 * file is intentionally located in the project directory but outside the
 * classpath.</p>
 *
 * <h2>JSON value types</h2>
 *
 * <p>The current implementation reads JSON values as strings using
 * {@code List<Map<String, String>>}. This is convenient for simple login
 * data. If the JSON later contains booleans, numbers, arrays, or nested
 * objects, a typed Java model should be introduced.</p>
 *
 * @see Gson
 * @see TypeToken
 */
public final class JsonDataProvider {

    /**
     * Shared Gson parser used to convert JSON text into Java objects.
     */
    private static final Gson GSON = new Gson();

    /**
     * Describes the expected JSON structure:
     *
     * <pre>{@code
     * List<Map<String, String>>
     * }</pre>
     *
     * <p>The JSON root must be an array. Each array element must be a JSON
     * object whose property names and values are represented as strings.</p>
     */
    private static final Type TEST_DATA_TYPE =
            new TypeToken<List<Map<String, String>>>() {
            }.getType();

    /**
     * Prevents instantiation because this utility class exposes static methods.
     */
    private JsonDataProvider() {
        // Prevent utility-class instantiation
    }

    /**
     * Reads JSON test data from the test runtime classpath.
     *
     * <p>For a Maven project, a file located at:</p>
     *
     * <pre>{@code
     * src/test/resources/testdata/loginData.json
     * }</pre>
     *
     * <p>must be referenced using:</p>
     *
     * <pre>{@code
     * testdata/loginData.json
     * }</pre>
     *
     * <p>Do not include {@code src/test/resources} in the resource name.</p>
     *
     * <p>This method can be called from a TestNG method annotated with
     * {@code @DataProvider} because it returns {@code Object[][]}.</p>
     *
     * @param resourceName the classpath-relative path of the JSON file
     *
     * @return a TestNG-compatible two-dimensional array. Each outer element
     *         represents one test invocation, and each inner element contains
     *         one map representing one JSON object
     *
     * @throws IOException if the JSON resource cannot be found, cannot be
     *                     read, contains invalid JSON, or contains no test data
     */
    public static Object[][] getJsonDataFromClasspath(
            String resourceName
    ) throws IOException {

        InputStream inputStream = JsonDataProvider.class
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

    /**
     * Reads JSON test data from a file inside the project directory.
     *
     * <p>The project directory is determined using the
     * {@code user.dir} system property. The supplied path parts are resolved
     * relative to that directory.</p>
     *
     * <p>For example, if the file exists at:</p>
     *
     * <pre>{@code
     * project-root/testdata/loginData.json
     * }</pre>
     *
     * <p>it can be loaded using:</p>
     *
     * <pre>{@code
     * getJsonDataFromProject(
     *         "testdata",
     *         "loginData.json"
     * );
     * }</pre>
     *
     * <p>This method is intended for files outside the Maven classpath.
     * For files under {@code src/test/resources}, prefer
     * {@link #getJsonDataFromClasspath(String)}.</p>
     *
     * @param first the first path element relative to the project directory
     * @param more additional path elements used to build the JSON path
     *
     * @return a TestNG-compatible two-dimensional array. Each outer element
     *         represents one test invocation, and each inner element contains
     *         one map representing one JSON object
     *
     * @throws IOException if the resolved path leaves the project directory,
     *                     the file does not exist, the file cannot be read,
     *                     the JSON is invalid, or no test data is available
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

    /**
     * Converts JSON content from a Reader into TestNG data-provider format.
     *
     * <p>The JSON root is expected to be a nonempty array of objects. Each
     * object is converted into one {@code Map<String, String>} and then
     * wrapped inside an {@code Object[]}.</p>
     *
     * <p>This method is shared by both public loading methods so that
     * classpath files and project files use the same parsing behaviour.</p>
     *
     * @param reader the reader containing JSON text
     * @param source a source description used in diagnostic messages
     *
     * @return a two-dimensional array containing one map per JSON object
     *
     * @throws IOException if the JSON is invalid, empty, or contains no test
     *                     data
     */
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