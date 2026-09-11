package tests;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import factory.PlaywrightFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/** TestNG lifecycle for the thread-local PlaywrightFactory. */
public abstract class BaseTest {

    @BeforeMethod(alwaysRun = true)
    public final void setUpPlaywright() {
        PlaywrightFactory.start();
    }

    @AfterMethod(alwaysRun = true)
    public final void tearDownPlaywright(ITestResult testResult) {
        try {
            PlaywrightFactory.quit();
        } catch (RuntimeException | Error cleanupFailure) {
            Throwable testFailure = testResult.getThrowable();

            if (testFailure != null) {
                // Preserve the real assertion/test failure as the primary cause.
                testFailure.addSuppressed(cleanupFailure);
                return;
            }

            throw cleanupFailure;
        }
    }

    protected final Page page() {
        return PlaywrightFactory.page();
    }

    protected final BrowserContext context() {
        return PlaywrightFactory.context();
    }

    protected final Browser browser() {
        return PlaywrightFactory.browser();
    }

    protected final Playwright playwright() {
        return PlaywrightFactory.playwright();
    }

    protected final BrowserContext newContext() {
        return PlaywrightFactory.newContext();
    }
}