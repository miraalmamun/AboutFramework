# Excel Data Provider with a Real Playwright Test

## Purpose

This guide shows how to use your Excel data-provider class with:

- Java 25
- Maven
- TestNG
- Playwright for Java
- Page Object Model
- BaseTest and PlaywrightFactory

The complete flow is:

~~~text
Excel file
    -> ExcelDataProvider
    -> TestDataProviders
    -> LoginExcelTest
    -> LoginPage
    -> Playwright Page
~~~

The test does not manually call the data-provider method. TestNG calls it automatically because the test references the provider name in the test annotation.

---

## 1. Project structure

A practical project structure is:

~~~text
src
└── test
    ├── java
    │   ├── base
    │   │   └── BaseTest.java
    │   ├── dataproviders
    │   │   └── TestDataProviders.java
    │   ├── pages
    │   │   └── LoginPage.java
    │   └── tests
    │       └── LoginExcelTest.java
    └── resources
        └── testdata
            └── loginData.xlsx
~~~

Your uploaded provider currently imports:

~~~java
import utilities.ExcelDataProvider;
~~~

That import is correct if the Excel reader declares:

~~~java
package utilities;
~~~

If the Excel reader declares:

~~~java
package dataproviders;
~~~

then use:

~~~java
import dataproviders.ExcelDataProvider;
~~~

The package declaration and the import must match the actual class location.

---

## 2. Excel file

Place the workbook here:

~~~text
src/test/resources/testdata/loginData.xlsx
~~~

Create a worksheet named:

~~~text
LoginData
~~~

Use headers in the first row:

| username | password | expectedResult |
|---|---|---|
| validUser | validPassword | SUCCESS |
| invalidUser | wrongPassword | INVALID_CREDENTIALS |

The Java code reads the Excel cells using their header names:

~~~java
data.get("username");
data.get("password");
data.get("expectedResult");
~~~

Therefore, the names must match the Excel headers exactly.

If the Excel header is written as userName but Java requests username, the lookup will not find the value.

---

## 3. ExcelDataProvider responsibility

The Excel reader is responsible for:

1. Finding the workbook on the classpath.
2. Opening the workbook.
3. Finding the requested worksheet.
4. Reading the header row.
5. Rejecting blank or duplicate headers.
6. Reading each nonblank data row.
7. Converting each row into a map.
8. Returning TestNG-compatible Object[][] data.
9. Closing the workbook and input stream.

It does not:

- Create a browser.
- Create a Playwright page.
- Perform login.
- Contain assertions.
- Know anything about a particular test scenario.

For example, one Excel row becomes:

~~~java
Map<String, String> row = Map.of(
        "username", "validUser",
        "password", "validPassword",
        "expectedResult", "SUCCESS"
);
~~~

The provider wraps that map like this:

~~~text
Object[][] {
    { row1Map },
    { row2Map }
}
~~~

Because each row contains one map, the test method receives one parameter:

~~~java
public void loginTest(Map<String, String> data)
~~~

---

## 4. TestNG data-provider class

Create or update:

~~~text
src/test/java/dataproviders/TestDataProviders.java
~~~

~~~java
package dataproviders;

import org.testng.annotations.DataProvider;
import utilities.ExcelDataProvider;

import java.io.IOException;

public final class TestDataProviders {

    private TestDataProviders() {
        // Utility class
    }

    @DataProvider(name = "excelLoginData", parallel = false)
    public static Object[][] excelLoginData() throws IOException {
        return ExcelDataProvider.getExcelDataFromClasspath(
                "testdata/loginData.xlsx",
                "LoginData"
        );
    }
}
~~~

### Explanation

~~~java
@DataProvider(name = "excelLoginData", parallel = false)
~~~

This registers a TestNG data provider named excelLoginData.

~~~java
public static Object[][] excelLoginData()
~~~

This method returns all Excel rows in the format expected by TestNG.

~~~text
testdata/loginData.xlsx
~~~

This is a classpath-relative path. Do not use the complete source path when using the classpath method.

~~~text
LoginData
~~~

This is the worksheet name.

~~~text
parallel = false
~~~

This keeps data rows sequential while the framework is being established.

---

## 5. Page Object

Create:

~~~text
src/test/java/pages/LoginPage.java
~~~

~~~java
package pages;

import com.microsoft.playwright.AriaRole;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public final class LoginPage {

    private final Page page;

    private final Locator usernameInput;
    private final Locator passwordInput;
    private final Locator loginButton;
    private final Locator dashboardHeading;
    private final Locator invalidCredentialsMessage;

    public LoginPage(Page page) {
        this.page = page;

        usernameInput = page.getByLabel("Username");
        passwordInput = page.getByLabel("Password");

        loginButton = page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                        .setName("Login")
                        .setExact(true)
        );

        dashboardHeading = page.getByRole(
                AriaRole.HEADING,
                new Page.GetByRoleOptions()
                        .setName("Dashboard")
                        .setExact(true)
        );

        invalidCredentialsMessage =
                page.getByText("Invalid username or password");
    }

    public void open() {
        // This assumes PlaywrightFactory configured a baseUrl.
        page.navigate("/login");
    }

    public void login(String username, String password) {
        usernameInput.fill(username);
        passwordInput.fill(password);
        loginButton.click();
    }

    public void assertLoginSuccessful() {
        assertThat(dashboardHeading).isVisible();
    }

    public void assertInvalidCredentials() {
        assertThat(invalidCredentialsMessage).isVisible();
    }
}
~~~

Replace these values with the actual values from your application:

- Username
- Password
- Login
- Dashboard
- Invalid username or password
- /login

The Page Object owns locators and UI actions. The test class should not contain raw locators.

---

## 6. Real Excel-driven test

Create or update:

~~~text
src/test/java/tests/LoginExcelTest.java
~~~

~~~java
package tests;

import base.BaseTest;
import dataproviders.TestDataProviders;
import org.testng.annotations.Test;
import pages.LoginPage;

import java.util.Locale;
import java.util.Map;

public final class LoginExcelTest extends BaseTest {

    @Test(
            dataProvider = "excelLoginData",
            dataProviderClass = TestDataProviders.class
    )
    public void loginShouldBehaveAsExpected(
            Map<String, String> data
    ) {
        String username = required(data, "username");
        String password = required(data, "password");

        String expectedResult = required(data, "expectedResult")
                .toUpperCase(Locale.ROOT);

        LoginPage loginPage = new LoginPage(page());

        loginPage.open();
        loginPage.login(username, password);

        switch (expectedResult) {
            case "SUCCESS" ->
                    loginPage.assertLoginSuccessful();

            case "INVALID_CREDENTIALS" ->
                    loginPage.assertInvalidCredentials();

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported expectedResult: "
                                    + expectedResult
                    );
        }
    }

    private static String required(
            Map<String, String> data,
            String columnName
    ) {
        String value = data.get(columnName);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Excel column is missing or blank: "
                            + columnName
            );
        }

        return value.strip();
    }
}
~~~

---

## 7. Understanding the test annotation

This is the most important part:

~~~java
@Test(
        dataProvider = "excelLoginData",
        dataProviderClass = TestDataProviders.class
)
~~~

### dataProvider

~~~java
dataProvider = "excelLoginData"
~~~

This is the name registered in the data-provider annotation.

### dataProviderClass

~~~java
dataProviderClass = TestDataProviders.class
~~~

This tells TestNG that the provider is located in another class.

Because the provider is external, both attributes are required.

If the provider were inside the same test class, use:

~~~java
@Test(dataProvider = "excelLoginData")
~~~

The dataProviderClass attribute is not needed in that situation.

### Test method parameter

~~~java
public void loginShouldBehaveAsExpected(
        Map<String, String> data
)
~~~

The provider returns one map for each Excel row, so the test receives one map.

TestNG does not require this:

~~~java
TestDataProviders.excelLoginData();
~~~

TestNG invokes the provider automatically.

---

## 8. What happens during execution

Assume the worksheet contains two rows:

~~~text
Row 1 -> validUser -> SUCCESS
Row 2 -> invalidUser -> INVALID_CREDENTIALS
~~~

TestNG performs this process:

~~~text
1. TestNG calls excelLoginData()
2. ExcelDataProvider reads loginData.xlsx
3. TestNG receives two data rows

4. @BeforeMethod runs
5. PlaywrightFactory.start() creates Playwright, Browser,
   BrowserContext, and Page
6. loginShouldBehaveAsExpected(row 1) runs
7. LoginPage performs the successful-login assertion
8. @AfterMethod runs
9. PlaywrightFactory.quit() closes the resources

10. @BeforeMethod runs again
11. A new BrowserContext and Page are created
12. loginShouldBehaveAsExpected(row 2) runs
13. LoginPage performs the invalid-login assertion
14. @AfterMethod runs
15. PlaywrightFactory.quit() closes the resources
~~~

The test method runs once for every data row.

---

## 9. How BaseTest participates

The test extends:

~~~java
public final class LoginExcelTest extends BaseTest
~~~

BaseTest starts Playwright before each test invocation:

~~~java
@BeforeMethod(alwaysRun = true)
public final void setUpPlaywright() {
    PlaywrightFactory.start();
}
~~~

The test can then use:

~~~java
page()
~~~

because BaseTest provides the page.

After the invocation, BaseTest closes the resources:

~~~java
@AfterMethod(alwaysRun = true)
public final void tearDownPlaywright(...) {
    PlaywrightFactory.quit();
}
~~~

The test class should not create another browser manually.

---

## 10. How to run the test

If PlaywrightFactory supports baseUrl and headless properties, run:

~~~text
mvn clean test -Dtest=LoginExcelTest -DbaseUrl=https://your-application-url -Dheadless=false
~~~

The base URL should normally be the application root:

~~~text
https://your-application-url
~~~

Then the Page Object can navigate to:

~~~java
page.navigate("/login");
~~~

For a headless run:

~~~text
mvn clean test -Dtest=LoginExcelTest -DbaseUrl=https://your-application-url -Dheadless=true
~~~

For another browser:

~~~text
mvn clean test -Dtest=LoginExcelTest -Dbrowser=firefox -Dheadless=true
~~~

The same Java test can run locally or in Jenkins with different Maven properties.

---

## 11. Why this design is recommended

This design separates responsibilities:

| Responsibility | Class |
|---|---|
| Read Excel workbook | ExcelDataProvider |
| Register TestNG data | TestDataProviders |
| Create and close Playwright | PlaywrightFactory |
| Test lifecycle | BaseTest |
| Locators and browser actions | LoginPage |
| Scenario and expected result | LoginExcelTest |

The test does not know how Excel is opened. The data provider does not know how the browser works. The Page Object does not know where the test data came from.

This makes the framework easier to maintain.

---

## 12. Current map-based version and future typed version

The current implementation uses:

~~~java
Map<String, String> data
~~~

That is appropriate while the current reader returns maps.

A stronger long-term model is an immutable record:

~~~java
public record LoginData(
        String username,
        String password,
        LoginOutcome expectedOutcome
) {
}
~~~

Then the test would receive:

~~~java
public void loginShouldBehaveAsExpected(LoginData testCase) {
    LoginPage loginPage = new LoginPage(page());

    loginPage.open();
    loginPage.login(
            testCase.username(),
            testCase.password()
    );

    switch (testCase.expectedOutcome()) {
        case SUCCESS -> loginPage.assertLoginSuccessful();
        case INVALID_CREDENTIALS ->
                loginPage.assertInvalidCredentials();
    }
}
~~~

The migration can happen later. You do not need to change the reader and test simultaneously.

---

## 13. Common mistakes

### Wrong resource path

Incorrect for classpath loading:

~~~text
src/test/resources/testdata/loginData.xlsx
~~~

Correct:

~~~text
testdata/loginData.xlsx
~~~

### Wrong worksheet name

If the workbook contains LoginData, use:

~~~java
"LoginData"
~~~

A different spelling causes a worksheet-not-found error.

### Test does not extend BaseTest

Incorrect for a browser test:

~~~java
public final class LoginExcelTest {
~~~

Correct:

~~~java
public final class LoginExcelTest extends BaseTest {
~~~

### Provider name does not match

Provider:

~~~java
@DataProvider(name = "excelLoginData")
~~~

Test:

~~~java
dataProvider = "excelLoginData"
~~~

These names must match exactly.

### Header name does not match

Excel:

~~~text
expectedResult
~~~

Java:

~~~java
data.get("expectedResult")
~~~

These names must match exactly.

### No assertions

A test that only reads or prints values is not a UI test. The real test must perform an action and verify an expected result.

### Duplicate browser setup

Do not call PlaywrightFactory.start() inside the test method if BaseTest already starts it in BeforeMethod.

---

## 14. Final execution model

~~~text
LoginExcelTest
    |
    | references provider name
    v
TestNG
    |
    | invokes excelLoginData()
    v
TestDataProviders
    |
    | calls ExcelDataProvider
    v
ExcelDataProvider
    |
    | reads loginData.xlsx
    v
Map<String, String>
    |
    | supplied to test method
    v
LoginPage
    |
    | performs Playwright actions
    v
BaseTest and PlaywrightFactory
    |
    | create and close browser resources
    v
Test result
~~~

This is the complete real-test usage of ExcelDataProvider.

