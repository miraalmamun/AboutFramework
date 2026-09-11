package factory;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds all Playwright resources belonging to one test session.
 * Closing this object closes:
 * 1. BrowserContext
 * 2. Browser
 * 3. Playwright
 */
public final class PlaywrightSession implements AutoCloseable {

    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;

    private final AtomicBoolean closed =
            new AtomicBoolean(false);

    public PlaywrightSession(
            Playwright playwright,
            Browser browser,
            BrowserContext context,
            Page page) {

        this.playwright = playwright;
        this.browser = browser;
        this.context = context;
        this.page = page;
    }

    public Playwright playwright() {
        verifySessionIsOpen();
        return playwright;
    }

    public Browser browser() {
        verifySessionIsOpen();
        return browser;
    }

    public BrowserContext context() {
        verifySessionIsOpen();
        return context;
    }

    public Page page() {
        verifySessionIsOpen();
        return page;
    }

    public boolean isClosed() {
        return closed.get();
    }

    private void verifySessionIsOpen() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "The Playwright session has already been closed."
            );
        }
    }

    @Override
    public void close() {

        // Prevent resources from being closed more than once.
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        RuntimeException failure = null;

        failure = closeResource(
                context::close,
                failure
        );

        failure = closeResource(
                browser::close,
                failure
        );

        failure = closeResource(
                playwright::close,
                failure
        );

        if (failure != null) {
            throw failure;
        }
    }

    private RuntimeException closeResource(
            Runnable closeAction,
            RuntimeException previousFailure) {

        try {
            closeAction.run();
        } catch (RuntimeException currentFailure) {

            if (previousFailure == null) {
                return currentFailure;
            }

            previousFailure.addSuppressed(currentFailure);
        }

        return previousFailure;
    }
}