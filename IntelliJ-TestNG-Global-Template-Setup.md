# IntelliJ TestNG Global Template Setup

## Playwright Java, TestNG, Maven, and FrameworkConfig Guide

This guide explains how to configure global TestNG defaults in IntelliJ IDEA
for a Playwright Java automation framework.

It covers:

- IntelliJ TestNG run-configuration templates
- Java VM options
- `-Dapp.env=test`
- Maven `-Ptest`
- Playwright browser selection
- headed and headless execution
- slow motion
- Playwright debug logging
- Java breakpoint debugging
- configuration priority
- local execution versus Maven and Jenkins
- common mistakes and troubleshooting

The examples assume that the framework uses:

- Java 25
- Maven
- TestNG
- Playwright for Java
- `FrameworkConfig`
- `PlaywrightFactory`
- `BaseTest`
- environment files such as
  `Environment/test/configuration.properties`

---

## 1. The problem the global template solves

Suppose you run this test by clicking the green IntelliJ button:

```java
package tests;

import org.testng.annotations.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public final class ExampleTest extends BaseTest {

    @Test
    public void playwrightWebsiteShouldOpen() {
        page().navigate(urls().mirUi());

        assertThat(page()).hasTitle(
                Pattern.compile("Playwright")
        );
    }
}
```

You may want every local test to use:

- the `test` environment;
- Chromium;
- a visible browser;
- slow motion;
- optional Playwright API logs.

Without a template, you might have to add the same VM options to every new
TestNG run configuration:

```text
-Dapp.env=test -Dbrowser=chromium -Dheadless=false -DslowMo=2000
```

The IntelliJ TestNG template provides the default settings for new TestNG run
configurations. You configure the values once, and IntelliJ copies them into
new configurations created from the template.

---

## 2. What “global template” means

The TestNG template is a default configuration inside IntelliJ IDEA.

It affects new TestNG configurations created by actions such as:

- clicking the green button beside an `@Test` method;
- clicking the green button beside a TestNG class;
- creating a new TestNG configuration manually.

It does not mean that the configuration is globally applied to:

- Maven Surefire;
- Jenkins;
- other team members' IntelliJ installations;
- TestNG configurations that already existed before the template changed;
- every Java application in IntelliJ.

Think of it as a reusable starting template for IntelliJ TestNG runs.

---

## 3. Configure the TestNG template in IntelliJ

### Step 1: Open the run-configuration window

Open:

```text
Run → Edit Configurations
```

### Step 2: Open configuration templates

Depending on the IntelliJ version, use one of these locations:

- Select **Edit configuration templates…**.
- Expand the **Templates** section in the left panel.
- Use the configuration menu and select **Edit configuration templates**.

### Step 3: Select TestNG

Select the **TestNG** template. Make sure you are editing the template, not one
existing test configuration such as
`ExampleTest.playwrightWebsiteShouldOpen`.

### Step 4: Find VM options

Open the **JDK Settings** section and locate **VM options**.

If the field is not visible, use:

```text
Modify options → Add VM options
```

### Step 5: Add the recommended local settings

Enter the options on one line:

```text
-ea -Dapp.env=test -Dbrowser=chromium -Dheadless=false -DslowMo=1000
```

### Step 6: Save the template

Select:

```text
Apply → OK
```

### Step 7: Create a new TestNG configuration

Click the green button beside a test method and run or debug it. IntelliJ
should create the configuration using the template values.

---

## 4. Important limitation: existing configurations do not automatically change

Changing the TestNG template generally affects configurations created after
the change. Existing TestNG configurations can retain their previous values.

If an existing test does not receive the new options, use one of these methods.

### Option A: Update the existing configuration

1. Open **Run → Edit Configurations**.
2. Select the existing configuration.
3. Add the values to **VM options**.
4. Select **Apply** and **OK**.

### Option B: Delete and recreate the configuration

1. Open **Run → Edit Configurations**.
2. Delete the old TestNG configuration.
3. Click the green button beside the test again.
4. IntelliJ creates a new configuration from the updated template.

Use Option A when the configuration contains other customization that you want
to preserve.

---

## 5. Recommended VM options for local Playwright testing

### Visible Chromium with moderate slow motion

```text
-ea -Dapp.env=test -Dbrowser=chromium -Dheadless=false -DslowMo=1000
```

This is a useful learning and debugging template because the browser is visible
and actions are slowed down.

### Fast headless execution

```text
-ea -Dapp.env=test -Dbrowser=chromium -Dheadless=true -DslowMo=0
```

This is better when you want fast local execution and do not need to watch the
browser.

### Visible Chromium with Playwright API logs

```text
-ea -Dapp.env=test -Dbrowser=chromium -Dheadless=false -DslowMo=1000 -Dplaywright.debug=api
```

### Visible Firefox

```text
-ea -Dapp.env=test -Dbrowser=firefox -Dheadless=false -DslowMo=1000
```

### Visible WebKit

```text
-ea -Dapp.env=test -Dbrowser=webkit -Dheadless=false -DslowMo=1000
```

---

## 6. Meaning of each VM option

Consider this configuration:

```text
-ea -Dapp.env=test -Dbrowser=chromium -Dheadless=false -DslowMo=1000 -Dplaywright.debug=api
```

| Option | Purpose |
|---|---|
| `-ea` | Enables Java assertions for the JVM |
| `-Dapp.env=test` | Selects the framework environment named `test` |
| `-Dbrowser=chromium` | Selects Playwright Chromium |
| `-Dheadless=false` | Opens a visible browser window |
| `-DslowMo=1000` | Adds a one-second delay to Playwright operations |
| `-Dplaywright.debug=api` | Enables Playwright API debug output through the factory |

### Why `-ea` is already visible in IntelliJ

IntelliJ commonly includes:

```text
-ea
```

This means **enable assertions**. It activates Java statements such as:

```java
assert value != null;
```

TestNG assertions and Playwright assertions do not normally depend on `-ea`,
but keeping it is reasonable for test execution.

Add your system properties after it:

```text
-ea -Dapp.env=test -Dheadless=false -DslowMo=1000
```

---

## 7. Why IntelliJ TestNG uses `-Dapp.env=test`

When you click IntelliJ's green TestNG button, IntelliJ launches TestNG
directly. Maven does not run.

The execution path is:

```text
IntelliJ → TestNG → BaseTest → PlaywrightFactory → Test method
```

Because Maven does not run:

- Maven profiles are not activated;
- `-Ptest` is not processed;
- Maven Surefire is not responsible for loading the environment file;
- POM profile properties are not automatically passed to the test.

The Java property supplies the environment directly:

```text
-Dapp.env=test
```

The framework can then load:

```text
Environment/test/configuration.properties
```

The conceptual flow is:

```text
-Dapp.env=test
        ↓
FrameworkConfig.environment()
        ↓
Environment/test/configuration.properties
        ↓
PlaywrightFactory settings
```

---

## 8. Why Maven uses `-Ptest`

When Maven starts the test, the recommended command is:

```bash
mvn -Ptest test
```

Here, `-Ptest` tells Maven to activate the profile named `test`.

A typical profile defines:

```xml
<profile>
    <id>test</id>

    <activation>
        <activeByDefault>true</activeByDefault>
    </activation>

    <properties>
        <build.profile.id>test</build.profile.id>
    </properties>
</profile>
```

Surefire can then use the selected profile value:

```xml
<systemPropertyVariables>
    <app.env>${build.profile.id}</app.env>
    <ENVIRONMENT>${build.profile.id}</ENVIRONMENT>
</systemPropertyVariables>

<systemPropertiesFile>
    ${project.basedir}/Environment/${build.profile.id}/configuration.properties
</systemPropertiesFile>
```

The Maven flow is:

```text
-Ptest
   ↓
Maven activates the test profile
   ↓
build.profile.id = test
   ↓
Surefire loads Environment/test/configuration.properties
   ↓
Surefire passes app.env=test to the test JVM
```

---

## 9. Difference between `-P` and `-D`

| Syntax | Meaning | Process that understands it |
|---|---|---|
| `-Ptest` | Activate the Maven profile named `test` | Maven |
| `-Dapp.env=test` | Define the Java/Maven property `app.env` | JVM, Maven, and framework code |
| `-Dheadless=false` | Define the framework property `headless` | `FrameworkConfig` and `PlaywrightFactory` |
| `-DslowMo=1000` | Define the framework property `slowMo` | `FrameworkConfig` and `PlaywrightFactory` |

Remember:

- `-P` means Maven **profile**.
- `-D` means **define a property**.

### Recommended environment selection

| Execution method | Recommended environment selection |
|---|---|
| IntelliJ green TestNG button | `-Dapp.env=test` in VM options |
| IntelliJ TestNG Debug | `-Dapp.env=test` in VM options |
| Local Maven | `mvn -Ptest test` |
| Jenkins running Maven | `mvn -Ptest test` |

---

## 10. Why `-Dheadless` works in both execution methods

The following settings are runtime overrides:

```text
-Dbrowser=firefox
-Dheadless=false
-DslowMo=1000
-Dplaywright.debug=api
```

They are not Maven environment profiles. Your Java configuration code reads
them as system properties, for example:

```java
System.getProperty("headless");
```

Therefore, they can be supplied through either:

- IntelliJ TestNG VM options; or
- Maven `-D` command-line arguments that Surefire passes to the test JVM.

Direct IntelliJ example:

```text
-Dapp.env=test -Dheadless=false -DslowMo=1000
```

Maven example:

```bash
mvn -Ptest -Dheadless=false -DslowMo=1000 test
```

---

## 11. Framework configuration priority

Your `FrameworkConfig` should use a clear priority such as:

1. Java system property
2. Operating-system environment variable
3. Selected environment's `configuration.properties`
4. Default supplied by Java code

Example:

```text
Java system property
        ↓
Environment variable
        ↓
Environment/test/configuration.properties
        ↓
Java default value
```

Suppose the file contains:

```properties
browser=chromium
headless=true
slowMo=0
```

The IntelliJ VM options contain:

```text
-Dheadless=false -DslowMo=1000
```

The effective settings should be:

| Setting | Effective value | Source |
|---|---|---|
| `browser` | `chromium` | Property file |
| `headless` | `false` | IntelliJ VM option |
| `slowMo` | `1000` | IntelliJ VM option |

The VM options override the property-file values without requiring changes to
the file.

---

## 12. Is `-Dapp.env=test` mandatory?

If `FrameworkConfig` contains a default such as:

```java
private static final String DEFAULT_ENVIRONMENT = "test";
```

then IntelliJ may select `test` even when `-Dapp.env=test` is absent.

For example, this may work:

```text
-ea -Dheadless=false -DslowMo=1000
```

However, explicitly including the environment is safer:

```text
-ea -Dapp.env=test -Dheadless=false -DslowMo=1000
```

Benefits of being explicit:

- The intended environment is visible in the run configuration.
- A future change to the default will not silently redirect the test.
- Testers can immediately see whether they selected `test`, `dev`, or `uat`.
- The console environment message is easier to verify.

Do not use `prod` as the global local-testing template.

---

## 13. Headless and headed browser behavior

### Headless mode

```text
-Dheadless=true
```

The browser runs without a visible window. This is normally faster and is the
usual choice for Jenkins.

### Headed mode

```text
-Dheadless=false
```

The real browser window appears on the screen. This is useful for:

- learning Playwright;
- observing test behavior;
- investigating locators;
- understanding page navigation;
- checking popups, new tabs, and dialogs.

### Recommended values

| Location | Recommended default |
|---|---|
| Local IntelliJ learning | `headless=false` |
| Local fast regression | `headless=true` |
| Jenkins | `headless=true` |
| Troubleshooting a UI failure | `headless=false` |

---

## 14. Understanding slow motion

Example:

```text
-DslowMo=2000
```

This asks Playwright to delay operations by approximately 2,000 milliseconds.
It can help you watch what the automation is doing.

Recommended values:

| Value | Usage |
|---:|---|
| `0` | Normal, fast execution |
| `300` | Small visible delay |
| `500` | Comfortable troubleshooting speed |
| `1000` | Beginner-friendly observation |
| `2000` | Very slow, useful for detailed learning |

Slow motion is not a synchronization solution. Continue using Playwright
locators, assertions, and automatic waiting. Do not add slow motion to make an
unstable test pass.

Jenkins should normally use:

```text
-DslowMo=0
```

---

## 15. Playwright logging versus Java debugging

These are separate concepts.

| Feature | Purpose |
|---|---|
| `-Dplaywright.debug=api` | Print Playwright API activity |
| `-Dplaywright.debug=browser` | Print browser or driver activity |
| `-Dplaywright.debug=all` | Print the configured combined debug output |
| IntelliJ **Debug** | Stop on Java breakpoints and inspect variables |

This option prints Playwright activity:

```text
-Dplaywright.debug=api
```

It does not cause Java to stop at a breakpoint.

When running TestNG directly through IntelliJ, click:

```text
Debug 'ExampleTest.playwrightWebsiteShouldOpen()'
```

IntelliJ starts the TestNG JVM and attaches its Java debugger automatically.

You may combine both:

```text
-ea -Dapp.env=test -Dheadless=false -DslowMo=1000 -Dplaywright.debug=api
```

Then click **Debug**, not only **Run**.

---

## 16. Create several reusable IntelliJ configurations

One global template cannot represent every testing situation. A practical
approach is:

1. Use the template for the most common local behavior.
2. Create named configurations for special cases.

Recommended configurations:

| Configuration name | VM options |
|---|---|
| `Local - Fast Chromium` | `-ea -Dapp.env=test -Dbrowser=chromium -Dheadless=true -DslowMo=0` |
| `Local - Visible Chromium` | `-ea -Dapp.env=test -Dbrowser=chromium -Dheadless=false -DslowMo=500` |
| `Local - Playwright API Debug` | `-ea -Dapp.env=test -Dbrowser=chromium -Dheadless=false -DslowMo=1000 -Dplaywright.debug=api` |
| `Local - Visible Firefox` | `-ea -Dapp.env=test -Dbrowser=firefox -Dheadless=false -DslowMo=500` |
| `Local - Visible WebKit` | `-ea -Dapp.env=test -Dbrowser=webkit -Dheadless=false -DslowMo=500` |

To create one:

1. Open **Run → Edit Configurations**.
2. Duplicate an existing configuration.
3. Change its name.
4. Change the VM options.
5. Save it.

Select the desired configuration from the IntelliJ toolbar before running.

---

## 17. “Store as project file” option

Your IntelliJ screenshot shows **Store as project file**.

When selected, IntelliJ can store that run configuration inside the project,
commonly under a `.run` directory. The configuration can then be committed to
Git and shared with the team.

Use it when:

- the configuration is useful to the whole team;
- paths are project-relative;
- it does not contain credentials;
- it uses safe environment defaults;
- the team agrees to share IntelliJ run configurations.

Do not store sensitive values such as:

```text
-Ddatabase.password=secret
-Dapi.token=secret
-Duser.password=secret
```

For shared configurations, prefer safe values such as:

```text
-Dapp.env=test -Dbrowser=chromium -Dheadless=true -DslowMo=0
```

Keep personal learning settings such as very high slow motion in your local
configuration unless the team wants them.

---

## 18. Do not put runtime settings inside the test method

Avoid this pattern:

```java
@Test
public void playwrightWebsiteShouldOpen() {
    System.setProperty("headless", "false");
    System.setProperty("slowMo", "2000");

    page().navigate(urls().mirUi());
}
```

It is too late if `BaseTest` has already called `PlaywrightFactory.start()`.
It also mixes framework configuration with test behavior and can cause problems
during parallel execution.

Keep the test focused on the scenario:

```java
@Test
public void playwrightWebsiteShouldOpen() {
    page().navigate(urls().mirUi());

    assertThat(page()).hasTitle(
            Pattern.compile("Playwright")
    );
}
```

Supply runtime choices through:

- the IntelliJ TestNG template;
- a named IntelliJ run configuration;
- Maven command-line properties;
- Jenkins parameters;
- environment property files.

---

## 19. Direct IntelliJ, Maven, and Jenkins comparison

| Behavior | IntelliJ TestNG | Maven Surefire | Jenkins with Maven |
|---|---|---|---|
| Started by | IntelliJ | Maven | Jenkins job or pipeline |
| Activates Maven profile | No | Yes | Yes |
| Environment selector | `-Dapp.env=test` | `-Ptest` | `-Ptest` or selected profile |
| Runtime override | VM options | Maven `-D` options | Jenkins/Maven `-D` options |
| Fast Java debugging | Yes | Requires correct Surefire debugging approach | Usually reproduced locally |
| Uses POM Surefire configuration | No | Yes | Yes |

### IntelliJ TestNG example

```text
-ea -Dapp.env=test -Dbrowser=chromium -Dheadless=false -DslowMo=1000
```

### Equivalent Maven example

```bash
mvn -Ptest \
    -Dtest=ExampleTest \
    -Dbrowser=chromium \
    -Dheadless=false \
    -DslowMo=1000 \
    test
```

### Typical Jenkins example

```bash
mvn -Ptest \
    -Dbrowser=chromium \
    -Dheadless=true \
    -DslowMo=0 \
    clean test
```

---

## 20. Environment display in BaseTest

Printing the environment once before the suite helps prevent mistakes:

```java
/**
 * Displays the environment selected for the current TestNG suite.
 *
 * <p>The method runs once before the suite and helps testers verify that the
 * expected environment was selected before browser tests begin.</p>
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
```

Expected output:

```text
========================================
 Test environment: test
========================================
```

Always check this message before allowing a test that changes application data
to continue.

---

## 21. Common mistakes

### Putting values in Test runner parameters

Incorrect location:

```text
Test runner params
```

Correct location:

```text
VM options
```

Your framework reads Java system properties, so use:

```text
-Dheadless=false
```

### Trying to use a Maven profile in TestNG VM options

This does not select a Maven profile during a direct IntelliJ TestNG run:

```text
-Ptest
```

Use:

```text
-Dapp.env=test
```

### Expecting the browser to appear with headless mode

This hides the browser:

```text
-Dheadless=true
```

Use this to see it:

```text
-Dheadless=false
```

### Expecting slow motion to stabilize a test

Slow motion is for observation. It should not replace Playwright's locator,
assertion, and automatic-waiting behavior.

### Expecting the template to update old configurations

Edit or recreate configurations that existed before the template was changed.

### Putting secrets in VM options

VM options can appear in process details, screenshots, logs, and shared project
files. Supply credentials through the organization's approved Jenkins
credential or secret-management system.

---

## 22. Troubleshooting

### The test still uses headless mode

Check that:

- `-Dheadless=false` is in **VM options**;
- the option is spelled exactly as expected by `PlaywrightFactory`;
- the existing configuration was recreated or manually updated;
- the property file is not being given higher priority than system properties;
- `PlaywrightFactory.start()` reads configuration after the JVM starts.

### Slow motion does not work

Check that:

- the key is exactly `slowMo` if that is what the factory expects;
- the value is numeric, such as `1000`;
- `PlaywrightFactory` passes it to
  `BrowserType.LaunchOptions.setSlowMo(...)`;
- Playwright resources were not created before the property was read.

### The wrong environment loads

Check the VM options:

```text
-Dapp.env=test
```

Then confirm that the console prints:

```text
Test environment: test
```

Also verify that this file exists:

```text
Environment/test/configuration.properties
```

### Playwright API logs do not appear

Check that:

```text
-Dplaywright.debug=api
```

matches the property name read by `PlaywrightFactory`. The factory must set the
Playwright driver's `DEBUG` environment value before calling
`Playwright.create(...)`.

### New tests use the template, but an old test does not

The old run configuration already existed. Update it manually or delete and
recreate it.

### The green button runs the test, but Maven behavior is different

This is expected because direct IntelliJ TestNG execution bypasses Maven
Surefire. Reproduce the Maven path with:

```bash
mvn -Ptest -Dtest=ExampleTest test
```

---

## 23. Recommended setup for your framework

### Global local TestNG template

Use this while learning and visually debugging tests:

```text
-ea -Dapp.env=test -Dbrowser=chromium -Dheadless=false -DslowMo=500
```

Add API logs only when needed:

```text
-Dplaywright.debug=api
```

Keeping API logs off by default prevents excessive console output.

### Property-file defaults

Use safe, automation-friendly defaults:

```properties
browser=chromium
headless=true
slowMo=0
playwright.debug=off
```

### Jenkins defaults

Use:

```text
headless=true
slowMo=0
playwright.debug=off
```

Enable additional diagnostics for a troubleshooting build when necessary.

### Production safety

Do not make `prod` the global TestNG template. Use an explicit, controlled
configuration for any approved production smoke testing. Production tests
should normally be read-only unless the organization explicitly authorizes
data-changing tests.

---

## 24. Quick setup checklist

- [ ] Opened **Run → Edit Configurations**.
- [ ] Opened the TestNG configuration template.
- [ ] Added values to **VM options**.
- [ ] Kept `-ea` if Java assertions should remain enabled.
- [ ] Added `-Dapp.env=test` explicitly.
- [ ] Selected a supported browser.
- [ ] Used `-Dheadless=false` to display the browser.
- [ ] Used a reasonable local `slowMo` value.
- [ ] Applied and saved the template.
- [ ] Recreated or updated existing configurations.
- [ ] Confirmed the environment printed in the console.
- [ ] Avoided credentials and secrets in VM options.

---

## 25. Final recommendation

Use the IntelliJ TestNG global template for your normal local defaults:

```text
-ea -Dapp.env=test -Dbrowser=chromium -Dheadless=false -DslowMo=500
```

Use this direct IntelliJ pattern when you click a green TestNG button:

```text
-Dapp.env=test
```

Use this Maven pattern when running through Maven or Jenkins:

```bash
mvn -Ptest test
```

Keep the test method independent of environment and browser configuration. The
test should describe application behavior, while `FrameworkConfig`,
`PlaywrightFactory`, Maven, Jenkins, and IntelliJ determine how and where the
test runs.

---

## Official references

- [JetBrains IntelliJ IDEA Documentation](https://www.jetbrains.com/help/idea/)
- [JetBrains: Run/Debug Configurations](https://www.jetbrains.com/help/idea/run-debug-configuration.html)
- [JetBrains: TestNG](https://www.jetbrains.com/help/idea/testng.html)
- [TestNG Documentation](https://testng.org/)
- [Apache Maven: Introduction to Build Profiles](https://maven.apache.org/guides/introduction/introduction-to-profiles.html)
- [Playwright Java: Running and Debugging Tests](https://playwright.dev/java/docs/debug)

