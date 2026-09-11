package factory;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Thread-safe lifecycle facade for Playwright Java + TestNG.
 *
 * <p>Each TestNG test invocation calls {@link #start()} on its executing
 * thread and {@link #quit()} on the same thread. The ThreadLocal keeps every
 * test's Playwright, Browser, BrowserContext, and Page isolated from tests
 * running concurrently.</p>
 *
 * <p>Supported Maven properties:</p>
 * <ul>
 *   <li>-Dbrowser=chromium|firefox|webkit (default: chromium)</li>
 *   <li>-Dplaywright.debug=off|api|browser|all|api,browser (default: off)</li>
 *   <li>-Dheadless=true|false (default: true)</li>
 *   <li>-DslowMo=milliseconds (default: 0)</li>
 *   <li>-Dbrowser.launchTimeout=milliseconds (default: 30000)</li>
 *   <li>-DbaseUrl=<a href="https://example.test">...</a> (default: empty)</li>
 *   <li>-Dviewport.width=1920 (default: 1920)</li>
 *   <li>-Dviewport.height=1080 (default: 1080)</li>
 *   <li>-DignoreHTTPSErrors=true|false (default: false)</li>
 * </ul>
 */
public final class PlaywrightFactory {

    private static final ThreadLocal<TestState> STATE = new ThreadLocal<>();

    private static final String BROWSER_PROPERTY = "browser";
    private static final String DEBUG_PROPERTY = "playwright.debug";
    private static final String HEADLESS_PROPERTY = "headless";
    private static final String SLOW_MO_PROPERTY = "slowMo";
    private static final String LAUNCH_TIMEOUT_PROPERTY = "browser.launchTimeout";
    private static final String BASE_URL_PROPERTY = "baseUrl";
    private static final String VIEWPORT_WIDTH_PROPERTY = "viewport.width";
    private static final String VIEWPORT_HEIGHT_PROPERTY = "viewport.height";
    private static final String IGNORE_HTTPS_ERRORS_PROPERTY = "ignoreHTTPSErrors";

    private PlaywrightFactory() {
        throw new AssertionError("PlaywrightFactory cannot be instantiated");
    }

    /** Creates one isolated Playwright stack for the current test thread. */
    public static void start() {
        if (isStarted()) {
            throw new IllegalStateException(
                    "Playwright is already initialized for thread: "
                            + Thread.currentThread().getName()
            );
        }

        Settings settings = Settings.fromSystemProperties();
        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;

        try {
            playwright = createPlaywright(settings.debugMode());
            browser = launchBrowser(playwright, settings);
            context = createContext(browser, settings);
            Page page = context.newPage();

            STATE.set(new TestState(playwright, browser, context, page, settings));

        } catch (RuntimeException | Error startupFailure) {
            closeAfterStartupFailure(
                    context,
                    browser,
                    playwright,
                    startupFailure
            );
            throw startupFailure;
        }
    }

    public static Page page() {
        return requireState().page;
    }

    public static BrowserContext context() {
        return requireState().primaryContext;
    }

    public static Browser browser() {
        return requireState().browser;
    }

    public static Playwright playwright() {
        return requireState().playwright;
    }

    /**
     * Creates and tracks another isolated context in the same browser.
     * Use for multi-user tests. All tracked contexts are closed by quit().
     */
    public static BrowserContext newContext() {
        TestState state = requireState();
        BrowserContext context = createContext(state.browser, state.settings);
        state.contexts.addFirst(context);
        return context;
    }

    /** Closes every resource for the current thread and removes ThreadLocal. */
    public static void quit() {
        TestState state = STATE.get();

        if (state == null) {
            return;
        }

        Throwable failure = null;

        try {
            while (!state.contexts.isEmpty()) {
                BrowserContext context = state.contexts.removeFirst();
                failure = closeResource(context::close, failure);
            }

            failure = closeResource(state.browser::close, failure);
            failure = closeResource(state.playwright::close, failure);

        } finally {
            STATE.remove();
        }

        rethrowUnchecked(failure);
    }

    /** Returns whether the current thread owns an active Playwright stack. */
    public static boolean isStarted() {
        return STATE.get() != null;
    }

    private static Playwright createPlaywright(DebugMode debugMode) {
        /*
         * setEnv() supplies the environment for the Playwright driver process.
         * Start with the current environment so Jenkins variables such as proxy,
         * certificate and temporary-directory settings are not discarded.
         * An empty DEBUG value guarantees that the default run has no PW logs.
         */
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("DEBUG", debugMode.driverValue());

        Playwright.CreateOptions options = new Playwright.CreateOptions()
                .setEnv(environment);
        return Playwright.create(options);
    }

    private static Browser launchBrowser(
            Playwright playwright,
            Settings settings) {

        BrowserType browserType = switch (settings.browserName()) {
            case CHROMIUM -> playwright.chromium();
            case FIREFOX -> playwright.firefox();
            case WEBKIT -> playwright.webkit();
        };

        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(settings.headless())
                .setSlowMo(settings.slowMoMs())
                .setTimeout(settings.launchTimeoutMs());

        return browserType.launch(options);
    }

    private static BrowserContext createContext(
            Browser browser,
            Settings settings) {

        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setViewportSize(
                        settings.viewportWidth(),
                        settings.viewportHeight()
                )
                .setIgnoreHTTPSErrors(settings.ignoreHTTPSErrors());

        if (!settings.baseUrl().isBlank()) {
            options.setBaseURL(settings.baseUrl());
        }

        return browser.newContext(options);
    }

    private static TestState requireState() {
        TestState state = STATE.get();

        if (state == null) {
            throw new IllegalStateException(
                    "Playwright is not initialized for thread: "
                            + Thread.currentThread().getName()
                            + ". Call PlaywrightFactory.start() from @BeforeMethod."
            );
        }

        return state;
    }

    private static void closeAfterStartupFailure(
            BrowserContext context,
            Browser browser,
            Playwright playwright,
            Throwable startupFailure) {

        if (context != null) {
            closeSafely(context::close, startupFailure);
        }
        if (browser != null) {
            closeSafely(browser::close, startupFailure);
        }
        if (playwright != null) {
            closeSafely(playwright::close, startupFailure);
        }
    }

    private static void closeSafely(
            Runnable closeAction,
            Throwable originalFailure) {

        try {
            closeAction.run();
        } catch (RuntimeException | Error closeFailure) {
            originalFailure.addSuppressed(closeFailure);
        }
    }

    private static Throwable closeResource(
            Runnable closeAction,
            Throwable previousFailure) {

        try {
            closeAction.run();
        } catch (RuntimeException | Error currentFailure) {
            if (previousFailure == null) {
                return currentFailure;
            }
            previousFailure.addSuppressed(currentFailure);
        }
        return previousFailure;
    }

    private static void rethrowUnchecked(Throwable failure) {
        switch (failure) {
            case null -> {
                // Nothing failed during cleanup.
            }
            case RuntimeException runtimeException -> throw runtimeException;
            case Error error -> throw error;
            default -> throw new IllegalStateException(
                    "Unexpected checked exception during Playwright cleanup",
                    failure
            );
        }
    }

    private enum BrowserName {
        CHROMIUM,
        FIREFOX,
        WEBKIT;

        private static BrowserName from(String value) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);

            return switch (normalized) {
                case "chromium" -> CHROMIUM;
                case "firefox" -> FIREFOX;
                case "webkit" -> WEBKIT;
                default -> throw new IllegalArgumentException(
                        "Unsupported -D" + BROWSER_PROPERTY + "='" + value
                                + "'. Allowed values: chromium, firefox, webkit"
                );
            };
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
            String normalized = value.trim()
                    .toLowerCase(Locale.ROOT)
                    .replace(" ", "");

            return switch (normalized) {
                case "", "off", "false", "none" -> OFF;
                case "api", "pw:api" -> API;
                case "browser", "pw:browser", "pw:browser*" -> BROWSER;
                case "all", "true", "api,browser", "browser,api",
                     "pw:api,pw:browser", "pw:browser,pw:api",
                     "pw:api,pw:browser*", "pw:browser*,pw:api" -> ALL;
                default -> throw new IllegalArgumentException(
                        "Unsupported -D" + DEBUG_PROPERTY + "='" + value
                                + "'. Allowed values: "
                                + "off, api, browser, all, api,browser"
                );
            };
        }
    }

    private record Settings(
            BrowserName browserName,
            DebugMode debugMode,
            boolean headless,
            double slowMoMs,
            double launchTimeoutMs,
            String baseUrl,
            int viewportWidth,
            int viewportHeight,
            boolean ignoreHTTPSErrors) {

        private static Settings fromSystemProperties() {
            return new Settings(
                    BrowserName.from(System.getProperty(BROWSER_PROPERTY, "chromium")),
                    DebugMode.from(System.getProperty(DEBUG_PROPERTY, "off")),
                    readBoolean(HEADLESS_PROPERTY, true),
                    readNonNegativeDouble(SLOW_MO_PROPERTY, 0),
                    readPositiveDouble(LAUNCH_TIMEOUT_PROPERTY, 30_000),
                    System.getProperty(BASE_URL_PROPERTY, "").trim(),
                    readPositiveInt(VIEWPORT_WIDTH_PROPERTY, 1920),
                    readPositiveInt(VIEWPORT_HEIGHT_PROPERTY, 1080),
                    readBoolean(IGNORE_HTTPS_ERRORS_PROPERTY, false)
            );
        }
    }

    private static final class TestState {
        private final Playwright playwright;
        private final Browser browser;
        private final BrowserContext primaryContext;
        private final Page page;
        private final Settings settings;
        private final Deque<BrowserContext> contexts = new ArrayDeque<>();

        private TestState(
                Playwright playwright,
                Browser browser,
                BrowserContext primaryContext,
                Page page,
                Settings settings) {

            this.playwright = playwright;
            this.browser = browser;
            this.primaryContext = primaryContext;
            this.page = page;
            this.settings = settings;
            this.contexts.addFirst(primaryContext);
        }
    }

    private static boolean readBoolean(String name, boolean defaultValue) {
        String rawValue = System.getProperty(name);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(
                    "-D" + name + " must be true or false, but was: " + rawValue
            );
        };
    }

    private static int readPositiveInt(String name, int defaultValue) {
        String rawValue = System.getProperty(name);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        try {
            int value = Integer.parseInt(rawValue.trim());
            if (value <= 0) {
                throw new NumberFormatException("Value must be greater than zero");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "-D" + name + " requires a positive integer, but was: "
                            + rawValue,
                    exception
            );
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static double readNonNegativeDouble(
            String name,
            double defaultValue) {

        double value = readFiniteDouble(name, defaultValue);
        if (value < 0) {
            throw new IllegalArgumentException(
                    "-D" + name + " must be zero or greater, but was: " + value
            );
        }
        return value;
    }

    @SuppressWarnings("SameParameterValue")
    private static double readPositiveDouble(
            String name,
            double defaultValue) {

        double value = readFiniteDouble(name, defaultValue);
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "-D" + name + " must be greater than zero, but was: " + value
            );
        }
        return value;
    }

    private static double readFiniteDouble(String name, double defaultValue) {
        String rawValue = System.getProperty(name);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        try {
            double value = Double.parseDouble(rawValue.trim());
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("Value must be finite");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "-D" + name + " requires a valid number, but was: " + rawValue,
                    exception
            );
        }
    }
}
