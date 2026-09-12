# Test data and framework recommendation

## Purpose

This document reviews the uploaded Playwright/TestNG test-data files and describes a practical production direction for a framework being built from scratch.

Target stack:

- Java 25
- Maven
- TestNG
- Playwright for Java
- Page Object Model (POM)
- Local execution and Jenkins execution
- Eventual TestNG parallel execution

The recommendation is deliberately incremental. The current readers are useful and do not need to be thrown away. First make the data safe and predictable, then introduce the typed model and stronger validation.

## Executive recommendation

Use these rules as the framework foundation:

1. Remove plaintext credentials from the repository immediately. The uploaded JSON and Excel files contain passwords. Treat those values as exposed: rotate or change the accounts, remove the secrets from Git history if they were committed, and use Jenkins Credentials Binding or environment variables.
2. Choose one canonical automation data shape. JSON is usually the best canonical format for automation-owned cases because it is reviewable, diffable, and easy to validate. Keep Excel only when business users genuinely maintain the data.
3. Use one schema for JSON and Excel. A case loaded from either source should become the same Java object.
4. Introduce an immutable typed model such as LoginData and an enum such as LoginOutcome. Keep Map<String, String> only as a short-lived reader boundary.
5. Make the data provider thin. It should load, validate, filter enabled rows, and return one typed object per TestNG invocation. It should not perform browser actions or UI login.
6. Start with parallel = false while the framework is being stabilised. Enable parallel data rows only after account, file, database, and environment isolation have been designed.
7. Keep Playwright lifecycle in the reviewed PlaywrightFactory and BaseTest. A test receives its page from BaseTest; a page object receives the page; a data provider supplies data. These responsibilities should not be mixed.
8. Fail early with a useful source, row, field, and case identifier. Never include a password, token, cookie, or complete secret-bearing row in an exception or report.
9. Add unit tests for the readers and mappers before adding many UI tests. A bad data file should fail before a browser is launched.
10. Keep the framework boring at the edges: explicit Maven properties, deterministic resources, immutable values, and one cleanup owner.

## What was uploaded

### TestDataProviders.java

Current behaviour:

- Declares a utility class.
- Exposes one TestNG provider called excelLoginData.
- Loads testdata/loginData.xlsx and the LoginData sheet.
- Returns the result from ExcelDataProvider.

Good parts:

- The class cannot be instantiated.
- The resource path is appropriate for a file under src/test/resources.
- The sheet name is explicit.

Changes recommended:

- Add a JSON provider; currently the JSON reader is not wired to TestNG.
- Prefer names that describe the business data, for example loginJson and loginExcel.
- Keep parallel = false until the suite is proven safe.
- Move from Object[][] containing maps to Object[][] containing LoginData.
- Add a data-source error message that identifies the provider, but not the data values.
- Do not put browser or UI actions in this class.

### ExcelDataProvider.java

Current strengths:

- Loads the workbook from the classpath.
- Uses try-with-resources for the input stream and workbook.
- Uses DataFormatter, so cells are read as displayable strings.
- Evaluates formula cells.
- Detects missing resources and sheets.
- Rejects blank and duplicate headers.
- Skips completely blank rows.
- Preserves column order with LinkedHashMap.
- Returns unmodifiable row maps and uses the modern typed toArray(Object[][]::new) form.

Important limitations to address:

- It returns TestNG-shaped data directly. A reusable reader should normally return List<Map<String, String>>; a separate adapter should create Object[][].
- Header comparison is exact. Decide whether headers are case-sensitive. A common choice is to trim and compare case-insensitively, while preserving a canonical name.
- The first physical row is assumed to be the header. Document that rule or find the first nonblank row explicitly.
- The current formatter follows the workbook display formatting. Dates, numbers, and leading-zero identifiers need a defined convention.
- Hidden rows, formulas, and blank cells need a documented policy.
- Required-column and row-level validation belongs in a mapper/domain layer, not only in the generic reader.
- The reader must never log a complete row because a column might contain a secret.

### JsonDataProvider.java

Current strengths:

- Uses UTF-8.
- Loads classpath resources correctly for Maven test resources.
- Uses a project-file method only when a file is intentionally outside the classpath.
- Checks that a project path does not escape the project root.
- Reports invalid JSON and empty data with useful messages.

Important limitations to address:

- The generic type List<Map<String, String>> makes every value a string. It cannot naturally represent booleans, numbers, arrays, or nested objects.
- The parser should reject a null element, a non-object element, missing required keys, and unknown keys according to an explicit policy.
- Returned maps should be immutable if this transitional API remains.
- A caseId should be required and unique so a failed invocation can be identified.
- Classpath resources should be the normal path in Maven and Jenkins. The project-file method is an exception path.
- Do not put a literal password in JSON. Store a secret alias such as E2E_VALID_PASSWORD and resolve it from the environment at execution time.

### loginData.json

The current file contains login names and plaintext passwords and has no case identifier or expected outcome. Do not copy those values into a new example or commit them again.

A safer shape is shown later in this document. Change the credentials and remove the old values from source control history if necessary.

### loginData.xlsx

The LoginData sheet has a useful table shape, but it is not the same schema as the JSON file. It contains an expected-result column while the JSON file does not.

The workbook also has a Flags sheet with scattered values rather than a normal table. Decide whether that sheet is test data. If it is not, remove it from the automation workbook or document it clearly. If it is test data, convert it into a real header-and-row table. A generic reader should not have to guess what isolated cells mean.

## Recommended architecture

Keep each concern in one place:

~~~text
test data file
    -> reader
    -> schema validation and mapping
    -> immutable LoginData
    -> TestNG data-provider adapter
    -> test method
    -> page object
    -> Playwright page
~~~

Suggested project layout:

~~~text
src
├── main
│   └── java
│       └── framework
│           └── (shared production utilities only, if needed)
└── test
    ├── java
    │   ├── base
    │   │   └── BaseTest.java
    │   ├── config
    │   │   └── TestConfig.java
    │   ├── factory
    │   │   └── PlaywrightFactory.java
    │   ├── pages
    │   │   ├── LoginPage.java
    │   │   └── ...
    │   ├── components
    │   │   └── ...
    │   ├── data
    │   │   ├── model
    │   │   │   ├── LoginData.java
    │   │   │   └── LoginOutcome.java
    │   │   ├── reader
    │   │   │   ├── ExcelDataReader.java
    │   │   │   └── JsonDataReader.java
    │   │   ├── mapper
    │   │   │   └── LoginDataMapper.java
    │   │   └── support
    │   │       └── DataValidation.java
    │   ├── dataproviders
    │   │   └── TestDataProviders.java
    │   ├── listeners
    │   │   └── ...
    │   └── tests
    │       └── LoginTest.java
    └── resources
        └── testdata
            ├── loginData.json
            └── loginData.xlsx
~~~

The exact package names may differ, but the ownership should remain the same:

| Concern | Owner | Should not own |
|---|---|---|
| Browser, context, page creation and cleanup | PlaywrightFactory/BaseTest | Test data parsing |
| Locator and UI actions | Page object | Maven properties or Excel parsing |
| File parsing | JSON/Excel reader | Browser actions |
| Required fields and domain rules | Mapper/model validator | Screenshot or UI assertions |
| TestNG integration | TestDataProviders | Secret retrieval logic in every test |
| Scenario assertions | Test class or assertion helper | Browser lifecycle |

## One common data schema

Use the same logical columns in JSON and Excel:

| Field | Required | Example | Meaning |
|---|---:|---|---|
| caseId | yes | LOGIN-001 | Stable identifier used in reports and failures |
| username | yes | valid.user | Account name used by the test |
| passwordEnv | yes | E2E_VALID_PASSWORD | Environment variable name, not a password |
| expectedOutcome | yes | SUCCESS | A controlled enum value |
| enabled | yes | true | Allows a case to be disabled without deleting it |
| tags | no | smoke,login | Optional filtering metadata |

Prefer a secret alias (passwordEnv) over a literal password (password). The model can resolve the alias only at the moment the test needs the secret.

### Safe JSON example

This is a shape example only. The values are placeholders and should not be copied as real credentials:

~~~json
[
  {
    "caseId": "LOGIN-001",
    "username": "valid.user",
    "passwordEnv": "E2E_VALID_PASSWORD",
    "expectedOutcome": "SUCCESS",
    "enabled": true,
    "tags": ["smoke", "login"]
  },
  {
    "caseId": "LOGIN-002",
    "username": "locked.user",
    "passwordEnv": "E2E_INVALID_PASSWORD",
    "expectedOutcome": "INVALID_CREDENTIALS",
    "enabled": true,
    "tags": ["negative", "login"]
  }
]
~~~

### Safe Excel example

Use exactly these headers in row 1:

~~~text
caseId | username | passwordEnv | expectedOutcome | enabled | tags
LOGIN-001 | valid.user | E2E_VALID_PASSWORD | SUCCESS | true | smoke,login
LOGIN-002 | locked.user | E2E_INVALID_PASSWORD | INVALID_CREDENTIALS | true | negative,login
~~~

Keep the secret names in the workbook. Do not store the corresponding secret values in hidden columns, comments, formulas, or document properties.

## Typed domain model

A typed model is safer than having every test cast map values independently.

### Outcome enum

~~~java
package data.model;

public enum LoginOutcome {
    SUCCESS,
    INVALID_CREDENTIALS,
    VALIDATION_ERROR
}
~~~

An enum makes a typo such as INVALID_CREDENTAILS fail during data mapping instead of silently taking the wrong assertion path.

### Immutable Java 25 record

~~~java
package data.model;

import java.util.Objects;

public record LoginData(
        String caseId,
        String username,
        String passwordEnv,
        LoginOutcome expectedOutcome,
        boolean enabled
) {
    public LoginData {
        caseId = required(caseId, "caseId");
        username = required(username, "username");
        passwordEnv = required(passwordEnv, "passwordEnv");
        expectedOutcome = Objects.requireNonNull(
                expectedOutcome,
                "expectedOutcome must not be null"
        );
    }

    public String password() {
        String value = System.getenv(passwordEnv);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable '" + passwordEnv
                            + "' is missing or blank for case " + caseId
            );
        }

        return value;
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(
                value,
                field + " must not be null"
        ).strip();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }

        return normalized;
    }
}
~~~

Security rules for this record:

- Never override toString to include the password.
- Never log the return value of password.
- Never put password into a TestNG parameter, report title, screenshot name, or exception message.
- If the application uses a secret manager instead of environment variables, replace the lookup inside password with an injected secret resolver. Keep that change in one place.

If your username is sensitive in your organisation, protect it in reports as well. The password rule always applies.

## Mapping and validation

The reader answers “what text is in the file?” The mapper answers “is this a valid login case?”

A mapper should:

1. Check required fields.
2. Normalize harmless whitespace.
3. Parse expectedOutcome with Locale.ROOT.
4. Parse enabled explicitly instead of treating every non-true value as false.
5. Reject duplicate caseId values.
6. Reject unknown columns if strict schema control is desired.
7. Include source name and row number in errors.
8. Exclude secret values from all error text.

A transitional mapper from the current string maps can look like this:

~~~java
package data.mapper;

import data.model.LoginData;
import data.model.LoginOutcome;

import java.util.Locale;
import java.util.Map;

public final class LoginDataMapper {

    private LoginDataMapper() {
    }

    public static LoginData fromMap(
            Map<String, String> row,
            String source,
            int rowNumber
    ) {
        String caseId = required(row, "caseId", source, rowNumber);
        String username = required(row, "username", source, rowNumber);
        String passwordEnv = required(row, "passwordEnv", source, rowNumber);
        String outcomeText = required(
                row,
                "expectedOutcome",
                source,
                rowNumber
        );

        LoginOutcome outcome;
        try {
            outcome = LoginOutcome.valueOf(
                    outcomeText.strip().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw invalid(
                    source,
                    rowNumber,
                    "expectedOutcome must be SUCCESS, "
                            + "INVALID_CREDENTIALS, or VALIDATION_ERROR",
                    exception
            );
        }

        String enabledText = row.getOrDefault("enabled", "true").strip();
        boolean enabled;
        if ("true".equalsIgnoreCase(enabledText)) {
            enabled = true;
        } else if ("false".equalsIgnoreCase(enabledText)) {
            enabled = false;
        } else {
            throw invalid(
                    source,
                    rowNumber,
                    "enabled must be true or false",
                    null
            );
        }

        return new LoginData(
                caseId,
                username,
                passwordEnv,
                outcome,
                enabled
        );
    }

    private static String required(
            Map<String, String> row,
            String field,
            String source,
            int rowNumber
    ) {
        String value = row.get(field);

        if (value == null || value.isBlank()) {
            throw invalid(
                    source,
                    rowNumber,
                    "required field '" + field + "' is missing or blank",
                    null
            );
        }

        return value;
    }

    private static IllegalArgumentException invalid(
            String source,
            int rowNumber,
            String message,
            Throwable cause
    ) {
        String fullMessage = source
                + ", row " + rowNumber + ": " + message;

        return cause == null
                ? new IllegalArgumentException(fullMessage)
                : new IllegalArgumentException(fullMessage, cause);
    }
}
~~~

For a strict production implementation, make the JSON and Excel readers return a common intermediate type such as List<Map<String, String>>, then pass every row through this mapper. This guarantees that JSON and Excel use the same validation rules.

## TestNG data-provider design

### Transitional version using the current readers

This can be added now while the typed migration is prepared:

~~~java
package dataproviders;

import org.testng.annotations.DataProvider;
import dataproviders.ExcelDataProvider;
import dataproviders.JsonDataProvider;

import java.io.IOException;

public final class TestDataProviders {

    private TestDataProviders() {
    }

    @DataProvider(name = "excelLoginData", parallel = false)
    public static Object[][] excelLoginData() throws IOException {
        return ExcelDataProvider.getExcelDataFromClasspath(
                "testdata/loginData.xlsx",
                "LoginData"
        );
    }

    @DataProvider(name = "jsonLoginData", parallel = false)
    public static Object[][] jsonLoginData() throws IOException {
        return JsonDataProvider.getJsonDataFromClasspath(
                "testdata/loginData.json"
        );
    }
}
~~~

A test using this transitional provider receives a map:

~~~java
@Test(
        dataProvider = "jsonLoginData",
        dataProviderClass = TestDataProviders.class
)
public void loginUsingCurrentJsonReader(Map<String, String> data) {
    // Temporary bridge only.
    // Map key spelling and validation should move out of the test.
}
~~~

### Preferred version using typed objects

The long-term provider should look conceptually like this:

~~~java
@DataProvider(name = "loginJson", parallel = false)
public static Object[][] loginJson() throws IOException {
    List<LoginData> cases = LoginDataLoader.fromJsonClasspath(
            "testdata/loginData.json"
    );

    return enabledRows(cases);
}

@DataProvider(name = "loginExcel", parallel = false)
public static Object[][] loginExcel() throws IOException {
    List<LoginData> cases = LoginDataLoader.fromExcelClasspath(
            "testdata/loginData.xlsx",
            "LoginData"
    );

    return enabledRows(cases);
}

private static Object[][] enabledRows(List<LoginData> cases) {
    List<LoginData> enabledCases = cases.stream()
            .filter(LoginData::enabled)
            .toList();

    if (enabledCases.isEmpty()) {
        throw new IllegalStateException(
                "No enabled login test cases were loaded"
        );
    }

    return enabledCases.stream()
            .map(testCase -> new Object[]{testCase})
            .toArray(Object[][]::new);
}
~~~

The provider is allowed to throw a clear loading exception. TestNG should report the provider failure instead of launching tests with incomplete data.

Do not use parallel = true merely because it is available. Parallel data rows are safe only when every row and every external dependency can be used concurrently.

## Page Object Model example

The test should express the scenario, not how the file was parsed.

~~~java
package tests;

import data.model.LoginData;
import org.testng.annotations.Test;
import pages.LoginPage;

public final class LoginTest extends BaseTest {

    @Test(
            dataProvider = "loginJson",
            dataProviderClass = TestDataProviders.class
    )
    public void loginBehaviour(LoginData testCase) {
        LoginPage loginPage = new LoginPage(page());

        loginPage.open();
        loginPage.signIn(
                testCase.username(),
                testCase.password()
        );

        switch (testCase.expectedOutcome()) {
            case SUCCESS -> loginPage.assertLoggedIn();
            case INVALID_CREDENTIALS -> loginPage.assertInvalidCredentials();
            case VALIDATION_ERROR -> loginPage.assertValidationError();
        }
    }
}
~~~

Notes:

- BaseTest owns the page lifecycle; the test does not create a second browser.
- LoginPage owns locators and UI actions.
- LoginData owns the scenario values.
- The switch is exhaustive for the enum. Adding a new outcome forces the test to decide how to handle it.
- The test never knows whether the data came from JSON or Excel.
- If the test fails, use testCase.caseId() in a safe message or artifact name. Do not include testCase.password().

A page object may contain methods such as open, signIn, assertLoggedIn, and assertInvalidCredentials. It should not read Excel, access System.getProperty, or decide which browser to launch.

## JSON and Excel ownership

Do not maintain two independent copies of the same cases unless there is a specific business reason.

Recommended policy:

- JSON is canonical for automation-owned scenarios.
- Excel is an import or review format for business-maintained scenarios.
- If both are required, keep separate provider names and make the source visible in the test name or report metadata.
- Add a parity test only if both files are intended to represent the same cases. Compare normalized typed objects, never raw formatting.
- Never let one provider silently fall back to the other. A missing JSON file should fail clearly.

## Playwright lifecycle connection

Use the reviewed PlaywrightFactory and BaseTest already prepared for this project. The data-provider work should not create another session abstraction.

The intended ownership is:

- one Playwright object graph per TestNG test invocation;
- one browser selected by a Maven property;
- one isolated BrowserContext for the test;
- one primary Page exposed by BaseTest;
- any extra pages or contexts created by the test are closed by their owner;
- cleanup runs in @AfterMethod, including when the test fails.

A BrowserContext is the isolation boundary for cookies, local storage, permissions, and other browser state. Data objects should be immutable so a parallel invocation cannot modify another invocation's values.

Do not make Page, Browser, BrowserContext, or a mutable data map static. ThreadLocal browser state does not make shared accounts, files, database rows, or test users thread-safe.

## Parallel execution plan

Use this sequence:

1. Run all data providers and UI tests sequentially.
2. Add reader and mapper unit tests.
3. Verify that each invocation gets a fresh context and that cleanup is reliable.
4. Identify shared external resources: accounts, carts, uploaded filenames, database records, queues, and email inboxes.
5. Give each parallel case isolated data or a reservation mechanism.
6. Enable a small TestNG group in parallel.
7. Review traces, artifacts, and server-side collisions.
8. Expand concurrency only after repeated stable runs.

Examples of data that commonly break parallel runs:

- two tests change the same user password;
- two tests use the same shopping cart;
- two tests upload document.pdf;
- two tests wait for the same email;
- two tests edit the same database row;
- one test disables an account used by another.

A parallel = true data provider only controls TestNG scheduling. It does not isolate the application or external systems.

## Maven and Jenkins usage

Keep configuration in Maven properties or environment variables. Do not use Scanner or interactive prompts.

Safe local examples:

~~~text
mvn clean test -Dbrowser=chromium -Dheadless=false
mvn clean test -Dbrowser=firefox -Dheadless=true -Dplaywright.debug=api
mvn clean test -DtestDataSource=json
~~~

A Jenkins pipeline can pass non-secret choices as parameters:

~~~text
mvn clean test -Dbrowser=<BROWSER> -Dheadless=true -DtestDataSource=<TEST_DATA_SOURCE>
~~~

The exact Jenkins syntax depends on the pipeline type. Store E2E_VALID_PASSWORD and other secrets in Jenkins Credentials, bind them as environment variables, and let LoginData.password() resolve them. Do not pass passwords as -Dpassword=...; command lines and build logs can expose them.

Keep the following separate:

- browser selection and Playwright debug properties;
- environment/base URL properties;
- data source selection;
- credentials and other secrets.

## Reader improvements to make next

### Excel reader

Preserve the current resource and workbook handling. Add these behaviours behind tests:

- return a reusable list of rows in a reader API;
- validate that all required columns exist before reading data rows;
- trim headers and choose a documented case policy;
- reject duplicate caseId values;
- define whether hidden rows are included;
- document date and numeric formatting;
- preserve leading zeros by treating identifiers as text in the workbook;
- avoid formula cells for expected results that must be deterministic;
- make each returned map immutable;
- include source, sheet, and row number in safe errors.

### JSON reader

Preserve UTF-8 classpath loading. Add these behaviours:

- require a JSON array of objects;
- reject null rows;
- validate required fields and allowed values;
- reject duplicate caseId values;
- choose strict or lenient handling of unknown fields;
- return immutable rows;
- include array index and source in safe errors;
- use the project-file overload only for an intentional local data file;
- consider a typed JSON DTO when booleans, numbers, or nested data appear.

### TestDataProviders

The provider layer should:

- expose both JSON and Excel providers;
- declare parallel = false initially;
- map rows to domain records;
- filter enabled = false;
- fail when no enabled rows remain;
- not log secrets;
- use stable provider names;
- avoid caching mutable rows globally.

## Unit-test checklist for data code

Before writing dozens of browser tests, test these cases:

| Area | Required checks |
|---|---|
| Resource lookup | Missing JSON, missing Excel, wrong sheet |
| Empty input | Empty array, header-only sheet, blank rows |
| Schema | Missing field, blank field, duplicate header, unknown field policy |
| Values | Invalid enum, invalid boolean, whitespace, case normalization |
| Identity | Duplicate caseId |
| Excel formatting | Formula, date, number, leading zero, blank cell |
| Security | Error messages and reports do not contain password values |
| Secrets | Missing or blank environment variable produces a clear case-specific error |
| Filtering | Disabled rows are not scheduled; all-disabled input fails |
| Compatibility | JSON and Excel versions of the same case map to the same typed object |

These tests are fast, deterministic, and usually reveal more framework defects than an early large UI suite.

## Reporting and diagnostics

Use safe metadata for report names and artifacts:

- case identifier;
- browser;
- environment name;
- data source;
- test method;
- retry number, if retries are enabled.

Avoid:

- username and password together in titles;
- complete maps or serialized JSON rows;
- cookies, storage state, authorization headers, or tokens;
- secrets in screenshot names or trace descriptions.

The Playwright factory should own trace, video, screenshot, and console-log policy. The data layer should only provide safe identifiers and expected outcomes.

## What not to build yet

Avoid these designs while the framework is small:

- a global static Page;
- one browser context shared by the whole suite;
- a custom PlaywrightSession wrapper solely to hold the same objects;
- a data provider that logs into the application;
- a data provider that creates browsers;
- a generic Thread.sleep wait utility;
- reflection-heavy magic mapping before the schema is stable;
- parallel execution before external test data is isolated;
- a second factory with different browser property names;
- passwords in source, Excel, JSON, Maven arguments, screenshots, or reports.

## Practical migration plan

### Phase 1: safety and cleanup

- Rotate the credentials currently present in the uploaded files.
- Remove the literal passwords from JSON and Excel.
- Add passwordEnv and placeholder secret names.
- Remove or clarify the irregular Flags sheet.
- Confirm the files are under src/test/resources/testdata.

### Phase 2: common schema

- Add caseId, expectedOutcome, and enabled to both sources.
- Decide the unknown-column policy.
- Decide whether JSON is canonical.
- Document the schema beside the files.

### Phase 3: typed data

- Add LoginOutcome.
- Add immutable LoginData.
- Add a mapper with required-field and enum/boolean validation.
- Add duplicate-case validation.
- Keep the existing generic readers as transition adapters.

### Phase 4: TestNG integration

- Wire jsonLoginData and excelLoginData.
- Return LoginData, not maps, from the preferred providers.
- Keep providers sequential.
- Add provider unit tests.

### Phase 5: UI tests

- Make tests consume LoginData.
- Keep locators and actions in page objects.
- Use the reviewed BaseTest and PlaywrightFactory.
- Add safe case IDs to reporting.

### Phase 6: CI and parallelism

- Add Jenkins parameters for browser, headless mode, debug mode, environment, and data source.
- Bind secrets through Jenkins Credentials.
- Run sequential smoke tests first.
- Introduce parallel groups only after data isolation is proven.
- Publish Playwright artifacts and TestNG/Surefire reports.

## Definition of done

The foundation is in good shape when all of these are true:

- No credentials are stored in the repository or command line.
- JSON and Excel cases use the same documented schema.
- Every enabled case has a unique caseId.
- Every row maps to an immutable typed object.
- Invalid data fails before browser creation.
- Test methods do not parse maps or files.
- Page objects do not know about data sources.
- One factory/BaseTest pair owns Playwright lifecycle.
- Sequential execution is stable.
- Parallel execution has isolated accounts and external resources.
- Jenkins can select browser and debug mode without code changes.
- Reports identify cases without exposing secrets.
- Reader, mapper, and secret-resolution behaviour have unit coverage.

## Final recommendation

Keep your current Excel and JSON readers as a starting point because they already handle resource lookup and basic file errors well. Do not grow the framework by adding more map casts to test methods. Make JSON the canonical automation format, clean both files into one schema, introduce LoginData, and put all validation in a mapper/domain layer. Then expose thin TestNG providers and let the reviewed PlaywrightFactory/BaseTest handle browser lifecycle.

This path gives a beginner-friendly framework now and leaves room for Jenkins, trace collection, multiple browsers, and safe parallel execution later without rewriting every test.

