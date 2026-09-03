# JSON Data Provider Outside the Classpath

## Purpose

This guide explains how to read JSON test data stored inside the project directory but outside Java's classpath. In this situation, use Java's `Path` and `Files` APIs instead of `ClassLoader`.

This approach avoids manually joining paths with `/` or `\\` and therefore works on Windows, macOS, and Linux.

## Project structure

```text
AboutFramework
├── pom.xml
├── testdata
│   └── loginData.json
└── src
    └── test
        └── java
            ├── tests
            │   └── LoginTest.java
            └── utilities
                └── DataProviderUtil.java
```

The `testdata` directory is directly under the project root. It is not under `src/test/resources`, so it is not a classpath resource.

## Gson dependency

Add Gson inside the `<dependencies>` section of `pom.xml`:

```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.14.0</version>
</dependency>
```

## DataProviderUtil implementation

```java
package utilities;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
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
            List<Map<String, String>> testData;

            try {
                testData = GSON.fromJson(reader, TEST_DATA_TYPE);
            } catch (JsonParseException exception) {
                throw new IOException(
                        "Invalid JSON file: " + jsonPath,
                        exception
                );
            }

            if (testData == null || testData.isEmpty()) {
                throw new IOException(
                        "JSON file contains no test data: " + jsonPath
                );
            }

            return testData.stream()
                    .map(data -> new Object[]{data})
                    .toArray(Object[][]::new);
        }
    }
}
```

## JSON example

Save this file as `AboutFramework/testdata/loginData.json`:

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

Use only dummy credentials in a public repository. Never commit real passwords, tokens, API keys, or company credentials.

## TestNG usage

```java
package tests;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import utilities.DataProviderUtil;

import java.io.IOException;
import java.util.Map;

public class LoginTest {

    @DataProvider(name = "loginData")
    public Object[][] loginData() throws IOException {
        return DataProviderUtil.getJsonDataFromProject(
                "testdata",
                "loginData.json"
        );
    }

    @Test(dataProvider = "loginData")
    public void loginTest(Map<String, String> data) {
        String username = data.get("username");
        String password = data.get("password");

        System.out.println("Username: " + username);

        // Do not print passwords in real test logs.
        // loginPage.login(username, password);
    }
}
```

## Why use Path instead of ClassLoader?

`ClassLoader` is designed for resources available on the Java classpath, such as files under `src/main/resources` or `src/test/resources`.

The file in this example is located here:

```text
AboutFramework/testdata/loginData.json
```

Because it is outside the classpath, use filesystem APIs:

```text
Path
Files
```

## Why pass separate path components?

Use:

```text
getJsonDataFromProject("testdata", "loginData.json")
```

Do not manually construct paths such as:

```text
// Avoid
"testdata/loginData.json"
"testdata\\loginData.json"
```

`Path.of(first, more)` lets the active filesystem choose the correct separator.

The resolved location will resemble:

```text
Windows:
C:\Users\miraa\IdeaProjects\AboutFramework\testdata\loginData.json

macOS/Linux:
/path/to/AboutFramework/testdata/loginData.json
```

## Why use user.dir?

```text
System.getProperty("user.dir")
```

`user.dir` identifies the working directory from which the Java process started. IntelliJ and Maven normally start the test with the project root as the working directory, allowing the method to resolve the `testdata` folder.

This assumption is not guaranteed. A Jenkins job or custom run configuration can use a different working directory. When that happens, either configure the working directory as the project root or supply the test-data directory through a system property.

## Optional configurable location

For greater flexibility in Jenkins, read a custom system property with the project directory as the default:

```text
String configuredDirectory = System.getProperty(
        "test.data.dir",
        Path.of(System.getProperty("user.dir"), "testdata").toString()
);

Path jsonPath = Path.of(configuredDirectory, "loginData.json")
        .toAbsolutePath()
        .normalize();
```

Example Maven command on Windows:

```text
mvn test -Dtest.data.dir=C:\automation-data
```

Example Maven command on macOS or Linux:

```text
mvn test -Dtest.data.dir=/opt/automation-data
```

This configuration is useful when test data is maintained outside the repository.

## Why normalize and validate the path?

```text
toAbsolutePath().normalize()
```

This creates a predictable absolute path and removes redundant path elements.

The following validation ensures that the resolved path remains inside the project directory:

```text
if (!jsonPath.startsWith(projectRoot)) {
    throw new IOException(...);
}
```

The regular-file check produces a clear error when the file is missing or the supplied location points to a directory:

```text
Files.isRegularFile(jsonPath)
```

## Why use Files.newBufferedReader?

```text
Files.newBufferedReader(jsonPath, StandardCharsets.UTF_8)
```

This reads the filesystem file efficiently as characters, explicitly applies UTF-8, and supplies a `Reader` directly to Gson without first loading the entire file into a separate `String`.

The try-with-resources statement closes the reader automatically, including when parsing fails.

## Data-type limitation

The utility uses:

```text
Map<String, String>
```

Therefore, every JSON value should be a string. If the JSON contains actual numbers, booleans, arrays, nested objects, or mixed types, change both occurrences to:

```text
Map<String, Object>
```

For stable test-data structures, consider using a dedicated Java class or `record` instead of a map.

## Classpath versus project-file approach

| File location | Recommended API | Example input |
|---|---|---|
| `src/test/resources/testdata/loginData.json` | `ClassLoader.getResourceAsStream()` | `"testdata/loginData.json"` |
| `AboutFramework/testdata/loginData.json` | `Path` and `Files` | `"testdata", "loginData.json"` |
| External configurable directory | `Path` and a system property | `-Dtest.data.dir=...` |

## Recommendation

Prefer `src/test/resources` and `ClassLoader` for test data committed with the project. Use this project-file implementation when the file must intentionally remain outside the classpath or needs to be managed as a normal filesystem file.
