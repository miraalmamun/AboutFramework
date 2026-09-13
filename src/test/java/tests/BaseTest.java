package tests;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import config.FrameworkConfig;
import config.ServiceUrls;
import factory.PlaywrightFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

/**
 * Connects the TestNG per-method lifecycle to {@link PlaywrightFactory}.
 *
 * <h2>Purpose</h2>
 *
 * <p>Every UI test class extends {@code BaseTest}. Before each {@code @Test}
 * method, BaseTest asks PlaywrightFactory to create a new Playwright, Browser,
 * BrowserContext, and Page. After the method, BaseTest closes those resources
 * even when the test failed.</p>
 *
 * <table>
 *   <caption>Lifecycle for one TestNG test method</caption>
 *   <thead>
 *     <tr>
 *       <th>Stage</th>
 *       <th>BaseTest operation</th>
 *       <th>Result</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@code @BeforeMethod}</td>
 *       <td>{@link PlaywrightFactory#start()}</td>
 *       <td>Fresh isolated browser resources</td>
 *     </tr>
 *     <tr>
 *       <td>{@code @Test}</td>
 *       <td>Test uses {@link #page()} and Page Objects</td>
 *       <td>Scenario executes</td>
 *     </tr>
 *     <tr>
 *       <td>{@code @AfterMethod}</td>
 *       <td>{@link PlaywrightFactory#quit()}</td>
 *       <td>All resources and ThreadLocal state are removed</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <h2>Why the class is abstract</h2>
 *
 * <p>BaseTest is framework support code, not a test scenario. Declaring it
 * {@code abstract} prevents accidental construction/execution by itself while
 * allowing real test classes to inherit its lifecycle and protected helper
 * methods.</p>
 *
 * <h2>Basic test example</h2>
 *
 * <pre>{@code
 * package tests;
 *
 * import com.microsoft.playwright.assertions.PlaywrightAssertions;
 * import org.testng.annotations.Test;
 *
 * public final class HomePageTest extends BaseTest {
 *
 *     @Test
 *     public void pageHasExpectedTitle() {
 *         page().navigate("https://example.test");
 *
 *         PlaywrightAssertions.assertThat(page())
 *                 .hasTitle("Example");
 *     }
 * }
 * }</pre>
 *
 * <h2>Page Object Model example</h2>
 *
 * <p>Pass the current test's Page into the Page Object constructor. Do not
 * store Page Objects in static fields.</p>
 *
 * <pre>{@code
 * public final class LoginTest extends BaseTest {
 *
 *     @Test
 *     public void userCanLogIn() {
 *         LoginPage loginPage = new LoginPage(page());
 *
 *         loginPage.open();
 *         loginPage.login("sample-user", "sample-password");
 *         loginPage.verifyLoginSucceeded();
 *     }
 * }
 * }</pre>
 *
 * <h2>New tab or popup example</h2>
 *
 * <p>A popup is another Page in the same BrowserContext. It shares the same
 * login cookies/session as the original page and is closed automatically when
 * the context closes.</p>
 *
 * <pre>{@code
 * @Test
 * public void savedFormOpensConfirmationTab() {
 *     page().navigate("https://example.test/form");
 *
 *     Page confirmationPage = page().waitForPopup(
 *             () -> page().getByText("Save and open").click()
 *     );
 *
 *     confirmationPage.waitForLoadState();
 *     PlaywrightAssertions.assertThat(confirmationPage)
 *             .hasURL("https://example.test/confirmation");
 * }
 * }</pre>
 *
 * <h2>Two independent users example</h2>
 *
 * <p>Use {@link #newContext()} when two users must have different cookies and
 * login states:</p>
 *
 * <pre>{@code
 * @Test
 * public void administratorCanSeeCustomerSubmission() {
 *     // Primary context: customer
 *     Page customerPage = page();
 *
 *     // Additional isolated context: administrator
 *     BrowserContext adminContext = newContext();
 *     Page adminPage = adminContext.newPage();
 *
 *     customerPage.navigate("https://example.test/customer");
 *     adminPage.navigate("https://example.test/admin");
 *
 *     // Perform the customer and administrator workflow here.
 * }
 * }</pre>
 *
 * <h2>Parallel-execution rule</h2>
 *
 * <p>The factory state is thread-local, so each TestNG worker thread receives
 * separate Playwright resources. Tests must not copy {@link #page()} into a
 * mutable static field or share one Page Object across test methods. Create
 * Page Objects inside the test method or in a per-method setup.</p>
 *
 * <h2>What BaseTest intentionally does not do</h2>
 *
 * <ul>
 *   <li>It does not choose an environment. Maven profiles and
 *       {@code FrameworkConfig} handle that.</li>
 *   <li>It does not contain test assertions or page locators.</li>
 *   <li>It does not create a separate PlaywrightSession wrapper.</li>
 *   <li>It does not perform application login automatically.</li>
 *   <li>It does not add logging/reporting dependencies.</li>
 * </ul>
 */
public abstract class BaseTest {

    /*
     * Loaded only once for the current test JVM.
     *
     * This is safe for parallel testing because:
     * 1. ServiceUrls is immutable.
     * 2. The Maven environment does not change while the JVM is running.
     * 3. Tests only read these values.
     */
    private static final ServiceUrls SERVICE_URLS =
            ServiceUrls.load();


    /**
     * Displays the environment selected for the current TestNG test-suite run.
     *
     * <p>This method executes once before TestNG starts running the suite's test
     * classes and test methods. It reads the selected environment from
     * {@link FrameworkConfig#environment()} and prints it clearly in the console.
     * This helps testers confirm that the tests are running against the expected
     * environment, such as {@code dev}, {@code test}, {@code uat}, or
     * {@code staging}.</p>
     *
     * <p>The environment may be selected through a Maven profile:</p>
     *
     * <pre>{@code
     * mvn -Ptest test
     * mvn -Pdev test
     * mvn -Puat test
     * }</pre>
     *
     * <p>Example console output:</p>
     *
     * <pre>{@code
     * ========================================
     *  Test environment: test
     * ========================================
     * }</pre>
     *
     * <p>{@code @BeforeSuite} is used because the environment needs to be
     * displayed only once for the complete TestNG suite, rather than before every
     * test class or test method.</p>
     *
     * <p>{@code alwaysRun = true} asks TestNG to execute this configuration method
     * even when test groups, dependencies, failures, or filtering rules are
     * involved.</p>
     *
     * <p>The method is {@code final} so a child test class cannot override it and
     * accidentally remove or change this framework-level suite behavior.</p>
     *
     * <p>This method only displays the selected environment. It does not create a
     * browser, start Playwright, change configuration values, or switch the
     * environment.</p>
     *
     * @throws IllegalArgumentException if the configured environment name is
     *                                  invalid
     * @throws IllegalStateException if the selected environment configuration
     *                               cannot be loaded
     *
     * @see FrameworkConfig#environment()
     * @see org.testng.annotations.BeforeSuite
     */
    @BeforeSuite(alwaysRun = true)
    public final void displayEnvironment() {
        System.out.printf(
                "%n========================================%n" +
                        " Test environment: %s%n" +
                        "========================================%n%n",
                FrameworkConfig.environment()
        );
    }

    /**
     * Creates fresh Playwright resources before every TestNG test method.
     *
     * <p>{@code alwaysRun=true} asks TestNG to execute this configuration
     * method even when groups or dependencies are involved. The method is
     * {@code final} so a child test cannot accidentally override the mandatory
     * browser startup lifecycle.</p>
     *
     * @throws IllegalStateException    if factory state already exists on the
     *                                  current thread
     * @throws IllegalArgumentException if framework configuration is invalid
     * @throws RuntimeException         if Playwright/browser startup fails
     */
    @BeforeMethod(alwaysRun = true)
    public final void setUpPlaywright() {
        PlaywrightFactory.start();
    }

    /**
     * Closes all Playwright resources after every TestNG test method.
     *
     * <p>The cleanup executes for passed, failed, and skipped test methods
     * because {@code alwaysRun=true} is used.</p>
     *
     * <p>If the test already failed and cleanup also fails, the test failure
     * remains the primary cause and the cleanup failure is attached as a
     * suppressed exception. If the test itself passed but cleanup fails, the
     * cleanup failure is thrown so the problem is not hidden.</p>
     *
     * @param testResult TestNG result for the method that just completed
     * @throws RuntimeException if cleanup fails after an otherwise successful
     *                          test
     * @throws Error            if cleanup produces a serious JVM/Playwright error
     */
    @AfterMethod(alwaysRun = true)
    public final void tearDownPlaywright(ITestResult testResult) {
        try {
            PlaywrightFactory.quit();
        } catch (RuntimeException | Error cleanupFailure) {
            Throwable testFailure = testResult.getThrowable();

            if (testFailure != null) {
                testFailure.addSuppressed(cleanupFailure);
                return;
            }

            throw cleanupFailure;
        }
    }


    /**
     * Returns the URLs for the selected environment.
     *
     * @return immutable service URLs; never {@code null}
     */
    protected final ServiceUrls urls() {
        return SERVICE_URLS;
    }

    /**
     * Returns the primary Page for the currently executing test method.
     *
     * <p>This is the method most UI tests and Page Object constructors use:</p>
     *
     * <pre>{@code
     * LoginPage loginPage = new LoginPage(page());
     * }</pre>
     *
     * @return current test method's primary Page
     * @throws IllegalStateException if called outside the active per-method
     *                               Playwright lifecycle
     */
    protected final Page page() {
        return PlaywrightFactory.page();
    }

    /**
     * Returns the primary BrowserContext/session for the current test.
     *
     * <p>Use it for context-level operations such as permissions, cookies,
     * tracing, event listeners, or creating another Page in the same login
     * session:</p>
     *
     * <pre>{@code
     * Page secondTabInSameSession = context().newPage();
     * }</pre>
     *
     * @return current test method's primary BrowserContext
     * @throws IllegalStateException if the factory is not started
     */
    protected final BrowserContext context() {
        return PlaywrightFactory.context();
    }

    /**
     * Returns the Browser launched for the current test method.
     *
     * <p>Most normal tests do not need direct Browser access. Prefer
     * {@link #page()}, {@link #context()}, or {@link #newContext()}.</p>
     *
     * @return current test method's Browser
     * @throws IllegalStateException if the factory is not started
     */
    protected final Browser browser() {
        return PlaywrightFactory.browser();
    }

    /**
     * Returns the root Playwright object for the current test method.
     *
     * <p>This accessor is intended for advanced Playwright APIs that are not
     * available from Page, BrowserContext, or Browser.</p>
     *
     * @return current test method's Playwright root
     * @throws IllegalStateException if the factory is not started
     */
    protected final Playwright playwright() {
        return PlaywrightFactory.playwright();
    }

    /**
     * Creates an additional isolated BrowserContext for another user.
     *
     * <p>The new context does not share cookies, local storage, or session
     * storage with the primary context. PlaywrightFactory tracks it and closes
     * it automatically during {@link #tearDownPlaywright(ITestResult)}.</p>
     *
     * <pre>{@code
     * BrowserContext adminContext = newContext();
     * Page adminPage = adminContext.newPage();
     * }</pre>
     *
     * @return newly created isolated BrowserContext
     * @throws IllegalStateException if the factory is not started
     * @throws RuntimeException      if Playwright cannot create the context
     */
    protected final BrowserContext newContext() {
        return PlaywrightFactory.newContext();
    }
}