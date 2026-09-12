package factory;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import config.FrameworkConfig;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Creates, stores, exposes, and closes the Playwright resources used by one
 * TestNG test-method invocation.
 *
 * <h2>Why a factory is useful</h2>
 *
 * <p>Playwright can create browsers and contexts without a factory. This class
 * is a framework-level lifecycle manager that prevents every test class from
 * repeating browser-selection, configuration, parallel-isolation, and cleanup
 * code.</p>
 *
 * <p>The resource hierarchy owned by one test invocation is:</p>
 *
 * <pre>
 * Playwright
 *   └── Browser (Chromium, Firefox, or WebKit)
 *         └── Primary BrowserContext
 *               └── Primary Page
 * </pre>
 *
 * <h2>BrowserContext is the test session</h2>
 *
 * <p>A {@link BrowserContext} is an isolated, incognito-like browser profile.
 * It owns cookies, local storage, session storage, permissions, and pages.
 * Therefore, this framework does not need a separate
 * {@code PlaywrightSession} wrapper. A new primary context is created for
 * every test method.</p>
 *
 * <p>Additional contexts can be created with {@link #newContext()} for a
 * multi-user scenario. For example, an administrator and a customer can use
 * the same browser process while remaining logged in independently.</p>
 *
 * <h2>Parallel execution</h2>
 *
 * <p>Playwright Java objects are not thread-safe. This factory uses a
 * {@link ThreadLocal} so every TestNG worker thread receives its own complete
 * Playwright object graph. The Playwright, Browser, BrowserContext, and Page
 * created on one thread must continue to be used only on that same thread.</p>
 *
 * <p>{@code ThreadLocal} does not make one shared Page thread-safe. Never place
 * a {@link Page}, {@link BrowserContext}, or Page Object in a mutable
 * {@code static} field.</p>
 *
 * <h2>Configuration</h2>
 *
 * <p>Settings are read through {@link FrameworkConfig}. Maven may load the
 * selected profile file through {@code systemPropertiesFile}; a direct
 * IntelliJ run uses FrameworkConfig's file fallback.</p>
 *
 * <table>
 *   <caption>Supported settings</caption>
 *   <thead>
 *     <tr>
 *       <th>Key</th>
 *       <th>Allowed value</th>
 *       <th>Default</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@code browser}</td>
 *       <td>{@code chromium}, {@code firefox}, {@code webkit}</td>
 *       <td>{@code chromium}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code playwright.debug}</td>
 *       <td>{@code off}, {@code api}, {@code browser}, {@code all}</td>
 *       <td>{@code off}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code headless}</td>
 *       <td>boolean</td>
 *       <td>{@code true}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code slowMo}</td>
 *       <td>milliseconds, zero or greater</td>
 *       <td>{@code 0}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code browser.launchTimeout}</td>
 *       <td>milliseconds, greater than zero</td>
 *       <td>{@code 30000}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code baseUrl}</td>
 *       <td>URL or blank</td>
 *       <td>blank</td>
 *     </tr>
 *     <tr>
 *       <td>{@code viewport.width}</td>
 *       <td>positive integer</td>
 *       <td>{@code 1920}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code viewport.height}</td>
 *       <td>positive integer</td>
 *       <td>{@code 1080}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code ignoreHTTPSErrors}</td>
 *       <td>boolean</td>
 *       <td>{@code false}</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h2>Runtime examples</h2>
 *
 * <pre>{@code
 * // Default: test profile, Chromium, headless, debug off
 * mvn clean test
 *
 * // Headed Firefox with a one-second slow motion delay
 * mvn -Ptest clean test -Dbrowser=firefox -Dheadless=false -DslowMo=1000
 *
 * // WebKit with API debug messages
 * mvn -Ptest clean test -Dbrowser=webkit -Dplaywright.debug=api
 *
 * // Combined API and browser-driver debug messages
 * mvn -Ptest clean test -Dplaywright.debug=all
 * }</pre>
 *
 * <h2>Expected TestNG lifecycle</h2>
 *
 * <p>{@code BaseTest} calls {@link #start()} from {@code @BeforeMethod} and
 * {@link #quit()} from {@code @AfterMethod}. Tests normally use the protected
 * {@code page()}, {@code context()}, and {@code newContext()} methods inherited
 * from BaseTest rather than calling this factory directly.</p>
 */
public final class PlaywrightFactory {

    /**
     * Stores the resources owned by the currently executing TestNG worker
     * thread.
     */
    private static final ThreadLocal<TestState> STATE = new ThreadLocal<>();

    /** Browser engine configuration key. */
    private static final String BROWSER_PROPERTY = "browser";

    /** Playwright driver debug-mode configuration key. */
    private static final String DEBUG_PROPERTY = "playwright.debug";

    /** Headless/headed launch configuration key. */
    private static final String HEADLESS_PROPERTY = "headless";

    /** Slow-motion delay configuration key. */
    private static final String SLOW_MO_PROPERTY = "slowMo";

    /** Browser-process launch timeout configuration key. */
    private static final String LAUNCH_TIMEOUT_PROPERTY =
            "browser.launchTimeout";

    /** Optional BrowserContext base URL configuration key. */
    private static final String BASE_URL_PROPERTY = "baseUrl";

    /** BrowserContext viewport width configuration key. */
    private static final String VIEWPORT_WIDTH_PROPERTY = "viewport.width";

    /** BrowserContext viewport height configuration key. */
    private static final String VIEWPORT_HEIGHT_PROPERTY = "viewport.height";

    /** HTTPS certificate-handling configuration key. */
    private static final String IGNORE_HTTPS_ERRORS_PROPERTY =
            "ignoreHTTPSErrors";

    /**
     * Prevents construction because the factory exposes a static lifecycle API.
     *
     * @throws AssertionError always, if reflection attempts construction
     */
    private PlaywrightFactory() {
        throw new AssertionError(
                "PlaywrightFactory cannot be instantiated"
        );
    }

    /**
     * Starts one complete, isolated Playwright stack on the current thread.
     *
     * <p>The method performs these operations in order:</p>
     *
     * <ol>
     *   <li>Read and validate an immutable settings snapshot.</li>
     *   <li>Create Playwright with the selected driver-debug environment.</li>
     *   <li>Select and launch Chromium, Firefox, or WebKit.</li>
     *   <li>Create the primary isolated BrowserContext.</li>
     *   <li>Create the primary Page.</li>
     *   <li>Store all resources in the current thread's {@code ThreadLocal}.</li>
     * </ol>
     *
     * <p>If any startup operation fails, every resource created before the
     * failure is closed before the original exception is rethrown.</p>
     *
     * <p>Normal usage is handled by BaseTest:</p>
     *
     * <pre>{@code
     * @BeforeMethod(alwaysRun = true)
     * public void setUpPlaywright() {
     *     PlaywrightFactory.start();
     * }
     * }</pre>
     *
     * @throws IllegalStateException if Playwright is already started on the
     *                               current thread
     * @throws IllegalArgumentException if a configuration value is invalid
     * @throws RuntimeException if Playwright/browser/context startup fails
     */
    public static void start() {
        if (isStarted()) {
            throw new IllegalStateException(
                    "Playwright is already initialized for thread: "
                            + Thread.currentThread().getName()
            );
        }

        Settings settings = Settings.fromConfiguration();
        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;

        try {
            playwright = createPlaywright(settings.debugMode());
            browser = launchBrowser(playwright, settings);
            context = createContext(browser, settings);
            Page page = context.newPage();

            STATE.set(
                    new TestState(
                            playwright,
                            browser,
                            context,
                            page,
                            settings
                    )
            );
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

    /**
     * Returns the primary Page belonging to the current test invocation.
     *
     * <p>Example:</p>
     *
     * <pre>{@code
     * PlaywrightFactory.page().navigate("https://example.test");
     * }</pre>
     *
     * <p>Tests extending BaseTest normally call {@code page()} instead.</p>
     *
     * @return current thread's primary Page
     * @throws IllegalStateException if {@link #start()} has not been called on
     *                               the current thread
     */
    public static Page page() {
        return requireState().page;
    }

    /**
     * Returns the primary isolated BrowserContext for the current test.
     *
     * <p>This context represents the primary user's browser session. New tabs
     * and popups opened from the primary Page remain inside this context and
     * share its cookies/storage.</p>
     *
     * @return current thread's primary BrowserContext
     * @throws IllegalStateException if the factory is not started
     */
    public static BrowserContext context() {
        return requireState().primaryContext;
    }

    /**
     * Returns the launched Browser for the current test invocation.
     *
     * <p>Most tests should use Page/BrowserContext instead. Direct Browser
     * access is useful for advanced operations such as examining the browser
     * type or manually creating specially configured contexts.</p>
     *
     * @return current thread's Browser
     * @throws IllegalStateException if the factory is not started
     */
    public static Browser browser() {
        return requireState().browser;
    }

    /**
     * Returns the Playwright root object for the current test invocation.
     *
     * <p>Most tests do not need this method. It is available for APIs owned by
     * the Playwright root, such as request/API setup or device descriptors.</p>
     *
     * @return current thread's Playwright instance
     * @throws IllegalStateException if the factory is not started
     */
    public static Playwright playwright() {
        return requireState().playwright;
    }

    /**
     * Creates and tracks an additional isolated BrowserContext.
     *
     * <p>The additional context uses the same browser engine, viewport,
     * base URL, and HTTPS configuration as the primary context, but has
     * completely separate cookies and storage.</p>
     *
     * <p>Multi-user example:</p>
     *
     * <pre>{@code
     * // Primary context represents the customer.
     * Page customerPage = PlaywrightFactory.page();
     *
     * // Additional context represents the administrator.
     * BrowserContext adminContext = PlaywrightFactory.newContext();
     * Page adminPage = adminContext.newPage();
     *
     * customerPage.navigate("https://example.test/customer");
     * adminPage.navigate("https://example.test/admin");
     * }</pre>
     *
     * <p>The caller does not have to close the additional context in normal
     * BaseTest usage. {@link #quit()} tracks and closes it automatically.</p>
     *
     * @return new isolated BrowserContext
     * @throws IllegalStateException if the factory is not started
     * @throws RuntimeException if Playwright cannot create the context
     */
    public static BrowserContext newContext() {
        TestState state = requireState();
        BrowserContext context = createContext(
                state.browser,
                state.settings
        );

        state.contexts.addFirst(context);
        return context;
    }

    /**
     * Closes every Playwright resource owned by the current thread.
     *
     * <p>Cleanup order is:</p>
     *
     * <ol>
     *   <li>Additional contexts, newest first</li>
     *   <li>Primary context</li>
     *   <li>Browser</li>
     *   <li>Playwright</li>
     *   <li>Remove the ThreadLocal value</li>
     * </ol>
     *
     * <p>The method attempts every cleanup operation even when an earlier close
     * fails. Later cleanup failures are attached as suppressed exceptions.
     * {@code STATE.remove()} always executes, which is essential when TestNG
     * reuses worker threads.</p>
     *
     * <p>Calling {@code quit()} when the factory is not started is safe and
     * performs no work.</p>
     *
     * @throws RuntimeException if resource cleanup fails
     * @throws Error if cleanup produces a serious JVM/Playwright error
     */
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

    /**
     * Reports whether the current thread owns an active factory state.
     *
     * <p>This method checks only the current thread. Another parallel TestNG
     * thread may have its own active state.</p>
     *
     * @return {@code true} when {@link #start()} has completed successfully on
     *         the current thread; otherwise {@code false}
     */
    public static boolean isStarted() {
        return STATE.get() != null;
    }

    /**
     * Creates Playwright and configures debug logging for its driver process.
     *
     * <p>{@link Playwright.CreateOptions#setEnv(Map)} replaces the environment
     * supplied to the Playwright driver. Therefore, the current laptop/Jenkins
     * environment is copied first, and only {@code DEBUG} is replaced.</p>
     *
     * @param debugMode validated debug mode
     * @return new Playwright instance
     */
    private static Playwright createPlaywright(DebugMode debugMode) {
        Map<String, String> environment =
                new HashMap<>(System.getenv());

        environment.put(
                "DEBUG",
                debugMode.driverValue()
        );

        Playwright.CreateOptions options =
                new Playwright.CreateOptions()
                        .setEnv(environment);

        return Playwright.create(options);
    }

    /**
     * Selects a browser engine and launches it with validated settings.
     *
     * @param playwright current thread's Playwright root
     * @param settings immutable settings snapshot
     * @return launched Browser
     */
    private static Browser launchBrowser(
            Playwright playwright,
            Settings settings
    ) {
        BrowserType browserType = switch (settings.browserName()) {
            case CHROMIUM -> playwright.chromium();
            case FIREFOX -> playwright.firefox();
            case WEBKIT -> playwright.webkit();
        };

        BrowserType.LaunchOptions options =
                new BrowserType.LaunchOptions()
                        .setHeadless(settings.headless())
                        .setSlowMo(settings.slowMoMs())
                        .setTimeout(settings.launchTimeoutMs());

        return browserType.launch(options);
    }

    /**
     * Creates a BrowserContext using the shared context-level settings.
     *
     * <p>The method is used for both the primary context and every additional
     * context, ensuring consistent viewport, base URL, and HTTPS behavior.</p>
     *
     * @param browser current thread's Browser
     * @param settings immutable settings snapshot
     * @return new BrowserContext
     */
    private static BrowserContext createContext(
            Browser browser,
            Settings settings
    ) {
        Browser.NewContextOptions options =
                new Browser.NewContextOptions()
                        .setViewportSize(
                                settings.viewportWidth(),
                                settings.viewportHeight()
                        )
                        .setIgnoreHTTPSErrors(
                                settings.ignoreHTTPSErrors()
                        );

        if (!settings.baseUrl().isBlank()) {
            options.setBaseURL(settings.baseUrl());
        }

        return browser.newContext(options);
    }

    /**
     * Returns the current thread's state or throws a lifecycle error.
     *
     * @return current thread's TestState
     * @throws IllegalStateException if the factory has not been started
     */
    private static TestState requireState() {
        TestState state = STATE.get();

        if (state == null) {
            throw new IllegalStateException(
                    "Playwright is not initialized for thread: "
                            + Thread.currentThread().getName()
                            + ". Call PlaywrightFactory.start() "
                            + "from @BeforeMethod."
            );
        }

        return state;
    }

    /**
     * Cleans partially created resources after a startup failure.
     *
     * <p>The original startup failure remains the primary exception. Any
     * cleanup failure is attached to it as a suppressed exception.</p>
     *
     * @param context context created before failure, or {@code null}
     * @param browser browser created before failure, or {@code null}
     * @param playwright Playwright created before failure, or {@code null}
     * @param startupFailure original startup failure
     */
    private static void closeAfterStartupFailure(
            BrowserContext context,
            Browser browser,
            Playwright playwright,
            Throwable startupFailure
    ) {
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

    /**
     * Runs one startup-cleanup action without replacing the original failure.
     *
     * @param closeAction resource close operation
     * @param originalFailure original startup failure
     */
    private static void closeSafely(
            Runnable closeAction,
            Throwable originalFailure
    ) {
        try {
            closeAction.run();
        } catch (RuntimeException | Error closeFailure) {
            originalFailure.addSuppressed(closeFailure);
        }
    }

    /**
     * Closes one resource while accumulating cleanup failures.
     *
     * @param closeAction resource close operation
     * @param previousFailure earlier cleanup failure, or {@code null}
     * @return the first cleanup failure, possibly with suppressed failures
     */
    private static Throwable closeResource(
            Runnable closeAction,
            Throwable previousFailure
    ) {
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

    /**
     * Rethrows a collected unchecked cleanup failure.
     *
     * @param failure failure to rethrow, or {@code null}
     */
    private static void rethrowUnchecked(Throwable failure) {
        if (failure == null) {
            return;
        }

        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }

        if (failure instanceof Error error) {
            throw error;
        }

        throw new IllegalStateException(
                "Unexpected checked exception during Playwright cleanup",
                failure
        );
    }

    /**
     * Supported browser engines.
     *
     * <p>The enum is private because test classes should provide a simple
     * configuration value rather than depending on factory internals.</p>
     */
    private enum BrowserName {
        /** Playwright's Chromium engine. */
        CHROMIUM,

        /** Playwright's Firefox engine. */
        FIREFOX,

        /** Playwright's WebKit engine. */
        WEBKIT;

        /**
         * Converts a configuration string to a supported browser engine.
         *
         * @param value configured browser name
         * @return corresponding BrowserName
         * @throws IllegalArgumentException if the browser is unsupported
         */
        private static BrowserName from(String value) {
            String normalized =
                    value.strip().toLowerCase(Locale.ROOT);

            return switch (normalized) {
                case "chromium" -> CHROMIUM;
                case "firefox" -> FIREFOX;
                case "webkit" -> WEBKIT;
                default -> throw new IllegalArgumentException(
                        "Unsupported browser '" + value
                                + "'. Allowed values: "
                                + "chromium, firefox, webkit"
                );
            };
        }
    }

    /**
     * Supported Playwright driver debug modes.
     *
     * <p>This controls the driver's {@code DEBUG} environment variable. It is
     * different from Playwright Inspector's {@code PWDEBUG} mode.</p>
     */
    private enum DebugMode {
        /** No driver debug messages. */
        OFF(""),

        /** Playwright API call logging. */
        API("pw:api"),

        /** Browser-process/driver logging. */
        BROWSER("pw:browser*"),

        /** API and browser logging together. */
        ALL("pw:api,pw:browser*");

        /** Value passed to the Playwright driver's DEBUG environment variable. */
        private final String driverValue;

        /**
         * Creates one debug-mode mapping.
         *
         * @param driverValue Playwright DEBUG namespace value
         */
        DebugMode(String driverValue) {
            this.driverValue = driverValue;
        }

        /**
         * Returns the DEBUG value used by the driver process.
         *
         * @return Playwright DEBUG namespace string
         */
        private String driverValue() {
            return driverValue;
        }

        /**
         * Converts friendly or native debug text into a supported mode.
         *
         * <p>Examples:</p>
         *
         * <ul>
         *   <li>{@code api} and {@code pw:api} become {@link #API}</li>
         *   <li>{@code browser} and {@code pw:browser*} become
         *       {@link #BROWSER}</li>
         *   <li>{@code all} and {@code api,browser} become {@link #ALL}</li>
         * </ul>
         *
         * @param value configured debug text
         * @return parsed debug mode
         * @throws IllegalArgumentException if the mode is unsupported
         */
        private static DebugMode from(String value) {
            String normalized = value
                    .strip()
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
                        "Unsupported Playwright debug mode '" + value
                                + "'. Allowed values: "
                                + "off, api, browser, all"
                );
            };
        }
    }

    /**
     * Immutable configuration snapshot used by one test invocation.
     *
     * <p>Why use a record: all values are read/validated once at startup.
     * Browser creation and additional-context creation then use one consistent
     * settings object. This record is a private implementation detail; tests
     * never create or access {@code PlaywrightFactory.Settings}.</p>
     *
     * @param browserName selected browser engine
     * @param debugMode selected Playwright driver debug mode
     * @param headless whether the browser runs without visible UI
     * @param slowMoMs delay after Playwright operations, in milliseconds
     * @param launchTimeoutMs browser launch timeout, in milliseconds
     * @param baseUrl optional BrowserContext base URL
     * @param viewportWidth BrowserContext viewport width
     * @param viewportHeight BrowserContext viewport height
     * @param ignoreHTTPSErrors whether invalid HTTPS certificates are allowed
     */
    private record Settings(
            BrowserName browserName,
            DebugMode debugMode,
            boolean headless,
            double slowMoMs,
            double launchTimeoutMs,
            String baseUrl,
            int viewportWidth,
            int viewportHeight,
            boolean ignoreHTTPSErrors
    ) {
        /**
         * Reads and validates every Playwright setting through FrameworkConfig.
         *
         * <p>The browser, debug-mode, and base-URL lookups supply explicit
         * nonnull defaults. {@link FrameworkConfig#text(String, String)}
         * guarantees a nonnull result, so enum parsing and String operations
         * cannot receive {@code null}.</p>
         *
         * @return immutable settings snapshot
         * @throws IllegalArgumentException if any setting is invalid
         */
        private static Settings fromConfiguration() {
            return new Settings(
                    BrowserName.from(
                            FrameworkConfig.text(
                                    BROWSER_PROPERTY,
                                    "chromium"
                            )
                    ),
                    DebugMode.from(
                            FrameworkConfig.text(
                                    DEBUG_PROPERTY,
                                    "off"
                            )
                    ),
                    FrameworkConfig.booleanValue(
                            HEADLESS_PROPERTY,
                            true
                    ),
                    FrameworkConfig.nonNegativeDouble(
                            SLOW_MO_PROPERTY,
                            0
                    ),
                    FrameworkConfig.positiveDouble(
                            LAUNCH_TIMEOUT_PROPERTY,
                            30_000
                    ),
                    FrameworkConfig.text(
                            BASE_URL_PROPERTY,
                            ""
                    ),
                    FrameworkConfig.positiveInt(
                            VIEWPORT_WIDTH_PROPERTY,
                            1920
                    ),
                    FrameworkConfig.positiveInt(
                            VIEWPORT_HEIGHT_PROPERTY,
                            1080
                    ),
                    FrameworkConfig.booleanValue(
                            IGNORE_HTTPS_ERRORS_PROPERTY,
                            false
                    )
            );
        }
    }

    /**
     * Mutable resource holder confined to exactly one TestNG worker thread.
     *
     * <p>The deque contains the primary context and all additional contexts.
     * New contexts are added to the front so they are closed before the
     * primary context.</p>
     */
    private static final class TestState {
        /** Playwright root owned by this test invocation. */
        private final Playwright playwright;

        /** Browser process connection owned by this test invocation. */
        private final Browser browser;

        /** Primary isolated user session. */
        private final BrowserContext primaryContext;

        /** Primary page created inside the primary context. */
        private final Page page;

        /** Immutable settings reused for additional contexts. */
        private final Settings settings;

        /** All contexts that must be closed during teardown. */
        private final Deque<BrowserContext> contexts =
                new ArrayDeque<>();

        /**
         * Creates the thread-confined resource state.
         *
         * @param playwright Playwright root
         * @param browser launched Browser
         * @param primaryContext primary BrowserContext
         * @param page primary Page
         * @param settings immutable settings snapshot
         */
        private TestState(
                Playwright playwright,
                Browser browser,
                BrowserContext primaryContext,
                Page page,
                Settings settings
        ) {
            this.playwright = playwright;
            this.browser = browser;
            this.primaryContext = primaryContext;
            this.page = page;
            this.settings = settings;
            this.contexts.addFirst(primaryContext);
        }
    }
}
