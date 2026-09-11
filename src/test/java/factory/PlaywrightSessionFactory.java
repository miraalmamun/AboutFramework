package factory;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.util.Locale;
import java.util.Map;

/**
 * Creates a completely isolated PlaywrightSession for a TestNG test method.
 *
 * <p>Supported Maven runtime properties:</p>
 * <ul>
 *   <li>-Dbrowser=chromium|firefox|webkit (default: chromium)</li>
 *   <li>-Dplaywright.debug=off|api|browser|all|api,browser (default: off)</li>
 *   <li>-Dheadless=true|false (default: true)</li>
 *   <li>-DslowMo=milliseconds (default: 0)</li>
 *   <li>-Dbrowser.launchTimeout=milliseconds (default: 30000)</li>
 * </ul>
 */
public final class PlaywrightSessionFactory {

    private static final String BROWSER_PROPERTY = "browser";
    private static final String DEBUG_PROPERTY = "playwright.debug";
    private static final String HEADLESS_PROPERTY = "headless";
    private static final String SLOW_MO_PROPERTY = "slowMo";
    private static final String LAUNCH_TIMEOUT_PROPERTY =
            "browser.launchTimeout";

    private static final String DEFAULT_BROWSER = "chromium";
    private static final boolean DEFAULT_HEADLESS = true;
    private static final double DEFAULT_SLOW_MO_MS = 0.0;
    private static final double DEFAULT_LAUNCH_TIMEOUT_MS = 30_000.0;

    private PlaywrightSessionFactory() {
        throw new AssertionError(
                "PlaywrightSessionFactory cannot be instantiated"
        );
    }

    /**
     * Reads Maven -D properties and creates Playwright, Browser,
     * BrowserContext, and Page for one isolated test session.
     */
    public static PlaywrightSession createSession() {

        BrowserName browserName = BrowserName.from(
                System.getProperty(BROWSER_PROPERTY, DEFAULT_BROWSER)
        );

        DebugMode debugMode = DebugMode.from(
                System.getProperty(DEBUG_PROPERTY, "off")
        );

        boolean headless = readBoolean(
                HEADLESS_PROPERTY,
                DEFAULT_HEADLESS
        );

        double slowMo = readNonNegativeDouble(
                SLOW_MO_PROPERTY,
                DEFAULT_SLOW_MO_MS
        );

        double launchTimeout = readPositiveDouble(
                LAUNCH_TIMEOUT_PROPERTY,
                DEFAULT_LAUNCH_TIMEOUT_MS
        );

        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;

        try {
            Playwright.CreateOptions createOptions =
                    new Playwright.CreateOptions()
                            .setEnv(
                                    Map.of(
                                            "DEBUG",
                                            debugMode.driverValue()
                                    )
                            );

            playwright = Playwright.create(createOptions);

            BrowserType browserType = switch (browserName) {
                case CHROMIUM -> playwright.chromium();
                case FIREFOX -> playwright.firefox();
                case WEBKIT -> playwright.webkit();
            };

            BrowserType.LaunchOptions launchOptions =
                    new BrowserType.LaunchOptions()
                            .setHeadless(headless)
                            .setSlowMo(slowMo)
                            .setTimeout(launchTimeout);

            browser = browserType.launch(launchOptions);

            Browser.NewContextOptions contextOptions =
                    new Browser.NewContextOptions()
                            .setViewportSize(1920, 1080)
                            .setLocale("en-US")
                            .setTimezoneId("America/New_York");

            context = browser.newContext(contextOptions);
            Page page = context.newPage();

            return new PlaywrightSession(
                    playwright,
                    browser,
                    context,
                    page
            );

        } catch (RuntimeException | Error startupException) {
            closeAfterStartupFailure(
                    context,
                    browser,
                    playwright,
                    startupException
            );

            throw startupException;
        }
    }

    private static void closeAfterStartupFailure(
            BrowserContext context,
            Browser browser,
            Playwright playwright,
            Throwable startupException) {

        if (context != null) {
            closeSafely(context::close, startupException);
        }

        if (browser != null) {
            closeSafely(browser::close, startupException);
        }

        if (playwright != null) {
            closeSafely(playwright::close, startupException);
        }
    }

    private static void closeSafely(
            Runnable closeAction,
            Throwable originalException) {

        try {
            closeAction.run();
        } catch (RuntimeException closeException) {
            originalException.addSuppressed(closeException);
        }
    }

    private static boolean readBoolean(
            String propertyName,
            boolean defaultValue) {

        String rawValue = System.getProperty(propertyName);

        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(
                    "-D" + propertyName
                            + " must be true or false, but was: "
                            + rawValue
            );
        };
    }

    private static double readNonNegativeDouble(
            String propertyName,
            double defaultValue) {

        double value = readDouble(propertyName, defaultValue);

        if (value < 0) {
            throw new IllegalArgumentException(
                    "-D" + propertyName
                            + " must be zero or greater, but was: "
                            + value
            );
        }

        return value;
    }

    private static double readPositiveDouble(
            String propertyName,
            double defaultValue) {

        double value = readDouble(propertyName, defaultValue);

        if (value <= 0) {
            throw new IllegalArgumentException(
                    "-D" + propertyName
                            + " must be greater than zero, but was: "
                            + value
            );
        }

        return value;
    }

    private static double readDouble(
            String propertyName,
            double defaultValue) {

        String rawValue = System.getProperty(propertyName);

        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        try {
            double value = Double.parseDouble(rawValue.trim());

            if (!Double.isFinite(value)) {
                throw new NumberFormatException(
                        "Value must be finite"
                );
            }

            return value;

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "-D" + propertyName
                            + " requires a valid number, but was: "
                            + rawValue,
                    exception
            );
        }
    }

    private enum BrowserName {
        CHROMIUM,
        FIREFOX,
        WEBKIT;

        private static BrowserName from(String value) {
            try {
                return valueOf(
                        value.trim().toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Unsupported -D" + BROWSER_PROPERTY
                                + "='" + value + "'. Allowed values: "
                                + "chromium, firefox, webkit",
                        exception
                );
            }
        }
    }

    private enum DebugMode {
        OFF(""),
        API("pw:api"),
        BROWSER("pw:browser"),
        ALL("pw:api,pw:browser");

        private final String driverValue;

        DebugMode(String driverValue) {
            this.driverValue = driverValue;
        }

        private String driverValue() {
            return driverValue;
        }

        private static DebugMode from(String value) {
            String normalized = value
                    .trim()
                    .toLowerCase(Locale.ROOT)
                    .replace(" ", "");

            return switch (normalized) {
                case "", "off", "false", "none" -> OFF;

                case "api", "pw:api" -> API;

                case "browser", "pw:browser", "pw:browser*" ->
                        BROWSER;

                case "all",
                     "true",
                     "api,browser",
                     "browser,api",
                     "pw:api,pw:browser",
                     "pw:browser,pw:api",
                     "pw:api,pw:browser*",
                     "pw:browser*,pw:api" -> ALL;

                default -> throw new IllegalArgumentException(
                        "Unsupported -D" + DEBUG_PROPERTY
                                + "='" + value + "'. Allowed values: "
                                + "off, api, browser, all, api,browser"
                );
            };
        }
    }
}
