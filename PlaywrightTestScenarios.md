# Playwright Java Test Scenarios

## Complete TestNG + Maven + Page Object Model guide for PlaywrightFactory and BaseTest

This guide is a large, practical scenario catalogue for the existing
factory.PlaywrightFactory and base.BaseTest classes in this project.

The examples assume:

- Java 25
- Maven
- TestNG
- Playwright Java
- Page Object Model
- BaseTest starts and stops Playwright for every TestNG invocation
- the factory owns Playwright, Browser, the primary BrowserContext, and the primary Page
- PlaywrightFactory.newContext() creates an additional tracked, isolated context
- browser, headless, debug, timeouts, base URL, viewport, and HTTPS settings are
  selected with Maven/JVM -D properties
- no custom PlaywrightSession class is required

No document can enumerate every application's business rule. This guide does,
however, cover the browser primitives, events, controls, and failure modes from
which those business rules are built. Add your application's data and expected
business outcomes to these patterns.

> Complete classes use a java fence. Method fragments intentionally use a text
> fence so IntelliJ does not inspect incomplete examples as Java. If IntelliJ
> still reports Markdown diagnostics, review the Markdown code-fence inspection
> setting.

---

## 1. Resource ownership and lifecycle

The TestNG lifecycle is:

~~~mermaid
flowchart TD
    A["@BeforeMethod"] --> B["PlaywrightFactory.start()"]
    B --> C["Playwright → Browser → Context → Page"]
    C --> D["@Test and Page Objects"]
    D --> E["@AfterMethod"]
    E --> F["PlaywrightFactory.quit()"]
~~~

Every invocation receives a new isolated stack. The factory uses ThreadLocal, so
parallel TestNG workers do not share browser objects.

| Method       | Meaning                                       | Use                                          |
|--------------|-----------------------------------------------|----------------------------------------------|
| page()       | Primary page in the primary context           | Normal UI test                               |
| context()    | Primary isolated browser profile              | Context events, routes, cookies, extra pages |
| browser()    | Selected Chromium, Firefox, or WebKit browser | Advanced context creation                    |
| playwright() | Current Playwright driver                     | API request context and advanced APIs        |
| newContext() | New tracked context in the same browser       | Two users or two independent sessions        |

Do not call start or quit from a normal test. Do not put Page, BrowserContext,
Browser, or Playwright in a static field. Do not pass one test thread's Page to
another thread. BaseTest preserves a real test failure if cleanup also fails.

### 1.1 Smallest complete test

~~~java
package tests;

import base.BaseTest;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import org.testng.annotations.Test;

public final class NavigationSmokeTest extends BaseTest {

    @Test
    public void homePageCanBeOpened() {
        page().navigate("https://example.com");

        PlaywrightAssertions.assertThat(page())
                .hasTitle("Example Domain");
        PlaywrightAssertions.assertThat(page())
                .hasURL("https://example.com/");
    }
}
~~~

Use -Dheadless=false to see the browser locally. Jenkins normally remains
headless and publishes screenshots, traces, and reports.

---

## 2. Which Playwright object belongs to which scenario?

| Object           | Real-life meaning              | Examples                            |
|------------------|--------------------------------|-------------------------------------|
| Playwright       | Automation driver process      | API request context                 |
| Browser          | One browser process            | Chromium/Firefox/WebKit selection   |
| BrowserContext   | Fresh isolated profile/session | Cookies, users, permissions, routes |
| Page             | One tab or popup window        | Navigate, fill, click, assert       |
| Locator          | Live element query             | Role, label, test id                |
| FrameLocator     | Element query inside an iframe | Payment or identity provider frame  |
| Request/Response | Network event                  | Wait for save API or assert status  |

A Page is a tab or popup window. Browser chrome such as the address bar,
extensions, download shelf, or the operating-system browser icon is outside the
Playwright Page model.

---

## 3. Locator strategy and assertions

Prefer stable, user-facing locators:

| Locator     | Java example                        | Use                           |
|-------------|-------------------------------------|-------------------------------|
| Role/name   | getByRole(AriaRole.BUTTON, options) | Buttons, links, rows, dialogs |
| Label       | getByLabel("Email")                 | Form fields                   |
| Placeholder | getByPlaceholder("Search")          | Stable search fields          |
| Test id     | getByTestId("save")                 | Explicit automation contract  |
| Text        | getByText("Saved")                  | User-visible status           |
| Alt/title   | getByAltText or getByTitle          | Images and titled controls    |
| CSS/XPath   | locator("...")                      | Last resort                   |

The role syntax is valid Java:

~~~text
page().getByRole(
        AriaRole.BUTTON,
        new Page.GetByRoleOptions()
                .setName("Add to cart")
                .setExact(true)
).click();
~~~

Locators re-resolve on each action and assertion. Playwright automatically waits
for actionability. Assertions retry until their timeout.

~~~text
Locator save = page().getByRole(
        AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Save").setExact(true));

save.click();
Assert.assertTrue(save.isEnabled());
Assert.assertEquals(save.getAttribute("type"), "submit");
Assert.assertTrue(save.count() == 1);

PlaywrightAssertions.assertThat(page().getByTestId("status"))
        .hasText("Saved");
PlaywrightAssertions.assertThat(page().getByLabel("Email"))
        .hasValue("user@example.com");
~~~

Use first, last, or nth only when order is a real requirement. A strict-mode
failure normally means the locator is ambiguous; make it more specific.

### 3.1 POM rule

Page Objects receive a Page in their constructor. They expose actions and state,
but they do not start browsers, call the factory, close resources, or hide the
business assertion.

~~~java
package pages;

import com.microsoft.playwright.AriaRole;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public final class LoginPage {
    private final Page page;
    private final Locator email;
    private final Locator password;
    private final Locator signIn;

    public LoginPage(Page page) {
        this.page = page;
        this.email = page.getByLabel("Email");
        this.password = page.getByLabel("Password");
        this.signIn = page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                        .setName("Sign in")
                        .setExact(true));
    }

    public LoginPage open(String baseUrl) {
        page.navigate(baseUrl + "/login");
        return this;
    }

    public void signInAs(String username, String secret) {
        email.fill(username);
        password.fill(secret);
        signIn.click();
    }

    public Locator errorMessage() {
        return page.getByRole(AriaRole.ALERT);
    }
}
~~~

---

## 4. Navigation scenarios

### 4.1 Direct and relative navigation

~~~text
page().navigate("https://example.test/dashboard");
PlaywrightAssertions.assertThat(page()).hasURL("**/dashboard");
PlaywrightAssertions.assertThat(page()).hasTitle("Dashboard");

page().navigate("/orders"); // Works when -DbaseUrl=https://example.test
~~~

### 4.2 Link navigation, redirect, reload, back, and forward

~~~text
page().getByRole(AriaRole.LINK,
        new Page.GetByRoleOptions().setName("Orders")).click();
PlaywrightAssertions.assertThat(page()).hasURL("**/orders");

page().reload();
page().goBack();
page().goForward();
page().waitForURL("**/dashboard");
~~~

### 4.3 Readiness and load states

~~~text
page().navigate("https://example.test");
page().waitForLoadState(LoadState.DOMCONTENTLOADED);
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.HEADING,
        new Page.GetByRoleOptions().setName("Home"))).isVisible();
~~~

Use a UI assertion or specific response as the readiness signal. NETWORKIDLE can
be unsuitable for pages with polling, analytics, or web sockets.

### 4.4 Same-page history and hash navigation

~~~text
page().getByRole(AriaRole.LINK,
        new Page.GetByRoleOptions().setName("Specifications")).click();
PlaywrightAssertions.assertThat(page()).hasURL("**/product#specifications");
page().goBack();
~~~

### 4.5 A page created by the test

When the test itself calls context().newPage(), the returned Page is already the
correct object; no event wait is needed.

~~~text
Page reportPage = context().newPage();
reportPage.navigate("https://example.test/report");
PlaywrightAssertions.assertThat(reportPage).hasURL("**/report");
~~~

---

## 5. Form controls and user input

All fragments in this section belong inside a @Test method or Page Object.

### 5.1 Text, email, password, search, number, URL

~~~text
page().getByLabel("First name").fill("Mira");
page().getByLabel("Email").fill("mira@example.com");
page().getByLabel("Password").fill("secret-from-ci");
page().getByPlaceholder("Search products").fill("wireless keyboard");
page().getByLabel("Quantity").fill("3");
page().getByLabel("Website").fill("https://example.com");
~~~

fill replaces the value. Use pressSequentially only when the product must
observe individual key events.

~~~text
page().getByLabel("Search").pressSequentially(
        "playwright",
        new Locator.PressSequentiallyOptions().setDelay(50));
~~~

### 5.2 Textarea and contenteditable

~~~text
page().getByLabel("Description").fill("A multi-line description.");
page().locator("[contenteditable='true']").fill("Rich text content");
~~~

### 5.3 Checkbox and switch

~~~text
Locator terms = page().getByLabel("I agree to the terms");
terms.check();
Assert.assertTrue(terms.isChecked());

terms.uncheck();
Assert.assertFalse(terms.isChecked());

page().getByRole(AriaRole.SWITCH,
        new Page.GetByRoleOptions().setName("Email notifications"))
        .check();
~~~

### 5.4 Radio button

~~~text
page().getByLabel("Credit card").check();
Assert.assertTrue(page().getByLabel("Credit card").isChecked());
~~~

### 5.5 Native select

~~~text
page().getByLabel("Country").selectOption("US");
page().getByLabel("Plan").selectOption(
        new SelectOption().setLabel("Professional"));
~~~

### 5.6 Custom combobox and option

~~~text
page().getByRole(AriaRole.COMBOBOX,
        new Page.GetByRoleOptions().setName("Country")).click();
page().getByRole(AriaRole.OPTION,
        new Page.GetByRoleOptions().setName("United States")).click();
~~~

### 5.7 Autocomplete/typeahead

~~~text
Locator city = page().getByLabel("City");
city.fill("Lon");
page().getByRole(AriaRole.OPTION,
        new Page.GetByRoleOptions().setName("London, UK")).click();
PlaywrightAssertions.assertThat(city).hasValue("London, UK");
~~~

### 5.8 Date, time, range, color, and spin button

~~~text
page().getByLabel("Date of birth").fill("1990-05-17");
page().getByLabel("Start time").fill("09:30");
page().locator("input[type='range']").fill("75");
page().locator("input[type='color']").fill("#336699");
page().getByRole(AriaRole.SPINBUTTON,
        new Page.GetByRoleOptions().setName("Quantity")).fill("2");
~~~

Native controls can differ between browsers. A custom date picker should be
tested through its visible calendar roles and names.

### 5.9 Keyboard-only interaction

~~~text
page().getByLabel("First name").focus();
page().keyboard().press("Tab");
page().keyboard().type("Mira");
page().getByLabel("Email").press("Control+A");
page().getByLabel("Email").press("Backspace");
page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Save")).press("Enter");
~~~

Use Meta instead of Control only for a deliberately platform-specific scenario.

---

## 6. Form validation and save outcomes

### 6.1 Required and client-side validation

~~~text
page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Save")).click();

PlaywrightAssertions.assertThat(page().getByText("Email is required"))
        .isVisible();
PlaywrightAssertions.assertThat(page().getByLabel("Email"))
        .hasAttribute("aria-invalid", "true");
~~~

Add cases for empty, whitespace-only, invalid format, minimum, maximum, and
one-beyond-maximum values. Assert that no backend request was sent when the
validation is entirely client-side.

### 6.2 Server-side validation

~~~text
Response response = page().waitForResponse(
        value -> value.url().contains("/api/profile")
                && "POST".equals(value.request().method()),
        () -> page().getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Save")).click());

Assert.assertEquals(response.status(), 422);
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.ALERT))
        .containsText("Please correct the highlighted fields");
~~~

### 6.3 Save, loading, disabled, and success toast

~~~text
Locator save = page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Save"));

save.click();
PlaywrightAssertions.assertThat(save).isDisabled();
PlaywrightAssertions.assertThat(page().getByText("Saving…")).isVisible();
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.STATUS))
        .containsText("Saved");
PlaywrightAssertions.assertThat(save).isEnabled();
~~~

### 6.4 Duplicate click and idempotency

Click once, wait for the request, and assert one created record. If the feature
must protect against rapid double-clicks, deliberately issue two clicks and
assert one POST or one business record. Observe the network or database result;
do not infer idempotency from a disabled button alone.

### 6.5 Reset, cancel, unsaved changes

~~~text
page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Reset")).click();
PlaywrightAssertions.assertThat(page().getByLabel("First name"))
        .hasValue("");

page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Cancel")).click();
PlaywrightAssertions.assertThat(page()).hasURL("**/profile");
~~~

Register a beforeunload dialog handler before leaving a dirty form.

### 6.6 Multi-step wizard

~~~text
page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Next")).click();
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.HEADING,
        new Page.GetByRoleOptions().setName("Payment"))).isVisible();
~~~

Test next, back, direct step navigation, refresh, invalid step data, duplicate
submission, and final confirmation.

---

## 7. Tabs, popups, and new windows

A Playwright Page represents both a tab and a popup window. The synchronization
must wrap the action that creates the page.

### 7.1 Link with target blank: context wait

~~~text
Page newTab = context().waitForPage(() ->
        page().getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Open report")).click());

newTab.waitForLoadState();
PlaywrightAssertions.assertThat(newTab).hasURL("**/reports/1001");
PlaywrightAssertions.assertThat(newTab.getByRole(AriaRole.HEADING,
        new Page.GetByRoleOptions().setName("Report"))).isVisible();
~~~

### 7.2 JavaScript window.open: page popup wait

~~~text
Page popup = page().waitForPopup(() ->
        page().getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Preview")).click());

popup.waitForLoadState();
PlaywrightAssertions.assertThat(popup).hasTitle("Preview");
~~~

Use context().waitForPage when the opener is unknown or the new page may be
created by a worker or another page.

### 7.3 Existing pages and exact page identity

~~~text
List<Page> before = context().pages();
Page child = context().waitForPage(() ->
        page().getByText("Open child").click());

Assert.assertEquals(context().pages().size(), before.size() + 1);
Assert.assertTrue(context().pages().contains(child));
~~~

Do not assume pages().get(1) is the desired page.

### 7.4 Unknown or occasional pages

~~~text
List<Page> observed = new CopyOnWriteArrayList<>();
Consumer<Page> listener = observed::add;
context().onPage(listener);
page().getByText("Continue").click();
context().offPage(listener);
Assert.assertEquals(observed.size(), 1);
~~~

Prefer a bounded waitForPage over a fixed delay. A listener is appropriate when
the event is not tied to one predictable action.

### 7.5 Popup opener and close

~~~text
Page popup = page().waitForPopup(() ->
        page().getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Open details")).click());

Assert.assertEquals(popup.opener(), page());
popup.close();
Assert.assertTrue(popup.isClosed());
~~~

The factory closes pages with their tracked context. Do not close the primary page
in a normal test.

### 7.6 Save form and open receipt tab

~~~text
page().navigate("https://example.test/orders/new");
page().getByLabel("Customer name").fill("Mira");
page().getByLabel("Customer email").fill("mira@example.com");

Page receipt = context().waitForPage(() -> {
    Response response = page().waitForResponse(
            value -> value.url().contains("/api/orders")
                    && "POST".equals(value.request().method()),
            () -> page().getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Save")).click());
    Assert.assertEquals(response.status(), 201);
});

receipt.waitForLoadState();
PlaywrightAssertions.assertThat(receipt).hasURL("**/orders/*/receipt");
PlaywrightAssertions.assertThat(receipt.getByRole(AriaRole.HEADING,
        new Page.GetByRoleOptions().setName("Receipt"))).isVisible();
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.STATUS))
        .containsText("Order saved");
~~~

If the product opens the tab before the POST finishes, the two waits still wrap
the click. If the product opens it later, wait for the guaranteed event order
instead of guessing.

### 7.7 Popup blocked or never opened

A waitForPage or waitForPopup timeout is a real failure. Capture the current URL
and a screenshot, then fail the test. Do not turn a missing popup into a pass.

---

## 8. JavaScript dialogs

Dialogs include alert, confirm, prompt, and beforeunload. Register a handler
before the action. An unhandled dialog can stall the action that opened it.

### 8.1 Alert

~~~text
page().onceDialog(dialog -> {
    Assert.assertEquals(dialog.type(), "alert");
    Assert.assertEquals(dialog.message(), "Profile saved");
    dialog.accept();
});
page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Save")).click();
~~~

### 8.2 Confirm accept or dismiss

~~~text
page().onceDialog(dialog -> {
    Assert.assertEquals(dialog.type(), "confirm");
    dialog.dismiss();
});
page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Delete")).click();
PlaywrightAssertions.assertThat(page().getByText("Delete cancelled"))
        .isVisible();
~~~

### 8.3 Prompt

~~~text
page().onceDialog(dialog -> {
    Assert.assertEquals(dialog.type(), "prompt");
    dialog.accept("Mira");
});
page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Rename")).click();
~~~

### 8.4 beforeunload

~~~text
page().onceDialog(dialog -> {
    Assert.assertEquals(dialog.type(), "beforeunload");
    dialog.accept();
});
page().getByRole(AriaRole.LINK,
        new Page.GetByRoleOptions().setName("Leave page")).click();
~~~

Playwright auto-dismisses dialogs when no handler exists. Use onceDialog for a
single event and onDialog/offDialog for a reusable listener.

### 8.5 HTML modal is not a JavaScript dialog

Most application modals are ordinary HTML. Locate them like any other part of
the page; do not install a Dialog handler for them.

~~~text
Locator modal = page().getByRole(AriaRole.DIALOG);
PlaywrightAssertions.assertThat(modal).isVisible();
PlaywrightAssertions.assertThat(modal.getByRole(AriaRole.HEADING,
        new Page.GetByRoleOptions().setName("Confirm delete"))).isVisible();
modal.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Cancel")).click();
PlaywrightAssertions.assertThat(modal).isHidden();
~~~

---

## 9. Downloads

Wait around the action, save immediately, and verify the saved file. Temporary
download files can disappear when the context closes.

~~~text
Path destination = Paths.get("target", "artifacts", "report.csv");
Files.createDirectories(destination.getParent());

Download download = page().waitForDownload(() ->
        page().getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Download report")).click());

Assert.assertEquals(download.suggestedFilename(), "report.csv");
download.saveAs(destination);
Assert.assertTrue(Files.exists(destination));
Assert.assertTrue(Files.size(destination) > 0);
~~~

Add cases for CSV, PDF, ZIP, wrong filename, server error, cancelled download,
empty file, and permission failure. Use PDFBox or ZipFile to verify business
content after Playwright saves the file.

---

## 10. Uploads and file chooser

Playwright sets a file directly; it does not need the operating-system file
picker.

### 10.1 Single file

~~~text
Path file = Paths.get("src", "test", "resources", "documents", "avatar.png");
page().getByLabel("Profile photo").setInputFiles(file);
PlaywrightAssertions.assertThat(page().getByText("avatar.png")).isVisible();
~~~

### 10.2 Multiple files

~~~text
Path first = Paths.get("src", "test", "resources", "a.txt");
Path second = Paths.get("src", "test", "resources", "b.txt");
page().locator("input[type='file']")
        .setInputFiles(new Path[]{first, second});
~~~

### 10.3 Custom Choose file button

~~~text
FileChooser chooser = page().waitForFileChooser(() ->
        page().getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Choose file")).click());
chooser.setFiles(Paths.get("src", "test", "resources", "invoice.pdf"));
~~~

### 10.4 Clear input and validate

~~~text
page().locator("input[type='file']").setInputFiles(new Path[0]);
~~~

Test unsupported extension, maximum size, zero-byte file, corrupted file,
duplicate file, multiple files, upload progress, retry, and network failure.

---

## 11. Frames and iframes

Use frameLocator for fields inside an iframe. Use page.frame when you need a
Frame object selected by name or URL.

### 11.1 Frame locator

~~~text
FrameLocator payment = page().frameLocator(
        "iframe[title='Payment form']");
payment.getByLabel("Card number").fill("4111111111111111");
payment.getByLabel("Expiry").fill("12/30");
payment.getByRole(AriaRole.BUTTON).click();
~~~

### 11.2 Named frame

~~~text
Frame frame = page().frame("payment-frame");
Assert.assertNotNull(frame);
frame.getByLabel("Card number").fill("4111111111111111");
~~~

### 11.3 Nested frames

~~~text
FrameLocator inner = page().frameLocator("iframe#outer")
        .frameLocator("iframe#inner");
inner.getByText("Submit").click();
~~~

Test frame loading, missing frame, cross-origin frame behavior, validation,
payment success, and the parent-page result.

---

## 12. Shadow DOM

Locators can pierce open shadow roots. XPath does not cross a shadow boundary.
Closed shadow roots are not inspectable through normal Playwright locators.

~~~text
page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Save")).click();

page().locator("my-component").locator("button.save").click();
~~~

Prefer a stable accessible name or test id exposed by the component rather than
depending on private implementation details.

---

## 13. Tables, grids, cards, and virtualized lists

### 13.1 Select row by business data

~~~text
Locator row = page().getByRole(AriaRole.ROW)
        .filter(new Locator.FilterOptions().setHasText("Order 1001"));
PlaywrightAssertions.assertThat(row).hasCount(1);
row.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("View")).click();
~~~

### 13.2 Headers and cells

~~~text
Locator headers = page().getByRole(AriaRole.COLUMNHEADER);
Assert.assertEquals(headers.allTextContents().get(0), "Order ID");
PlaywrightAssertions.assertThat(row.getByRole(AriaRole.CELL)
        .filter(new Locator.FilterOptions().setHasText("Paid")))
        .hasText("Paid");
~~~

### 13.3 Sorting and pagination

~~~text
page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Sort by date")).click();
PlaywrightAssertions.assertThat(page().getByTestId("sort-indicator"))
        .hasAttribute("aria-label", "Descending");

page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Next page")).click();
PlaywrightAssertions.assertThat(page().getByText("Page 2 of 5"))
        .isVisible();
~~~

### 13.4 Infinite scroll and virtualized list

~~~text
Locator list = page().getByTestId("results-list");
int oldCount = list.getByRole(AriaRole.LISTITEM).count();
list.evaluate("element => element.scrollTop = element.scrollHeight");
PlaywrightAssertions.assertThat(list.getByRole(AriaRole.LISTITEM))
        .hasCount(oldCount + 20);
~~~

Wait for the response or loading-status signal. Virtualized lists may render only
visible rows; scroll the target into view before asserting it.

### 13.5 Empty, loading, error, partial, and no-permission states

~~~text
PlaywrightAssertions.assertThat(page().getByTestId("spinner")).isHidden();
PlaywrightAssertions.assertThat(page().getByTestId("empty-state"))
        .containsText("No orders found");
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.ALERT))
        .containsText("Could not load orders");
~~~

---

## 14. Mouse, keyboard, drag-and-drop, and touch

### 14.1 Hover and tooltip

~~~text
page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Help")).hover();
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.TOOLTIP))
        .containsText("Enter your account number");
~~~

### 14.2 Right click and double click

~~~text
page().getByTestId("file-row").click(
        new Locator.ClickOptions().setButton(MouseButton.RIGHT));
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.MENU))
        .isVisible();

page().getByText("Open editor").dblclick();
~~~

### 14.3 Drag and drop

~~~text
page().getByTestId("card-1")
        .dragTo(page().getByTestId("column-done"));
PlaywrightAssertions.assertThat(page().getByTestId("column-done"))
        .containsText("Card 1");
~~~

### 14.4 Mouse coordinates and canvas

~~~text
page().mouse().move(200, 150);
page().mouse().down();
page().mouse().move(500, 150);
page().mouse().up();
~~~

Coordinates are viewport-sensitive. Use them only when a semantic locator or
dragTo cannot express the behavior.

### 14.5 Mobile and touch

Touch needs a context configured with setHasTouch(true), often setIsMobile(true).
The current no-argument factory newContext method does not accept those options.
Add a tracked context-options overload before writing a mobile test; do not leave
an untracked context open.

---

## 15. Network waits, interception, and mocking

Put an event wait around the action that causes the request or response.

### 15.1 Wait for request

~~~text
Request request = page().waitForRequest(
        value -> value.url().contains("/api/search")
                && "GET".equals(value.method()),
        () -> page().getByPlaceholder("Search").fill("playwright"));
Assert.assertTrue(request.url().contains("q=playwright"));
~~~

### 15.2 Wait for response and status/body

~~~text
Response response = page().waitForResponse(
        value -> value.url().contains("/api/orders")
                && "POST".equals(value.request().method()),
        () -> page().getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Save")).click());

Assert.assertEquals(response.status(), 201);
Assert.assertTrue(response.body().length > 0);
~~~

### 15.3 Failed-request diagnostics

~~~text
List<String> failures = new CopyOnWriteArrayList<>();
Consumer<Request> listener = request -> {
    if (request.failure() != null) {
        failures.add(request.url() + " :: " + request.failure());
    }
};
page().onRequestFailed(listener);
page().navigate("https://example.test");
page().offRequestFailed(listener);
Assert.assertTrue(failures.isEmpty(), failures.toString());
~~~

### 15.4 Fulfill a mocked response

~~~text
context().route("**/api/products", route -> route.fulfill(
        new Route.FulfillOptions()
                .setStatus(200)
                .setContentType("application/json")
                .setBody("{\"products\":[{\"name\":\"Mock keyboard\"}]}")
));
try {
    page().navigate("https://example.test/products");
    PlaywrightAssertions.assertThat(page().getByText("Mock keyboard"))
            .isVisible();
} finally {
    context().unroute("**/api/products");
}
~~~

### 15.5 Abort traffic

~~~text
context().route("**/analytics/**", Route::abort);
page().reload();
context().unroute("**/analytics/**");
~~~

Abort only traffic that is intentionally irrelevant to the scenario.

### 15.6 Offline

~~~text
context().setOffline(true);
page().reload();
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.ALERT))
        .containsText("You are offline");
context().setOffline(false);
~~~

### 15.7 Service workers, web sockets, and workers

A service worker can handle a request before a page route sees it. If a mock is
ignored, check service-worker behavior and route at the correct scope. Playwright
can observe web socket and worker events; assert the resulting UI first and use
protocol events as diagnostics.

~~~text
page().onWebSocket(socket ->
        socket.onFrameReceived(frame -> System.out.println("WS: " + frame)));
page().onWorker(worker ->
        System.out.println("Worker: " + worker.url()));
~~~

Event names are version-sensitive; use the API matching the Playwright Java
dependency in pom.xml.

---

## 16. API plus UI hybrid tests

An API request context created directly from playwright().request() is not tracked
by the current factory. Dispose it in the same test.

~~~text
APIRequestContext api = playwright().request().newContext(
        new APIRequest.NewContextOptions()
                .setBaseURL("https://example.test")
                .setExtraHTTPHeaders(
                        Map.of("Accept", "application/json")));
try {
    APIResponse created = api.post("/api/orders",
            RequestOptions.create()
                    .setData(Map.of("sku", "KB-1")));
    Assert.assertEquals(created.status(), 201);

    page().navigate("https://example.test/orders");
    PlaywrightAssertions.assertThat(page().getByText("KB-1"))
            .isVisible();
} finally {
    api.dispose();
}
~~~

Use API setup to make deterministic data quickly, then use the browser to verify
the real user journey.

---

## 17. Authentication and session scenarios

A BrowserContext is the isolated browser session. A custom session wrapper is
not a Playwright requirement.

### 17.1 UI login

~~~text
LoginPage login = new LoginPage(page()).open("https://example.test");
login.signInAs(System.getenv("E2E_USER"),
        System.getenv("E2E_PASSWORD"));
PlaywrightAssertions.assertThat(page()).hasURL("**/dashboard");
~~~

Keep credentials in Jenkins credentials or environment variables, never in
source or test reports.

### 17.2 Save storage state

~~~text
Path stateFile = Paths.get("target", "auth", "user.json");
Files.createDirectories(stateFile.getParent());
context().storageState(new BrowserContext.StorageStateOptions()
        .setPath(stateFile));
~~~

Storage state contains sensitive cookies and tokens. Keep it out of Git.

### 17.3 Reuse storage state safely

The current factory has only a no-argument tracked newContext method. For state,
video, locale, user-agent, permissions, or mobile options, add a tracked overload
that stores the new context in the factory's context deque:

~~~text
public static BrowserContext newContext(Browser.NewContextOptions options) {
    TestState state = requireState();
    BrowserContext extra = state.browser.newContext(options);
    state.contexts.addFirst(extra);
    return extra;
}

BrowserContext authenticated = PlaywrightFactory.newContext(
        new Browser.NewContextOptions().setStorageStatePath(stateFile));
Page authenticatedPage = authenticated.newPage();
~~~

Do not call browser().newContext(options) and assume the current factory will find
it. If you must create one directly, close it in a finally block before teardown.

### 17.4 Expiry, logout, and authorization

Test wrong password, locked user, expired cookie, 401, 403, logout, back-button
access after logout, direct protected URL, hidden action, role change, and tenant
isolation.

~~~text
context().clearCookies();
page().reload();
PlaywrightAssertions.assertThat(page()).hasURL("**/login");
~~~

### 17.5 Two isolated users

~~~text
BrowserContext customerContext = newContext();
BrowserContext adminContext = newContext();
Page customer = customerContext.newPage();
Page admin = adminContext.newPage();

customer.navigate("https://example.test/customer");
admin.navigate("https://example.test/admin");

// Log in each Page with its own Page Object and assert role-specific behavior.
~~~

The factory closes both tracked contexts in reverse creation order.

---

## 18. Cookies, storage, permissions, and emulation

### 18.1 Cookies

~~~text
context().addCookies(List.of(
        new BrowserContext.Cookie("feature", "new-checkout")
                .setUrl("https://example.test")));
Assert.assertFalse(context().cookies().isEmpty());
context().clearCookies();
~~~

### 18.2 Local and session storage

~~~text
page().evaluate("({key, value}) => localStorage.setItem(key, value)",
        Map.of("key", "experiment", "value", "B"));
page().evaluate("sessionStorage.setItem('wizardStep', '2')");
page().reload();
~~~

Use storage setup only when it is part of the fixture. Test user-facing behavior
through normal actions.

### 18.3 Permissions and geolocation

~~~text
context().grantPermissions(List.of("geolocation"),
        new BrowserContext.GrantPermissionsOptions()
                .setOrigin("https://example.test"));
context().setGeolocation(new Geolocation()
        .setLatitude(51.5072)
        .setLongitude(-0.1276));
page().navigate("https://example.test/nearby");
~~~

If a newer method is not available in your dependency, configure it through the
tracked Browser.NewContextOptions overload.

### 18.4 Locale, timezone, color scheme, user agent, mobile

~~~text
new Browser.NewContextOptions()
        .setLocale("fr-FR")
        .setTimezoneId("Europe/Paris")
        .setColorScheme(ColorScheme.DARK)
        .setUserAgent("automation-test-agent")
        .setViewportSize(390, 844)
        .setHasTouch(true)
        .setIsMobile(true);
~~~

Use focused tests or a TestNG matrix; do not multiply every test by every
emulation combination.

---

## 19. Responsive and viewport tests

The current factory reads viewport values at startup:

~~~text
-Dviewport.width=1920 -Dviewport.height=1080
~~~

Examples:

~~~text
mvn clean test -Dviewport.width=1920 -Dviewport.height=1080
mvn clean test -Dviewport.width=390 -Dviewport.height=844
~~~

Assert responsive behavior, not screen coordinates:

~~~text
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Open menu"))).isVisible();
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.NAVIGATION))
        .isHidden();
~~~

---

## 20. JavaScript evaluation

Use evaluate only for browser operations that cannot be expressed as a real user
action. Do not use it to set a value when the scenario claims to test typing,
validation, or keyboard events.

~~~text
Object text = page().evaluate("() => document.body.innerText");
Assert.assertTrue(text.toString().contains("Order"));

page().evaluate("() => window.scrollTo(0, document.body.scrollHeight)");
page().evaluate("({key, value}) => localStorage.setItem(key, value)",
        Map.of("key", "flag", "value", "on"));
~~~

---

## 21. Screenshots, PDF, trace, video, and diagnostics

### 21.1 Screenshot

~~~text
Path image = Paths.get("target", "artifacts", "checkout.png");
Files.createDirectories(image.getParent());
page().screenshot(new Page.ScreenshotOptions()
        .setPath(image)
        .setFullPage(true));
~~~

### 21.2 Element screenshot

~~~text
page().getByTestId("invoice").screenshot(
        new Locator.ScreenshotOptions()
                .setPath(Paths.get("target", "artifacts", "invoice.png")));
~~~

### 21.3 PDF

~~~text
page().pdf(new Page.PdfOptions()
        .setPath(Paths.get("target", "artifacts", "invoice.pdf")));
~~~

PDF generation is Chromium-specific and normally used headless.

### 21.4 Trace

Start and stop tracing on the current context and stop it before teardown:

~~~text
Path trace = Paths.get("target", "artifacts", "trace.zip");
Files.createDirectories(trace.getParent());
context().tracing().start(new Tracing.StartOptions()
        .setScreenshots(true)
        .setSnapshots(true)
        .setSources(true));
try {
    // Test actions
} finally {
    context().tracing().stop(new Tracing.StopOptions().setPath(trace));
}
~~~

A TestNG listener can stop a trace on failure. Do not start a second trace while
one is already running.

### 21.5 Video

Video is configured when a context is created. The current factory does not
expose recordVideoDir; add a tracked context-options overload for opt-in video.
Video is finalized when the context closes, so archive it after Maven teardown.

### 21.6 Console, page errors, and crash

~~~text
List<String> errors = new CopyOnWriteArrayList<>();
Consumer<ConsoleMessage> listener = message -> {
    if ("error".equals(message.type())) {
        errors.add(message.text());
    }
};
page().onConsoleMessage(listener);
page().navigate("https://example.test");
page().offConsoleMessage(listener);
Assert.assertTrue(errors.isEmpty(), errors.toString());
~~~

Use page-error and crash listeners for diagnosis. Remove listeners when the
scenario finishes so they do not affect later actions.

---

## 22. Accessibility-focused scenarios

Role locators improve testability and accessibility, but they are not a complete
WCAG audit. Pair interaction tests with an accessibility scanner when required.

### 22.1 Keyboard focus

~~~text
page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Open menu")).press("Enter");
page().keyboard().press("Tab");
Assert.assertEquals(
        page().evaluate("() => document.activeElement?.id"),
        "first-menu-item");
~~~

### 22.2 ARIA states and live regions

~~~text
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName("Submit")))
        .hasAttribute("aria-describedby", "submit-help");
PlaywrightAssertions.assertThat(page().getByRole(AriaRole.STATUS))
        .containsText("Saved");
~~~

Cover focus order, visible focus, keyboard-only completion, disabled/expanded/
selected state, error association, dialog name, and meaningful image alt text.

---

## 23. Time, polling, and asynchronous UI

### 23.1 Wait for a real UI state

~~~text
page().getByTestId("refresh").click();
PlaywrightAssertions.assertThat(page().getByTestId("last-updated"))
        .containsText("just now");
~~~

### 23.2 Bounded polling only when no event exists

~~~text
long deadline = System.nanoTime()
        + Duration.ofSeconds(10).toNanos();
while (System.nanoTime() < deadline) {
    if ("complete".equals(
            page().getByTestId("job-state").textContent())) {
        break;
    }
    page().waitForTimeout(100);
}
Assert.assertEquals(page().getByTestId("job-state").textContent(),
        "complete");
~~~

This is a last-resort pattern. Prefer a locator assertion, response wait, or
application status event. Never use Thread.sleep as a general synchronization
strategy.

Recent Playwright versions expose a context clock for deterministic timers. Check
the dependency version before using it for expiry banners, countdowns, and
scheduled jobs.

---

## 24. Negative, boundary, and security scenarios

| Area            | Required variations                                              |
|-----------------|------------------------------------------------------------------|
| Required fields | Empty, whitespace, all empty                                     |
| Formats         | Invalid email/date/phone/URL, Unicode, spaces                    |
| Length          | Minimum, maximum, one below, one above, pasted long text         |
| Numbers         | Zero, negative, decimal, huge, non-numeric, locale separator     |
| Files           | Wrong extension, oversized, empty, duplicate, corrupt            |
| Auth            | Wrong password, locked, expired, logout, 401, 403                |
| Authorization   | Hidden action, direct URL, tenant isolation                      |
| Concurrency     | Double submit, two tabs, stale version, conflict                 |
| Network         | Slow, timeout, offline, 4xx, 5xx, malformed JSON, retry          |
| Navigation      | Redirect loop, back after save, refresh while loading, deep link |
| Data            | Empty, one item, page boundary, duplicate sort, huge list        |
| Input safety    | HTML, script text, SQL-like text, encoded/path text              |
| Accessibility   | Keyboard only, focus trap, live region, screen-reader name       |
| Browser matrix  | Chromium, Firefox, WebKit where supported                        |

Assert safe output for security strings; do not execute injected text.

---

## 25. Data-driven TestNG tests

Each DataProvider invocation receives its own BaseTest lifecycle.

~~~java
package tests;

import base.BaseTest;
import pages.LoginPage;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public final class LoginDataDrivenTest extends BaseTest {

    @DataProvider(name = "invalidUsers", parallel = true)
    public Object[][] invalidUsers() {
        return new Object[][]{
                {"", "wrong", "Email is required"},
                {"not-an-email", "wrong", "Enter a valid email"},
                {"user@example.com", "wrong", "Invalid credentials"}
        };
    }

    @Test(dataProvider = "invalidUsers")
    public void invalidLoginShowsExpectedError(
            String username,
            String password,
            String expectedMessage) {

        LoginPage login = new LoginPage(page())
                .open("https://example.test");
        login.signInAs(username, password);
        Assert.assertTrue(
                login.errorMessage().textContent()
                        .contains(expectedMessage));
    }
}
~~~

Do not reuse a mutable Page or context between data rows. Use TestNG parameters
or Maven properties for environment values; do not use Scanner.

Use groups for smoke, regression, integration, and accessibility. Avoid tests
that depend on another test's order or data.

---

## 26. Parallel execution

The factory supports TestNG parallel methods and parallel data providers:

~~~xml
<suite name="Playwright suite" parallel="methods" thread-count="4">
    <test name="UI tests">
        <packages>
            <package name="tests"/>
        </packages>
    </test>
</suite>
~~~

Parallel rules:

- keep browser objects instance-scoped
- never share a Page between worker threads
- do not call start twice on one thread
- do not call quit from a different thread
- make backend data unique
- use newContext for two users inside one test
- use one independent Playwright stack per UI thread

Two contexts in one test are isolated but do not make Java UI objects thread-safe.
For truly concurrent business behavior, use independent test invocations or API
concurrency tooling.

---

## 27. Maven, browser, debug, and Jenkins runs

Current defaults:

| Property              | Default  |
|-----------------------|----------|
| browser               | chromium |
| playwright.debug      | off      |
| headless              | true     |
| slowMo                | 0        |
| browser.launchTimeout | 30000 ms |
| baseUrl               | empty    |
| viewport.width        | 1920     |
| viewport.height       | 1080     |
| ignoreHTTPSErrors     | false    |

### 27.1 Normal

~~~text
mvn clean test
~~~

### 27.2 Browser selection

~~~text
mvn clean test -Dbrowser=chromium
mvn clean test -Dbrowser=firefox
mvn clean test -Dbrowser=webkit
~~~

### 27.3 Visible debugging

~~~text
mvn clean test -Dheadless=false
mvn clean test -Dheadless=false -DslowMo=250
~~~

### 27.4 Debug streams

~~~text
mvn clean test -Dplaywright.debug=api
mvn clean test -Dplaywright.debug=browser
mvn clean test -Dplaywright.debug=all
mvn clean test -Dplaywright.debug=api,browser
~~~

The factory maps these values to the Playwright driver's DEBUG environment
variable. pw:api logs Playwright API calls; pw:browser logs browser-process
activity. A trace and screenshot are still useful for post-run diagnosis.

### 27.5 Jenkins

~~~text
mvn -B clean test -Dbrowser=\${BROWSER} -Dheadless=true -Dplaywright.debug=\${PLAYWRIGHT_DEBUG} -DbaseUrl=\${BASE_URL}
~~~

Archive target/surefire-reports and target/artifacts. Keep passwords, cookies,
storage state, authorization headers, and signed URLs out of logs and artifacts.

---

## 28. Complete form-to-receipt example

This is a complete class shape. Replace the URL and locators with your POM
classes; the lifecycle still comes from BaseTest.

~~~java
package tests;

import base.BaseTest;
import com.microsoft.playwright.AriaRole;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import org.testng.Assert;
import org.testng.annotations.Test;

public final class OrderReceiptTest extends BaseTest {

    @Test
    public void savingOrderOpensReceiptInNewTab() {
        page().navigate("https://example.test/orders/new");
        page().getByLabel("Customer name").fill("Mira");
        page().getByLabel("Customer email").fill("mira@example.com");

        Page receipt = context().waitForPage(() -> {
            Response response = page().waitForResponse(
                    value -> value.url().contains("/api/orders")
                            && "POST".equals(value.request().method()),
                    () -> page().getByRole(
                            AriaRole.BUTTON,
                            new Page.GetByRoleOptions()
                                    .setName("Save")
                                    .setExact(true))
                            .click());
            Assert.assertEquals(response.status(), 201);
        });

        receipt.waitForLoadState();
        PlaywrightAssertions.assertThat(receipt)
                .hasURL("**/orders/*/receipt");
        PlaywrightAssertions.assertThat(
                receipt.getByRole(
                        AriaRole.HEADING,
                        new Page.GetByRoleOptions()
                                .setName("Receipt")))
                .isVisible();
        PlaywrightAssertions.assertThat(
                page().getByRole(AriaRole.STATUS))
                .containsText("Order saved");
    }
}
~~~

---

## 29. Complete two-user example

~~~java
package tests;

import base.BaseTest;
import com.microsoft.playwright.AriaRole;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import org.testng.annotations.Test;

public final class MultiUserOrderTest extends BaseTest {

    @Test
    public void customerOrderBecomesVisibleToAdmin() {
        BrowserContext customerContext = newContext();
        BrowserContext adminContext = newContext();
        Page customer = customerContext.newPage();
        Page admin = adminContext.newPage();

        customer.navigate("https://example.test/customer/orders/new");
        admin.navigate("https://example.test/admin/orders");

        customer.getByLabel("Description")
                .fill("Replacement keyboard");
        customer.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                        .setName("Submit order"))
                .click();

        PlaywrightAssertions.assertThat(
                customer.getByText("Pending approval"))
                .isVisible();

        admin.reload();
        PlaywrightAssertions.assertThat(
                admin.getByText("Replacement keyboard"))
                .isVisible();
    }
}
~~~

Both contexts are tracked and closed by quit. Each context has independent
cookies, storage, permissions, and pages.

---

## 30. Complete mocked-network example

~~~java
package tests;

import base.BaseTest;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import org.testng.annotations.Test;

public final class ProductMockTest extends BaseTest {

    @Test
    public void productPageRendersControlledResponse() {
        context().route("**/api/products", route -> route.fulfill(
                new Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType("application/json")
                        .setBody(
                                "{\"products\":[{\"name\":\"Mock keyboard\"}]}")
        ));

        try {
            page().navigate("https://example.test/products");
            PlaywrightAssertions.assertThat(
                    page().getByText("Mock keyboard"))
                    .isVisible();
        } finally {
            context().unroute("**/api/products");
        }
    }
}
~~~

The finally block prevents a mock from affecting another action in the same
test. BaseTest closes the context afterward.

---

## 31. Page Object patterns for eventful actions

A Page Object may expose the action, while the test owns the event wait:

~~~text
// In OrderFormPage
public void clickSave() {
    saveButton.click();
}

// In the test
OrderFormPage form = new OrderFormPage(page());
Page receipt = context().waitForPage(form::clickSave);
OrderReceiptPage receiptPage = new OrderReceiptPage(receipt);
~~~

This keeps browser mechanics reusable and keeps the test's expected event visible.

For a component used on multiple pages, create a component object that receives
the root Locator or Page. Do not make component objects call PlaywrightFactory.

---

## 32. Common failures and fixes

| Symptom                              | Cause                              | Fix                                       |
|--------------------------------------|------------------------------------|-------------------------------------------|
| PlaywrightSession cannot be resolved | Non-required wrapper referenced    | Use BaseTest and factory accessors        |
| Click hangs                          | Dialog opened without handler      | Register onceDialog before click          |
| New tab missed                       | Wait started after click           | Wrap click in waitForPage or waitForPopup |
| Wrong tab selected                   | Fixed pages index                  | Use returned Page object                  |
| Download disappears                  | Context teardown removed temp file | saveAs immediately                        |
| OS file picker appears               | Desktop picker used                | setInputFiles or waitForFileChooser       |
| Field not found in iframe            | Wrong document                     | frameLocator or page.frame                |
| Route ignored                        | Service worker owns request        | Check worker and route scope              |
| Cross-test cookies                   | Shared static context              | Instance fields and ThreadLocal factory   |
| Cleanup hides test failure           | Teardown exception became primary  | Keep BaseTest suppression                 |
| Flaky sleeps                         | Sleep is not readiness             | Wait for locator, event, URL, or response |
| Extra context left open              | Direct browser.newContext          | Use tracked newContext or close finally   |
| IntelliJ Markdown errors             | Fragment inspected as Java         | Use text fences                           |
| Browser absent in Jenkins            | CI is headless                     | Use artifacts or approved display         |

---

## 33. Exhaustive coverage checklist

### Browser and lifecycle

- [ ] Chromium, Firefox, and WebKit where supported
- [ ] Headless local/CI run
- [ ] Visible local run
- [ ] pw:api debug
- [ ] pw:browser debug
- [ ] Combined debug
- [ ] Launch timeout and startup failure
- [ ] Cleanup failure preserves assertion failure
- [ ] Parallel methods and data providers

### Navigation and pages

- [ ] Direct URL and base URL
- [ ] Title and URL
- [ ] Redirect
- [ ] Reload, back, forward, hash
- [ ] New tab
- [ ] JavaScript popup
- [ ] Multiple pages
- [ ] Unknown page listener
- [ ] Popup opener/close
- [ ] Popup blocked/never opened
- [ ] Browser chrome explicitly out of scope

### Forms

- [ ] Text, email, password, search, number, URL
- [ ] Textarea and contenteditable
- [ ] Checkbox, switch, radio
- [ ] Native select
- [ ] Combobox and option
- [ ] Autocomplete
- [ ] Date, time, range, color
- [ ] Keyboard-only
- [ ] Required and client validation
- [ ] Server validation
- [ ] Loading, disabled, success
- [ ] Duplicate submission/idempotency
- [ ] Reset, cancel, unsaved changes
- [ ] Wizard and final confirmation

### Files and dialogs

- [ ] Alert, confirm, prompt, beforeunload
- [ ] Single and multiple upload
- [ ] File chooser and clear
- [ ] Download and saveAs
- [ ] Filename/content/status
- [ ] Download cancellation/failure
- [ ] Extension/size/corrupt-file validation

### Document structure

- [ ] Iframe and nested iframe
- [ ] Named/URL frame
- [ ] Open shadow DOM
- [ ] Closed shadow-DOM limitation
- [ ] Table/grid row and cell
- [ ] Sort/filter/pagination
- [ ] Infinite scroll/virtualization
- [ ] Empty/loading/error/partial states

### Interaction

- [ ] Hover and tooltip
- [ ] Right click/context menu
- [ ] Double click
- [ ] Drag/drop
- [ ] Keyboard/focus
- [ ] Canvas coordinates if required
- [ ] Mobile/touch if supported

### Network and data

- [ ] Request and response waits
- [ ] Status/body assertions
- [ ] Failed-request diagnostics
- [ ] Fulfill/mock and abort
- [ ] Route cleanup
- [ ] Offline
- [ ] Service-worker behavior
- [ ] WebSocket/worker behavior
- [ ] API setup plus UI assertion
- [ ] API context disposal

### Auth and emulation

- [ ] UI login
- [ ] Storage-state save/reuse
- [ ] Expiry/logout/401/403
- [ ] Customer/admin contexts
- [ ] Cookie and storage isolation
- [ ] Permission/geolocation
- [ ] Locale/timezone/color scheme
- [ ] User agent/mobile/touch
- [ ] Responsive viewports

### Observability and CI

- [ ] Screenshot
- [ ] Element screenshot
- [ ] Trace
- [ ] Video through context options
- [ ] Chromium PDF
- [ ] Console/page errors/crash
- [ ] Jenkins artifacts
- [ ] Secrets excluded

---

## 34. Rules for robust tests

1. Extend BaseTest; do not duplicate lifecycle code.
2. Construct Page Objects with a Page on the owning test thread.
3. Prefer role, label, and test-id locators.
4. Put waits around the action that triggers an event.
5. Assert the business result, not only that a click returned.
6. Wait for the specific response when a result depends on one API call.
7. Use tracked newContext for additional users.
8. Never share Playwright objects across TestNG threads.
9. Save downloads, screenshots, and traces before teardown.
10. Dispose API contexts that the factory does not own.
11. Keep debug and browser selection as -D properties.
12. Keep credentials and storage state out of source control.
13. Make backend data unique under parallel execution.
14. Avoid Thread.sleep; wait for observable conditions.
15. Keep assertions in tests and mechanics in Page Objects.
16. Check the Playwright Java version before using a newer API.

---

## 35. Advanced event and capability catalogue

The common scenarios above use the event waits that most tests need. The
following catalogue prevents less-common browser behavior from being forgotten.
Use an event only when it proves the requirement or provides useful diagnostics.

| Event or API                       | Scope           | Typical scenario                          |
|------------------------------------|-----------------|-------------------------------------------|
| waitForPage / onPage               | BrowserContext  | Any new tab or popup in a context         |
| waitForPopup / onPopup             | Page            | Popup opened by this page                 |
| waitForDownload / onDownload       | Page or context | Export, generated report, attachment      |
| waitForFileChooser / onFileChooser | Page            | Custom upload button                      |
| onceDialog / onDialog              | Page or context | Alert, confirm, prompt, beforeunload      |
| waitForRequest / onRequest         | Page or context | Request payload or request count          |
| waitForResponse / onResponse       | Page or context | HTTP status/body after a save             |
| onRequestFinished                  | Page or context | Confirm full response body finished       |
| onRequestFailed                    | Page or context | DNS, timeout, aborted request diagnostics |
| onConsoleMessage                   | Page or context | Browser console errors or warnings        |
| onPageError / onWebError           | Page or context | Unhandled JavaScript exception            |
| onCrash                            | Page            | Browser renderer crash handling           |
| onClose / onPageClose              | Page or context | Unexpected page/context closure           |
| onFrameAttached/detached/navigated | Page or context | Dynamic iframe lifecycle                  |
| waitForWebSocket / onWebSocket     | Page            | Real-time connection established          |
| waitForWorker / onWorker           | Page            | Dedicated worker created                  |
| waitForFunction                    | Page            | A browser condition with no locator       |
| waitForCondition                   | Page or context | A bounded custom condition (newer API)    |
| waitForConsoleMessage              | Page or context | Console message is the readiness signal   |
| route / routeFromHAR               | Page or context | Mock, replay, modify, or abort HTTP       |
| routeWebSocket                     | Page or context | Mock or observe WebSocket messages        |
| addInitScript                      | Page or context | Seed a browser flag before app scripts    |
| exposeFunction / exposeBinding     | Page or context | Controlled test hook into app code        |
| newCDPSession                      | BrowserContext  | Chromium-only protocol scenario           |
| clock                              | BrowserContext  | Deterministic timers (version-gated)      |
| credentials                        | BrowserContext  | Virtual WebAuthn/passkey (newer API)      |

When an event can happen more than once, filter by URL, method, page identity,
or payload. Remove listeners with the corresponding off method. One-off waits
are preferable to permanent listeners.

### 35.1 Browser context routes and HAR replay

For a large, stable set of responses, a HAR file can replay the network. For a
single endpoint, route.fulfill is easier to understand. Always clean a route or
use a fresh tracked context. HAR files can contain headers and data, so review
them before committing or publishing them.

### 35.2 Initialization hooks

Use addInitScript before navigation to seed a deterministic flag, freeze a
non-critical random source, or install a test-only hook. Keep the hook minimal;
overriding application APIs can hide real defects.

### 35.3 WebAuthn, HTTP credentials, and extensions

Passkeys, HTTP basic authentication, persistent profiles, browser extensions,
proxy settings, and custom launch arguments require Browser.NewContextOptions or
BrowserType.LaunchOptions. The current factory deliberately exposes only the
common cross-browser settings. Add a tracked, validated options overload instead
of bypassing lifecycle ownership.

---

## 36. Official references

- Pages, tabs, and popups: https://playwright.dev/java/docs/pages
- Events and event waits: https://playwright.dev/java/docs/events
- Dialogs: https://playwright.dev/java/docs/dialogs
- Downloads: https://playwright.dev/java/docs/downloads
- Frames: https://playwright.dev/java/docs/frames
- Locators: https://playwright.dev/java/docs/locators
- Network: https://playwright.dev/java/docs/network
- Authentication: https://playwright.dev/java/docs/auth
- Browser contexts and isolation: https://playwright.dev/java/docs/browser-contexts
- BrowserContext API: https://playwright.dev/java/docs/api/class-browsercontext
- Page API: https://playwright.dev/java/docs/api/class-page
- API testing: https://playwright.dev/java/docs/api-testing
- Videos: https://playwright.dev/java/docs/videos

The API pages are version-sensitive. If IntelliJ cannot resolve a method, check
the Playwright version in pom.xml and open the matching official documentation.

---

## 37. Final mental model

The factory answers: where browser resources come from and who closes them.
BaseTest connects that lifecycle to TestNG. Page Objects describe how the
application is used. The @Test method describes what behavior is expected and
which page, popup, dialog, response, file, or failure condition proves it.

~~~mermaid
sequenceDiagram
    participant T as TestNG
    participant P as Page
    participant C as Context
    participant E as Event
    T->>P: Fill and click
    T->>C: Wait for event
    C-->>E: Page, response, download, dialog
    E-->>T: Returned Playwright object
    T->>E: Assert behavior
~~~
