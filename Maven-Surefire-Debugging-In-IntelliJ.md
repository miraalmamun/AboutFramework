# Debugging Maven, TestNG, and Playwright Java Tests in IntelliJ IDEA

## Beginner-Friendly Guide for Your Framework

This guide explains how to debug a Playwright Java test that is executed by
Maven Surefire from IntelliJ IDEA. It is written for a framework that uses:

- Java 25
- Maven
- TestNG
- Playwright for Java
- Maven environment profiles such as `test` and `dev`
- A `PlaywrightFactory`
- A `BaseTest`
- Runtime configuration through Maven `-D` properties

The examples use this test:

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

---

## 1. The important difference: Java debugging and Playwright debugging

These are two different types of debugging.

| Debugging type | Purpose | Example |
|---|---|---|
| Java debugger | Stops on breakpoints and lets you inspect Java variables | `-Dmaven.surefire.debug` |
| Playwright API logging | Prints Playwright calls such as navigation, click, and fill | `-Dplaywright.debug=api` |
| Playwright browser logging | Prints lower-level browser and driver information | `-Dplaywright.debug=browser` |
| Visible browser | Opens a browser window so you can watch the test | `-Dheadless=false` |
| Slow motion | Delays Playwright actions so they are easier to observe | `-DslowMo=2000` |

`-Dplaywright.debug=api` does **not** make IntelliJ stop at a Java breakpoint.
For a breakpoint, IntelliJ must attach its Java debugger to the JVM executing
the test.

---

## 2. Why your IntelliJ breakpoint was not reached

When you click **Debug** on a Maven run configuration, IntelliJ can attach to
the Maven JVM. Maven Surefire normally starts a separate, forked JVM to execute
the TestNG tests.

| Process | Responsibility |
|---|---|
| Maven JVM | Runs Maven and the build lifecycle |
| Surefire test JVM | Runs `ExampleTest` and your Playwright code |

Your breakpoint is inside `ExampleTest`, so the debugger must be attached to
the **Surefire test JVM**.

This explains the behavior visible in your screenshot:

- Maven reported `BUILD SUCCESS`.
- The test executed normally.
- The breakpoint on `page().navigate(...)` was not reached.
- IntelliJ eventually printed `Disconnected from the target VM`.

That message is not an error by itself. It only says that the JVM to which
IntelliJ was attached has finished. The important problem was that the test ran
in a different JVM.

---

## 3. Recommended approach: attach IntelliJ to Maven Surefire

This is the best option when you want the local run to behave like Maven or a
Jenkins build. It preserves your Maven profile, Surefire configuration, system
properties, and normal test execution model.

### Step 1: Add a breakpoint

Click the gutter beside the line where execution should stop:

```java
page().navigate(urls().mirUi());
```

A solid red circle should appear.

### Step 2: Start Maven in debug-wait mode

Open the IntelliJ **Terminal** and run:

```bash
mvn -Ptest \
    -Dtest=ExampleTest \
    -Dheadless=false \
    -DslowMo=2000 \
    -Dmaven.surefire.debug \
    test
```

On Windows Command Prompt, it is easiest to use one line:

```bat
mvn -Ptest -Dtest=ExampleTest -Dheadless=false -DslowMo=2000 -Dmaven.surefire.debug test
```

The command should pause and wait for a debugger. By default, Surefire listens
on port `5005`.

The terminal may appear to be stuck. That is expected: the test JVM is waiting
for IntelliJ to connect.

### Step 3: Create an IntelliJ Remote JVM Debug configuration

In IntelliJ IDEA:

1. Open **Run > Edit Configurations**.
2. Select **+**.
3. Select **Remote JVM Debug**.
4. Use the following values:

| Setting | Value |
|---|---|
| Name | `Attach Surefire 5005` |
| Debugger mode | Attach to remote JVM |
| Host | `localhost` |
| Port | `5005` |
| Use module classpath | Your project module, such as `AboutFramework` |

5. Save the configuration.

You normally create this configuration only once and reuse it.

### Step 4: Attach the debugger

1. Leave the Maven command waiting in the terminal.
2. Select `Attach Surefire 5005` from the IntelliJ run-configuration list.
3. Click the **Debug** icon.
4. IntelliJ connects to the Surefire test JVM.
5. TestNG continues executing.
6. IntelliJ stops on your breakpoint.

The required order is:

1. Start Maven with `-Dmaven.surefire.debug`.
2. Wait until the test JVM is listening.
3. Attach the IntelliJ Remote JVM Debug configuration.

---

## 4. Recommended command for your Playwright framework

The following command combines Java breakpoint debugging with Playwright API
logging and a visible, slowed browser:

```bash
mvn -Ptest \
    -Dtest=ExampleTest \
    -Dheadless=false \
    -DslowMo=1000 \
    -Dplaywright.debug=api \
    -Dmaven.surefire.debug \
    test
```

Windows one-line version:

```bat
mvn -Ptest -Dtest=ExampleTest -Dheadless=false -DslowMo=1000 -Dplaywright.debug=api -Dmaven.surefire.debug test
```

This gives you all of the following:

- Maven profile `test`
- Only the `ExampleTest` class
- A visible browser
- One-second delay between Playwright actions
- Playwright API call details in the console
- Java breakpoint support through IntelliJ

### Debug only one test method

Maven Surefire supports the `ClassName#methodName` format:

```bash
mvn -Ptest \
    '-Dtest=ExampleTest#playwrightWebsiteShouldOpen' \
    -Dheadless=false \
    -Dmaven.surefire.debug \
    test
```

In PowerShell, keep the `-Dtest` expression in quotes because `#` has a
special meaning:

```powershell
mvn -Ptest "-Dtest=ExampleTest#playwrightWebsiteShouldOpen" -Dheadless=false -Dmaven.surefire.debug test
```

---

## 5. Using a different debugger port

Port `5005` may already be used by another application or another debugging
session. You can configure Surefire to use a different port, such as `8000`.

```bash
mvn -Ptest \
    -Dtest=ExampleTest \
    -Dheadless=false \
    -Dmaven.surefire.debug="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:8000" \
    test
```

Then create or update the IntelliJ Remote JVM Debug configuration:

| Setting | Value |
|---|---|
| Host | `localhost` |
| Port | `8000` |

The port in the Maven command and the port in IntelliJ must be identical.

---

## 6. Faster alternative: debug TestNG directly in IntelliJ

For everyday local development, you can debug the TestNG method directly:

1. Add a breakpoint.
2. Click the green run icon beside the test method.
3. Select **Debug 'playwrightWebsiteShouldOpen()'**.

In this mode, IntelliJ launches the TestNG test JVM itself, so it automatically
attaches the debugger to the correct process.

If the test needs runtime settings, open the TestNG run configuration and add
these **VM options**:

```text
-Dapp.env=test -Dheadless=false -DslowMo=2000 -Dplaywright.debug=api
```

Your `FrameworkConfig` fallback can load this file during direct IntelliJ
execution:

```text
Environment/test/configuration.properties
```

### Direct TestNG versus Maven Surefire

| Requirement | Recommended method |
|---|---|
| Quickly investigate one test locally | Direct IntelliJ TestNG Debug |
| Reproduce Maven behavior | Maven Surefire remote debugging |
| Reproduce Maven profile selection | Maven Surefire remote debugging |
| Investigate a Jenkins-only Maven issue locally | Maven Surefire remote debugging |
| Inspect Java variables and step through code | Either method |
| See Playwright API activity | Add `-Dplaywright.debug=api` to either method |

Use direct TestNG debugging for speed. Use Maven Surefire remote debugging when
the Maven execution path itself is important.

---

## 7. Meaning of every command option

Consider this command:

```bash
mvn -Ptest -Dtest=ExampleTest -Dheadless=false -DslowMo=2000 -Dplaywright.debug=api -Dmaven.surefire.debug test
```

| Option | Meaning |
|---|---|
| `mvn` | Starts Maven |
| `-Ptest` | Activates the Maven profile named `test` |
| `-Dtest=ExampleTest` | Runs only the `ExampleTest` class |
| `-Dheadless=false` | Opens a visible browser window |
| `-DslowMo=2000` | Adds a 2,000-millisecond delay to Playwright operations |
| `-Dplaywright.debug=api` | Enables Playwright API debug output through your factory |
| `-Dmaven.surefire.debug` | Suspends the Surefire JVM and waits for a Java debugger |
| `test` | Executes Maven's test lifecycle phase |

### Why `test` is usually better than `clean test` while debugging

Use this for most debugging iterations:

```bash
mvn -Ptest -Dtest=ExampleTest -Dmaven.surefire.debug test
```

`clean` deletes the previous build output and forces Maven to rebuild more
content. That can make every debugging attempt slower.

Use `clean test` when:

- compiled files appear stale;
- dependencies or generated files changed;
- you specifically need to verify a clean build;
- local behavior differs from Jenkins because Jenkins starts clean.

---

## 8. Playwright debug combinations supported by your factory

Your `PlaywrightFactory` translates the `playwright.debug` setting into the
`DEBUG` environment value used when `Playwright.create(options)` starts the
Playwright driver.

### API activity only

```bash
mvn -Ptest -Dtest=ExampleTest -Dplaywright.debug=api test
```

Useful for seeing operations such as:

- `page.navigate(...)`
- locator creation
- `click()`
- `fill()`
- Playwright auto-waiting
- assertions

### Browser or driver activity only

```bash
mvn -Ptest -Dtest=ExampleTest -Dplaywright.debug=browser test
```

Use this when investigating browser startup, driver communication, or a
browser-level issue.

### Combined Playwright logs

```bash
mvn -Ptest -Dtest=ExampleTest -Dplaywright.debug=all test
```

Combined output can be noisy. Start with `api`, then use `browser` or `all` only
when more detail is needed.

### Java breakpoint plus Playwright logs

```bash
mvn -Ptest \
    -Dtest=ExampleTest \
    -Dplaywright.debug=all \
    -Dmaven.surefire.debug \
    test
```

Remember: `playwright.debug` controls logs; `maven.surefire.debug` enables Java
breakpoints.

---

## 9. Debugging with different browsers and environments

### Chromium in the test environment

```bash
mvn -Ptest -Dtest=ExampleTest -Dbrowser=chromium -Dheadless=false test
```

### Firefox in the test environment

```bash
mvn -Ptest -Dtest=ExampleTest -Dbrowser=firefox -Dheadless=false test
```

### WebKit in the development environment

```bash
mvn -Pdev -Dtest=ExampleTest -Dbrowser=webkit -Dheadless=false test
```

### Firefox with a Java debugger and Playwright API logging

```bash
mvn -Ptest \
    -Dtest=ExampleTest \
    -Dbrowser=firefox \
    -Dheadless=false \
    -Dplaywright.debug=api \
    -Dmaven.surefire.debug \
    test
```

Make sure the selected Playwright browsers are installed before executing the
corresponding tests.

---

## 10. What to inspect after IntelliJ stops

When the breakpoint is reached, use IntelliJ's **Threads & Variables** view.

Useful expressions for this test include:

```java
urls().mirUi()
```

```java
page().url()
```

```java
page().title()
```

```java
page().context().pages().size()
```

You can inspect an expression with **Evaluate Expression**. On the common
Windows IntelliJ keymap, the shortcut is `Alt+F8`.

### Common debugger controls

| Action | Typical Windows shortcut | Meaning |
|---|---|---|
| Step Over | `F8` | Execute the current line without entering called methods |
| Step Into | `F7` | Enter the method called on the current line |
| Step Out | `Shift+F8` | Finish the current method and return to its caller |
| Resume | `F9` | Continue until the next breakpoint or test completion |
| Evaluate Expression | `Alt+F8` | Evaluate a Java expression while execution is suspended |

Shortcuts may differ if you use another IntelliJ keymap.

---

## 11. Troubleshooting

### Maven does not wait for the debugger

Confirm that the command contains the exact property:

```text
-Dmaven.surefire.debug
```

Also confirm that:

- the Maven `test` phase is present;
- the test name is correct;
- tests are not skipped with `-DskipTests`;
- Maven Surefire actually discovers the TestNG test.

### IntelliJ reports `Connection refused`

The Surefire JVM is probably not listening yet.

1. Start the Maven command first.
2. Wait for Maven to enter debugger-wait mode.
3. Start `Attach Surefire 5005` afterward.

### Port `5005` is already in use

Use a custom port such as `8000`, then configure the same port in IntelliJ.

### The breakpoint is hollow, gray, or never reached

Check the following:

- The remote configuration uses your project module classpath.
- `-Dtest` identifies the correct class.
- The breakpoint is in the code that the selected test actually executes.
- The source file is saved.
- Maven compiled the current source.
- You did not attach to the ordinary Maven JVM instead of the Surefire JVM.

If compiled output appears stale, run once with:

```bash
mvn clean test-compile
```

Then start the Surefire debug command again.

### The command appears to hang

If you used `-Dmaven.surefire.debug`, waiting is expected. Attach the Remote JVM
Debug configuration to continue.

### The browser closes immediately

Make sure you attach before the test begins. Put the breakpoint before the
action you want to examine and use:

```text
-Dheadless=false -DslowMo=2000
```

### `Disconnected from the target VM` appears

If it appears after the test or Maven build finishes, it is normal. It means
the debugged JVM exited and IntelliJ disconnected.

If it appears before the breakpoint:

- confirm that IntelliJ attached to the Surefire port;
- confirm that the test was discovered;
- confirm that the breakpoint belongs to the executed source;
- confirm that no earlier setup failure stopped the test.

### Playwright logs do not appear

Confirm that:

- the property is spelled `playwright.debug`;
- your `PlaywrightFactory` reads that exact property through `FrameworkConfig`;
- the factory passes the resulting `DEBUG` value through
  `Playwright.CreateOptions.setEnv(...)`;
- Playwright is created after configuration is read.

---

## 12. Temporary alternative: disable Surefire forking

For a quick experiment, Surefire can run tests without creating a forked JVM:

```bash
mvn -Ptest -Dtest=ExampleTest -DforkCount=0 test
```

Then debugging the Maven process may also debug the test because both run in
the same JVM.

This is not the recommended permanent solution because it changes normal
Surefire isolation and can differ from Jenkins behavior. Prefer remote
debugging with `-Dmaven.surefire.debug` when you are investigating the real
Maven test execution path.

Do not permanently change the project's `pom.xml` to `forkCount=0` only to make
breakpoints work.

---

## 13. Recommended workflow

### Normal local test execution

```bash
mvn -Ptest -Dtest=ExampleTest test
```

### Watch the browser

```bash
mvn -Ptest -Dtest=ExampleTest -Dheadless=false -DslowMo=1000 test
```

### Investigate Playwright calls

```bash
mvn -Ptest -Dtest=ExampleTest -Dheadless=false -Dplaywright.debug=api test
```

### Stop at Java breakpoints while preserving Maven behavior

```bash
mvn -Ptest -Dtest=ExampleTest -Dheadless=false -Dmaven.surefire.debug test
```

Then attach IntelliJ with `Attach Surefire 5005`.

### Use every useful local diagnostic together

```bash
mvn -Ptest \
    -Dtest=ExampleTest \
    -Dheadless=false \
    -DslowMo=1000 \
    -Dplaywright.debug=api \
    -Dmaven.surefire.debug \
    test
```

---

## 14. Important commands that are not Java breakpoint debugging

### Maven debug output

```bash
mvn -X test
```

`-X` prints detailed Maven diagnostic output. It does not attach IntelliJ to
your test or stop at Java breakpoints.

### Maven exception details

```bash
mvn -e test
```

`-e` prints Maven stack traces. It also does not enable breakpoint debugging.

### Playwright API output

```bash
mvn -Dplaywright.debug=api test
```

This displays Playwright operations but does not stop Java execution.

---

## 15. Quick checklist

Before attaching the debugger, verify:

- [ ] The breakpoint is solid red.
- [ ] The correct test class or method is selected.
- [ ] Maven was started with `-Dmaven.surefire.debug`.
- [ ] Maven is waiting for a debugger connection.
- [ ] IntelliJ uses a **Remote JVM Debug** configuration.
- [ ] IntelliJ host is `localhost`.
- [ ] IntelliJ port matches the Surefire debug port.
- [ ] The project module is selected in the remote configuration.
- [ ] `-Ptest` or the intended Maven profile is present.
- [ ] `-Dheadless=false` is present if you want to see the browser.

---

## 16. Final recommendation for your framework

Do not change `PlaywrightFactory`, `BaseTest`, or `FrameworkConfig` only because
a Maven breakpoint is not reached. That issue is normally caused by attaching
IntelliJ to the Maven JVM instead of Surefire's forked test JVM.

Use these two workflows:

1. **Direct TestNG Debug** for fast local investigation.
2. **Maven Surefire remote debugging** when Maven profiles, POM configuration,
   or Jenkins-like behavior matters.

Your default Maven breakpoint command should be:

```bash
mvn -Ptest \
    -Dtest=ExampleTest \
    -Dheadless=false \
    -DslowMo=1000 \
    -Dplaywright.debug=api \
    -Dmaven.surefire.debug \
    test
```

Start that command first, then attach IntelliJ to `localhost:5005`.

---

## Official documentation

- [Apache Maven Surefire: Debugging Tests](https://maven.apache.org/surefire/maven-surefire-plugin/examples/debugging.html)
- [JetBrains: Testing in Maven](https://www.jetbrains.com/help/idea/work-with-tests-in-maven.html)
- [JetBrains: Maven Run/Debug Configuration](https://www.jetbrains.com/help/idea/run-debug-configuration-maven.html)
- [Playwright Java: Debugging Tests](https://playwright.dev/java/docs/debug)

