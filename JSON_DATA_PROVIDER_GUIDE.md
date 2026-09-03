# JSON Test Data Provider Guide

## Purpose

`DataProviderUtil` reads JSON test data from either:

1. The Java classpath, such as `src/test/resources`.
2. A normal folder under the project root, such as `AboutFramework/testdata`.

It converts each JSON object into a `Map<String, String>` and returns the records as `Object[][]` for TestNG's `@DataProvider`.

## Gson dependency

Add Gson inside the `<dependencies>` section of `pom.xml`:

```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.14.0</version>
</dependency>
```

The `<!-- Source: ... -->` comment shown by dependency websites is informational and is not required.

## Project structure

```text
AboutFramework
├── pom.xml
├── testdata
│   └── loginData.json                 # Normal project file
└── src
    └── test
        ├── java
        │   ├── tests
        │   │   └── LoginTest.java
        │   └── utilities
        │       └── DataProviderUtil.java
        └── resources
            └── testdata
                └── loginData.json     # Classpath resource
```

The two JSON locations demonstrate different loading approaches. A real test normally uses only the location appropriate for the framework.

## Complete DataProviderUtil

```java
package utilities;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class DataProviderUtil {

    private static final Gson GSON = new Gson();

    private static final Type TEST_DATA_TYPE =
            new TypeToken<List<Map<String, String>>>() {}.getType();

    private DataProviderUtil() {
        // Prevent utility-class instantiation
    }

    /*
     * Use for files under src/test/resources.
     */
    public static Object[][] getJsonDataFromClasspath(
            String resourceName
    ) throws IOException {

        InputStream inputStream = DataProviderUtil.class
                .getClassLoader()
                .getResourceAsStream(resourceName);

        if (inputStream == null) {
            throw new IOException(
                    "JSON resource not found on classpath: " + resourceName
            );
        }

        try (Reader reader = new InputStreamReader(
                inputStream,
                StandardCharsets.UTF_8
        )) {
            return convertJsonToDataProvider(
                    reader,
                    "classpath resource: " + resourceName
            );
        }
    }

    /*
     * Use for files under the project folder but outside the classpath.
     */
    public static Object[][] getJsonDataFromProject(
            String first,
            String... more
    ) throws IOException {

        Path projectRoot = Path.of(
                System.getProperty("user.dir")
        ).toAbsolutePath().normalize();

        Path jsonPath = projectRoot
                .resolve(Path.of(first, more))
                .normalize();

        if (!jsonPath.startsWith(projectRoot)) {
            throw new IOException(
                    "JSON file must be inside the project folder: " + jsonPath
            );
        }

        if (!Files.isRegularFile(jsonPath)) {
            throw new IOException(
                    "JSON file not found: " + jsonPath
            );
        }

        try (Reader reader = Files.newBufferedReader(
                jsonPath,
                StandardCharsets.UTF_8
        )) {
            return convertJsonToDataProvider(
                    reader,
                    "file: " + jsonPath
            );
        }
    }

    private static Object[][] convertJsonToDataProvider(
            Reader reader,
            String source
    ) throws IOException {

        List<Map<String, String>> testData;

        try {
            testData = GSON.fromJson(reader, TEST_DATA_TYPE);
        } catch (JsonParseException exception) {
            throw new IOException(
                    "Invalid JSON in " + source,
                    exception
            );
        }

        if (testData == null || testData.isEmpty()) {
            throw new IOException(
                    "JSON contains no test data in " + source
            );
        }

        return testData.stream()
                .map(data -> new Object[]{data})
                .toArray(Object[][]::new);
    }
}
```

## JSON example

```json
[
  {
    "username": "Mir",
    "password": "mimo@123"
  },
  {
    "username": "Mir A",
    "password": "mimo@1234"
  }
]
```

The top-level JSON value must be an array. Each object becomes one TestNG execution. Use only dummy credentials in a public repository.

## Option 1: JSON on the classpath

Place the file here:

```text
src/test/resources/testdata/loginData.json
```

Maven places `src/test/resources` on the test classpath. Pass only the resource name after that root:

```text
testdata/loginData.json
```

Do not include `src/test/resources`, and do not start the resource name with `/`.

### Classpath TestNG example

```java
package tests;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import utilities.DataProviderUtil;

import java.io.IOException;
import java.util.Map;

public class ClasspathLoginTest {

    @DataProvider(name = "classpathLoginData")
    public Object[][] classpathLoginData() throws IOException {
        return DataProviderUtil.getJsonDataFromClasspath(
                "testdata/loginData.json"
        );
    }

    @Test(dataProvider = "classpathLoginData")
    public void loginTest(Map<String, String> data) {
        String username = data.get("username");
        String password = data.get("password");

        System.out.println("Username: " + username);
        // loginPage.login(username, password);
    }
}
```

## Why use ClassLoader?

`ClassLoader` is intended for resources under `src/main/resources` or `src/test/resources`.

- It does not depend on the process working directory.
- It works consistently in IntelliJ, Maven, Jenkins, Windows, macOS, and Linux.
- It can read resources from a classpath directory or packaged archive.
- Maven normally copies test resources under `target/test-classes`.

Classpath resource names use `/` on every operating system because they are logical resource names, not native filesystem paths.

## Option 2: JSON under the project root

Place the file here:

```text
AboutFramework/testdata/loginData.json
```

This file is outside the classpath, so use `Path` and `Files`.

### Project-file TestNG example

```java
package tests;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import utilities.DataProviderUtil;

import java.io.IOException;
import java.util.Map;

public class ProjectFileLoginTest {

    @DataProvider(name = "projectLoginData")
    public Object[][] projectLoginData() throws IOException {
        return DataProviderUtil.getJsonDataFromProject(
                "testdata",
                "loginData.json"
        );
    }

    @Test(dataProvider = "projectLoginData")
    public void loginTest(Map<String, String> data) {
        String username = data.get("username");
        String password = data.get("password");

        System.out.println("Username: " + username);
        // loginPage.login(username, password);
    }
}
```

## Why use Path and Files?

`ClassLoader` cannot normally locate an arbitrary project-root file. Java's filesystem APIs are appropriate for a normal file outside the classpath.

Pass separate path components:

```text
getJsonDataFromProject("testdata", "loginData.json")
```

`Path.of(first, more)` lets the active filesystem choose the correct separator, so the code does not decide between `/` and `\\`.

```text
Windows:
C:\Users\miraa\IdeaProjects\AboutFramework\testdata\loginData.json

macOS/Linux:
/path/to/AboutFramework/testdata/loginData.json
```

## Important user.dir limitation

`System.getProperty("user.dir")` identifies the working directory from which Java was started. IntelliJ and Maven normally use the project root, but this is configurable and not guaranteed.

If Jenkins starts Java from another directory, configure the working directory as the project root or provide an explicit test-data base directory through a system property.

## Why use a shared conversion method?

Both public loading methods call:

```text
convertJsonToDataProvider(reader, source)
```

This keeps Gson parsing, validation, and `Object[][]` conversion in one place. Both loading approaches consequently behave consistently.

## Why use Reader and UTF-8?

`InputStreamReader` converts a classpath stream to characters. `Files.newBufferedReader()` opens a normal filesystem file as characters.

Both approaches explicitly use:

```text
StandardCharsets.UTF_8
```

This prevents the result from depending on the default encoding of a developer's computer or Jenkins agent. Try-with-resources closes each reader automatically.

## Why reuse Gson and Type?

The utility reuses one Gson instance and one generic type description:

```text
private static final Gson GSON = new Gson();

private static final Type TEST_DATA_TYPE =
        new TypeToken<List<Map<String, String>>>() {}.getType();
```

`TypeToken` preserves the generic type information Gson needs. Java cannot express this as `List<Map<String, String>>.class` because generic type information is erased at runtime.

## Why Map instead of HashMap?

The code needs map behavior but does not require a particular implementation. Declaring `Map<String, String>` keeps it flexible and avoids unnecessary coupling to `HashMap`.

## Why return Object[][]?

TestNG commonly accepts `Object[][]` from a data provider:

- Each row represents one test execution.
- Each column represents one argument supplied to the test method.

This utility places one map in every row, so the test accepts one `Map<String, String>` parameter.

## Error handling

The utility reports:

1. A missing classpath resource.
2. A missing project file or non-regular file.
3. A project path that navigates outside the project root.
4. Invalid JSON syntax.
5. JSON containing no test records.

## Data-type limitation

`Map<String, String>` requires every JSON value to be a string. For actual numbers, booleans, arrays, nested objects, or mixed values, change both generic declarations to:

```text
Map<String, Object>
```

For stable test-data structures, consider a dedicated Java class or `record`.

## Which option should be used?

| JSON location | Recommended call |
|---|---|
| `src/test/resources/testdata/loginData.json` | `getJsonDataFromClasspath("testdata/loginData.json")` |
| `AboutFramework/testdata/loginData.json` | `getJsonDataFromProject("testdata", "loginData.json")` |
| External configurable directory | Use `Path` with a configurable base-directory property |

For test data committed with the automation project, prefer `src/test/resources` and `getJsonDataFromClasspath()`. Use `getJsonDataFromProject()` when the file intentionally remains outside the classpath.
