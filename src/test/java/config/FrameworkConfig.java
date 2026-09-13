package config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * Provides one central, read-only API for framework configuration.
 *
 * <p>This class allows test code and framework utilities to read configuration
 * without knowing whether the test was started by Maven, Jenkins, or directly
 * from IntelliJ.</p>
 *
 * <h2>Why this class is needed</h2>
 *
 * <p>Maven Surefire can load a profile file through
 * {@code systemPropertiesFile}. That works when the test is started with a
 * Maven command, for example:</p>
 *
 * <pre>{@code
 * mvn -Ptest clean test
 * mvn -Pdev clean test
 * }</pre>
 *
 * <p>However, clicking IntelliJ's green TestNG Run icon does not execute Maven
 * Surefire. Therefore, this class can load the same file directly as a
 * fallback:</p>
 *
 * <pre>
 * Environment/&lt;environment&gt;/configuration.properties
 * </pre>
 *
 * <h2>Environment selection</h2>
 *
 * <ul>
 *   <li>Maven: select the environment with {@code -Ptest}, {@code -Pdev},
 *       {@code -Puat}, {@code -Pstage}, or {@code -Pprod}.</li>
 *   <li>Direct IntelliJ TestNG run: add {@code -Dapp.env=dev} to the Run
 *       Configuration's VM options.</li>
 *   <li>If nothing is supplied, the environment defaults to {@code test}.</li>
 * </ul>
 *
 * <p>Do not use {@code -Dapp.env=dev} to switch a Maven profile. With Maven,
 * use {@code -Pdev} so the selected profile and
 * {@code systemPropertiesFile} path always refer to the same environment.</p>
 *
 * <h2>Value lookup order</h2>
 *
 * <ol>
 *   <li>JVM system property</li>
 *   <li>Operating-system or Jenkins environment variable</li>
 *   <li>Selected profile's {@code configuration.properties} file, when a
 *       direct-file fallback is required</li>
 *   <li>Default supplied by the calling Java method</li>
 * </ol>
 *
 * <p>During Maven execution, the values loaded by
 * {@code systemPropertiesFile} are JVM system properties. Surefire is
 * configured to promote Maven user properties, so a runtime
 * {@code -Dbrowser=firefox} value overrides {@code browser=chromium} from the
 * file.</p>
 *
 * <h2>Basic Java examples</h2>
 *
 * <pre>{@code
 * String environment = FrameworkConfig.environment();
 * String browser = FrameworkConfig.text("browser", "chromium");
 * boolean headless = FrameworkConfig.booleanValue("headless", true);
 * int width = FrameworkConfig.positiveInt("viewport.width", 1920);
 * String serviceUrl = FrameworkConfig.requiredText("MIR.SERVICE.API.URL");
 * }</pre>
 *
 * <p>The selected environment is fixed when this class is initialized.
 * Environment selection must therefore be supplied before the test JVM
 * starts, through a Maven profile, JVM option, or operating-system environment
 * variable.</p>
 */
public final class FrameworkConfig {

    /** Canonical JVM property used to select a direct-file environment. */
    private static final String APP_ENV_PROPERTY = "app.env";

    /** Environment used when Maven, IntelliJ, and the OS provide no selection. */
    private static final String DEFAULT_ENVIRONMENT = "test";

    /** Immutable environment selection for the lifetime of the current JVM. */
    private static final String SELECTED_ENVIRONMENT = selectEnvironment();

    /**
     * Prevents construction because this class contains only static utility
     * methods.
     *
     * @throws AssertionError always, if reflection attempts construction
     */
    private FrameworkConfig() {
        throw new AssertionError(
                "FrameworkConfig cannot be instantiated"
        );
    }

    /**
     * Returns the environment selected for the current test JVM.
     *
     * <p>Maven examples:</p>
     *
     * <pre>{@code
     * mvn -Ptest clean test  // returns "test"
     * mvn -Pdev clean test   // returns "dev"
     * }</pre>
     *
     * <p>Direct IntelliJ example:</p>
     *
     * <pre>
     * Run Configuration -&gt; VM options -&gt; -Dapp.env=dev
     * </pre>
     *
     * @return selected nonblank environment name
     */
    public static String environment() {
        return SELECTED_ENVIRONMENT;
    }

    /**
     * Reads a text configuration value.
     *
     * <p>The lookup checks JVM system properties first, followed by environment
     * variables, the selected profile file, and finally
     * {@code defaultValue}. Leading and trailing whitespace is removed from a
     * nonblank configured value.</p>
     *
     * <p>Environment-variable names may use uppercase underscores:</p>
     *
     * <ul>
     *   <li>{@code baseUrl} becomes {@code BASE_URL}</li>
     *   <li>{@code browser.launchTimeout} becomes
     *       {@code BROWSER_LAUNCH_TIMEOUT}</li>
     *   <li>{@code ignoreHTTPSErrors} becomes
     *       {@code IGNORE_HTTPS_ERRORS}</li>
     * </ul>
     *
     * <p>Example:</p>
     *
     * <pre>{@code
     * String browser = FrameworkConfig.text("browser", "chromium");
     * String baseUrl = FrameworkConfig.text("baseUrl", "");
     * }</pre>
     *
     * <p>A blank configured value is treated as missing. A blank default is
     * preserved. This is important for {@code baseUrl}, where an empty string
     * means that tests will use absolute navigation URLs.</p>
     *
     * <p>This method deliberately requires a nonnull default and therefore
     * never returns {@code null}. Use {@link #requiredText(String)} when a
     * setting must exist and the framework should fail if it is absent. This
     * explicit contract also lets Java and IntelliJ prove that callers can
     * safely invoke methods on the returned String.</p>
     *
     * @param key configuration key, such as {@code browser} or {@code baseUrl}
     * @param defaultValue nonnull value returned when no nonblank configured
     *                     value exists; a blank String is allowed
     * @return resolved value or the supplied default; never {@code null}
     * @throws NullPointerException if {@code key} or {@code defaultValue} is
     *                              {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     * @throws IllegalStateException if the fallback profile file is required
     *                               but cannot be found or read
     */
    public static String text(
            String key,
            String defaultValue
    ) {
        /*
         * A blank default can be meaningful. For example, an empty baseUrl
         * means the test must navigate with an absolute URL.
         */
        String fallbackValue = Objects.requireNonNull(
                defaultValue,
                "Default configuration value cannot be null"
        ).strip();

        return Objects.requireNonNullElse(
                configuredText(key),
                fallbackValue
        );
    }

    /**
     * Resolves a nonblank configured value without applying a default.
     *
     * <p>This nullable lookup is private so the public API has two clear
     * choices: {@link #text(String, String)} always returns a value, while
     * {@link #requiredText(String)} throws when no value exists.</p>
     *
     * @param key configuration key
     * @return configured value, or {@code null} when no source supplies one
     */
    private static String configuredText(String key) {
        String validatedKey = validateKey(key);

        String systemValue = firstNonBlank(
                System.getProperty(validatedKey),
                propertyIgnoreCase(
                        System.getProperties(),
                        validatedKey
                )
        );

        if (systemValue != null) {
            return systemValue;
        }

        String environmentValue = firstNonBlank(
                System.getenv(validatedKey),
                System.getenv(toEnvironmentKey(validatedKey))
        );

        if (environmentValue != null) {
            return environmentValue;
        }

        return firstNonBlank(
                propertyIgnoreCase(
                        profileProperties(),
                        validatedKey
                )
        );
    }

    /**
     * Reads a mandatory, nonblank text value.
     *
     * <p>Use this method when continuing without the setting would make the
     * test invalid. For example:</p>
     *
     * <pre>{@code
     * String apiUrl =
     *         FrameworkConfig.requiredText("MIR.SERVICE.API.URL");
     * }</pre>
     *
     * @param key required configuration key
     * @return resolved nonblank value
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     * @throws IllegalStateException if the value is missing/blank or the
     *                               profile file cannot be read
     */
    public static String requiredText(String key) {
        String value = configuredText(key);

        if (value == null) {
            throw new IllegalStateException(
                    "Required configuration is missing: " + key
            );
        }

        return value;
    }

    /**
     * Reads and validates a boolean configuration value.
     *
     * <p>Accepted true values are {@code true}, {@code yes}, and {@code 1}.
     * Accepted false values are {@code false}, {@code no}, and {@code 0}.
     * Matching is case-insensitive.</p>
     *
     * <pre>{@code
     * boolean headless =
     *         FrameworkConfig.booleanValue("headless", true);
     * }</pre>
     *
     * @param key configuration key
     * @param defaultValue value used when the key is not configured
     * @return parsed boolean
     * @throws IllegalArgumentException if the configured text is not an
     *                                  accepted boolean representation
     */
    public static boolean booleanValue(
            String key,
            boolean defaultValue
    ) {
        String rawValue = text(
                key,
                Boolean.toString(defaultValue)
        );

        return switch (rawValue.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> throw new IllegalArgumentException(
                    "Configuration '" + key
                            + "' must be true or false, but was: "
                            + rawValue
            );
        };
    }

    /**
     * Reads an integer that must be greater than zero.
     *
     * <p>This is appropriate for settings such as viewport width, viewport
     * height, or a thread count:</p>
     *
     * <pre>{@code
     * int width =
     *         FrameworkConfig.positiveInt("viewport.width", 1920);
     * }</pre>
     *
     * @param key configuration key
     * @param defaultValue positive default value
     * @return configured/default positive integer
     * @throws IllegalArgumentException if the value is not an integer or is
     *                                  zero/negative
     */
    public static int positiveInt(
            String key,
            int defaultValue
    ) {
        String rawValue = text(
                key,
                Integer.toString(defaultValue)
        );

        try {
            int value = Integer.parseInt(rawValue);

            if (value <= 0) {
                throw new NumberFormatException(
                        "Value must be greater than zero"
                );
            }

            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Configuration '" + key
                            + "' must be a positive integer, but was: "
                            + rawValue,
                    exception
            );
        }
    }

    /**
     * Reads a finite decimal value that must be zero or greater.
     *
     * <p>This is used for {@code slowMo}, where zero disables the delay:</p>
     *
     * <pre>{@code
     * double slowMo =
     *         FrameworkConfig.nonNegativeDouble("slowMo", 0);
     * }</pre>
     *
     * @param key configuration key
     * @param defaultValue nonnegative default value
     * @return configured/default finite value
     * @throws IllegalArgumentException if the value is invalid, infinite,
     *                                  NaN, or negative
     */
    public static double nonNegativeDouble(
            String key,
            double defaultValue
    ) {
        double value = finiteDouble(key, defaultValue);

        if (value < 0) {
            throw new IllegalArgumentException(
                    "Configuration '" + key
                            + "' must be zero or greater, but was: "
                            + value
            );
        }

        return value;
    }

    /**
     * Reads a finite decimal value that must be greater than zero.
     *
     * <p>This is used for the browser launch timeout:</p>
     *
     * <pre>{@code
     * double timeout = FrameworkConfig.positiveDouble(
     *         "browser.launchTimeout",
     *         30_000
     * );
     * }</pre>
     *
     * @param key configuration key
     * @param defaultValue positive default value
     * @return configured/default finite positive value
     * @throws IllegalArgumentException if the value is invalid, infinite,
     *                                  NaN, zero, or negative
     */
    public static double positiveDouble(
            String key,
            double defaultValue
    ) {
        double value = finiteDouble(key, defaultValue);

        if (value <= 0) {
            throw new IllegalArgumentException(
                    "Configuration '" + key
                            + "' must be greater than zero, but was: "
                            + value
            );
        }

        return value;
    }

    /**
     * Returns the normalized absolute project directory.
     *
     * <p>The method uses the JVM's {@code user.dir}. Maven and a normally
     * configured IntelliJ test run use the project/module directory as this
     * value.</p>
     *
     * @return normalized absolute project root
     */
    public static Path projectRoot() {
        return Path.of(
                System.getProperty("user.dir", ".")
        ).toAbsolutePath().normalize();
    }

    /**
     * Resolves a path safely inside the project directory.
     *
     * <p>Example:</p>
     *
     * <pre>{@code
     * Path downloadFile = FrameworkConfig.projectPath(
     *         "target",
     *         "downloads",
     *         "report.pdf"
     * );
     * }</pre>
     *
     * <p>A path such as {@code ../../outside.txt} is rejected after
     * normalization because it would leave the project directory.</p>
     *
     * @param first first path element
     * @param more remaining path elements
     * @return normalized absolute path inside the project
     * @throws NullPointerException if a path argument is {@code null}
     * @throws IllegalArgumentException if the resolved path leaves the project
     */
    public static Path projectPath(
            String first,
            String... more
    ) {
        Objects.requireNonNull(first, "First path element cannot be null");
        Objects.requireNonNull(more, "Additional path elements cannot be null");

        Path root = projectRoot().toAbsolutePath().normalize();
        Path target = root.resolve(Path.of(first, more)).normalize();

        if (!target.startsWith(root)) {
            throw new IllegalArgumentException(
                    "Path must remain inside the project directory: "
                            + target
            );
        }

        Path canonicalRoot;

        try {
            canonicalRoot = root.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to resolve the project directory: " + root,
                    exception
            );
        }

        Path canonicalTarget = canonicalRoot;
        Path relativeTarget = root.relativize(target);

        for (Path segment : relativeTarget) {
            canonicalTarget = canonicalTarget.resolve(segment).normalize();
            if (Files.exists(canonicalTarget)) {
                try {
                    canonicalTarget = canonicalTarget.toRealPath();
                } catch (IOException exception) {
                    throw new IllegalStateException(
                            "Unable to resolve path inside the project: "
                                    + target,
                            exception
                    );
                }
            }
        }

        if (!canonicalTarget.startsWith(canonicalRoot)) {
            throw new IllegalArgumentException(
                    "Path must remain inside the project directory: "
                            + target
            );
        }

        return canonicalTarget;
    }

    /**
     * Parses a configured number and rejects NaN/infinity.
     *
     * @param key configuration key
     * @param defaultValue number used when the key is missing
     * @return finite parsed number
     * @throws IllegalArgumentException if parsing/finite validation fails
     */
    private static double finiteDouble(
            String key,
            double defaultValue
    ) {
        String rawValue = text(
                key,
                Double.toString(defaultValue)
        );

        try {
            double value = Double.parseDouble(rawValue);

            if (!Double.isFinite(value)) {
                throw new NumberFormatException(
                        "Value must be finite"
                );
            }

            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Configuration '" + key
                            + "' must be a valid number, but was: "
                            + rawValue,
                    exception
            );
        }
    }

    /**
     * Returns fallback profile properties through initialization-on-demand.
     *
     * <p>This avoids reading the file immediately. During Maven execution,
     * Surefire has normally already placed requested file values in JVM system
     * properties. A direct file read is performed only when lookup reaches the
     * fallback level.</p>
     *
     * @return profile properties loaded for the selected environment
     */
    private static Properties profileProperties() {
        return ProfilePropertiesHolder.INSTANCE;
    }

    /**
     * Loads one environment profile file using UTF-8.
     *
     * @return loaded properties
     * @throws IllegalStateException if the file is missing or cannot be read
     */
    private static Properties loadProfileProperties() {
        Path file = projectPath(
                "Environment",
                SELECTED_ENVIRONMENT,
                "configuration.properties"
        );

        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException(
                    "Configuration file was not found: " + file
            );
        }

        Properties properties = new Properties();

        try (Reader reader = Files.newBufferedReader(
                file,
                StandardCharsets.UTF_8
        )) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read configuration file: " + file,
                    exception
            );
        }
    }

    /**
     * Selects the environment before any profile values are requested.
     *
     * @return selected environment or {@code test}
     */
    private static String selectEnvironment() {
        String configuredEnvironment = firstNonBlank(
                System.getProperty(APP_ENV_PROPERTY),
                System.getProperty("environment"),
                System.getProperty("ENVIRONMENT"),
                System.getenv("APP_ENV"),
                System.getenv("ENVIRONMENT")
        );

        if (configuredEnvironment == null) {
            return normalizeEnvironment(DEFAULT_ENVIRONMENT);
        }

        return normalizeEnvironment(configuredEnvironment);
    }

    /**
     * Normalizes and validates a project environment identifier.
     *
     * @param environment raw environment value from JVM or OS configuration
     * @return lower-case environment name safe for directory lookup
     */
    private static String normalizeEnvironment(String environment) {
        String validatedEnvironment = Objects.requireNonNull(
                environment,
                "Environment cannot be null"
        );

        String normalizedEnvironment = validatedEnvironment.strip();

        if (normalizedEnvironment.isEmpty()) {
            throw new IllegalArgumentException(
                    "Environment cannot be blank"
            );
        }

        String lowerCaseEnvironment = normalizedEnvironment
                .toLowerCase(Locale.ROOT);

        if (!lowerCaseEnvironment.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    "Invalid environment name: " + validatedEnvironment
            );
        }

        return lowerCaseEnvironment;
    }

    /**
     * Reads a Java property by exact key first and case-insensitive key second.
     *
     * <p>Exact spelling is still recommended. The fallback exists to support
     * legacy files containing keys such as {@code App.NAME}.</p>
     *
     * @param properties properties to search
     * @param key requested key
     * @return matching value or {@code null}
     */
    private static String propertyIgnoreCase(
            Properties properties,
            String key
    ) {
        String exactValue = properties.getProperty(key);

        if (exactValue != null) {
            return exactValue;
        }

        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.equalsIgnoreCase(key)) {
                return properties.getProperty(propertyName);
            }
        }

        return null;
    }

    /**
     * Converts a Java property key to an environment-variable key.
     *
     * <p>Examples:</p>
     *
     * <ul>
     *   <li>{@code slowMo} to {@code SLOW_MO}</li>
     *   <li>{@code viewport.width} to {@code VIEWPORT_WIDTH}</li>
     *   <li>{@code ignoreHTTPSErrors} to {@code IGNORE_HTTPS_ERRORS}</li>
     * </ul>
     *
     * @param key Java-style property key
     * @return uppercase underscore environment key
     */
    private static String toEnvironmentKey(String key) {
        return key
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('.', '_')
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }

    /**
     * Rejects null/blank keys before property lookup.
     *
     * @param key key to validate
     * @return the same key after validation; never {@code null}
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     */
    private static String validateKey(String key) {
        String validatedKey = Objects.requireNonNull(
                key,
                "Configuration key cannot be null"
        );

        if (validatedKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Configuration key cannot be blank"
            );
        }

        return validatedKey;
    }

    /**
     * Returns the first nonnull, nonblank value after stripping whitespace.
     *
     * @param values values in priority order
     * @return first usable value or {@code null}
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }

        return null;
    }

    /**
     * Lazy holder for direct-file fallback properties.
     *
     * <p>The JVM initializes this nested class only when
     * {@link #profileProperties()} is first called.</p>
     */
    private static final class ProfilePropertiesHolder {
        private static final Properties INSTANCE =
                loadProfileProperties();
    }
}
