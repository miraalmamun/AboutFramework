package utilities;

import java.util.Locale;

public final class Property {

    private Property() {
        throw new AssertionError(
                "Property cannot be instantiated"
        );
    }

    /**
     * Reads a configuration value from:
     *
     * 1. JVM system property: -Dkey=value
     * 2. Operating-system environment variable
     * 3. Supplied default value
     *
     * Maven Surefire properties loaded from
     * configuration.properties are available as JVM system properties.
     */
    public static String get(
            String key,
            String defaultValue
    ) {
        String value = System.getProperty(key);

        if (isBlank(value)) {
            value = System.getenv(key);
        }

        if (isBlank(value)) {
            value = System.getenv(toEnvironmentVariableName(key));
        }

        return isBlank(value)
                ? defaultValue
                : value.strip();
    }

    public static boolean getBoolean(
            String key,
            boolean defaultValue
    ) {
        String value = get(
                key,
                Boolean.toString(defaultValue)
        );

        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> throw new IllegalArgumentException(
                    "Property '" + key
                            + "' must be true or false, but was: "
                            + value
            );
        };
    }

    /**
     * Temporary compatibility method for existing code.
     *
     * Prefer get(...) because this method may read a system property
     * or an operating-system environment variable.
     */
    public static String getEnvironmentVariable(
            String key
    ) {
        return get(key, null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String toEnvironmentVariableName(
            String key
    ) {
        return key
                .replaceAll(
                        "([a-z0-9])([A-Z])",
                        "$1_$2"
                )
                .replace('.', '_')
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }
}