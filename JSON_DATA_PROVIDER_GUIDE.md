# JSON Test Data Provider Guide

## Purpose

`DataProviderUtil` reads JSON test data from the Java classpath, converts each JSON object into a `Map`, and returns the data as `Object[][]` for TestNG's `@DataProvider`.

This design is intended for test-data files stored under `src/test/resources`. It works consistently on Windows, macOS, Linux, local development machines, Maven builds, and Jenkins agents.

## Gson dependency

Add Gson inside the `<dependencies>` section of `pom.xml`:

```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.14.0</version>
</dependency>
```

The `<!-- Source: ... -->` comment shown by some dependency websites is informational only and is not required.

## Project structure

```text
AboutFramework
├── pom.xml
└── src
    └── test
        ├── java
        │   └── utilities
        │       └── DataProviderUtil.java
        └── resources
            └── testdata
                └── loginData.json
```

Maven adds `src/test/resources` to the test classpath. Therefore, the resource name starts after `src/test/resources`.

For this file:

```text
src/test/resources/testdata/loginData.json
```

use this resource name:

```java
"testdata/loginData.json"
```

Do not use:

```java
// Wrong: src/test/resources is already the classpath root
"src/test/resources/testdata/loginData.json"

// Wrong for ClassLoader.getResourceAsStream(): no leading slash
"/testdata/loginData.json"

// Correct
"testdata/loginData.json"
```

## DataProviderUtil implementation

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
import java.util.List;
import java.util.Map;

public final class DataProviderUtil {

    private static final Gson GSON = new Gson();

    private static final Type TEST_DATA_TYPE =
            new TypeToken<List<Map<String, String>>>() {}.getType();

    private DataProviderUtil() {
        // Prevent utility-class instantiation
    }

    public static Object[][] getJsonDataToMap(String resourceName)
            throws IOException {

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
            List<Map<String, String>> testData;

            try {
                testData = GSON.fromJson(reader, TEST_DATA_TYPE);
            } catch (JsonParseException exception) {
                throw new IOException(
                        "Invalid JSON in resource: " + resourceName,
                        exception
                );
            }

            if (testData == null || testData.isEmpty()) {
                throw new IOException(
                        "JSON resource contains no test data: " + resourceName
                );
            }

            return testData.stream()
                    .map(data -> new Object[]{data})
                    .toArray(Object[][]::new);
        }
    }
}
```

## Example JSON test data

```json
[
  {
    "username": "mir@example.com",
    "password": "abc123",
    "expectedResult": "success"
  },
  {
    "username": "invalid@example.com",
    "password": "wrong-password",
    "expectedResult": "failure"
  }
]
```

The top-level JSON value must be an array. Each object in the array represents one TestNG test execution.

## TestNG usage

```java
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Map;

public class LoginTest {

    @DataProvider(name = "loginData")
    public Object[][] loginData() throws IOException {
        return DataProviderUtil.getJsonDataToMap(
                "testdata/loginData.json"
        );
    }

    @Test(dataProvider = "loginData")
    public void loginTest(Map<String, String> data) {
        String username = data.get("username");
        String password = data.get("password");
        String expectedResult = data.get("expectedResult");

        // Use the values in the test steps and assertions.
    }
}
```

If the JSON array contains two objects, TestNG runs `loginTest()` twice. Each execution receives one complete map.

## Why use ClassLoader?

`ClassLoader` is appropriate because the JSON file is a project resource under `src/test/resources`, not an arbitrary external file.

Benefits include:

- **Cross-platform resource loading:** The code does not build operating-system paths with `/` or `\`.
- **Stable Maven and Jenkins execution:** Loading does not depend on the process working directory represented by `user.dir`.
- **Packaged-resource support:** Classpath resources can still be read when they are packaged rather than present as ordinary source-tree files.
- **Standard Maven structure:** Maven automatically copies test resources into the test runtime classpath, normally under `target/test-classes`.

Classpath resource names use `/` as their logical separator on every operating system. They are resource names, not Windows or Unix filesystem paths.

Use `Path` and `Files` instead when the JSON file is external to the application, such as a configurable folder, upload directory, network drive, or file supplied at runtime.

## Why use InputStream and Reader?

`getResourceAsStream()` returns an `InputStream` because a classpath resource might come from a normal directory or a packaged archive. `InputStreamReader` converts those bytes into characters that Gson can parse.

The reader is created inside a try-with-resources statement:

```java
try (Reader reader = ...) {
    // Read JSON
}
```

Java therefore closes the reader and underlying stream automatically, including when parsing fails.

## Why specify UTF-8?

```java
StandardCharsets.UTF_8
```

Explicit UTF-8 prevents the result from depending on the default character encoding of a developer's computer or Jenkins agent. This is important when test data contains names, symbols, or non-English text.

## Why reuse Gson and Type?

```java
private static final Gson GSON = new Gson();
```

The utility reuses one configured Gson instance instead of constructing a new instance for every method call.

```java
private static final Type TEST_DATA_TYPE =
        new TypeToken<List<Map<String, String>>>() {}.getType();
```

`TypeToken` preserves the complete generic type information that Gson needs. Java cannot express the required type as `List<Map<String, String>>.class` because generic type information is erased at runtime.

The type means:

- The JSON root is a `List`.
- Each list element is a `Map`.
- Every map key is a `String`.
- Every map value is a `String`.

Making this `Type` a constant avoids rebuilding the same type description for every call.

## Why use Map instead of HashMap?

```java
Map<String, String>
```

The test needs map behavior, but it does not require a specific map implementation. Declaring the interface keeps the code flexible and avoids unnecessary coupling to `HashMap`.

## Why return Object[][]?

TestNG data providers commonly return `Object[][]`:

- Each row represents one test execution.
- Each column represents one argument passed to the test method.

This utility places one map in each row:

```text
Row 1: [first JSON object as a Map]
Row 2: [second JSON object as a Map]
```

The test method consequently accepts one parameter:

```java
public void loginTest(Map<String, String> data)
```

## Error handling

The utility reports three distinct problems:

1. The requested resource was not found on the classpath.
2. The resource contains malformed JSON.
3. The JSON is empty or contains no test-data records.

These messages make failures easier to diagnose locally and in Jenkins logs.

## Data-type limitation

`Map<String, String>` assumes every JSON value is a string:

```json
{
  "username": "mir",
  "active": "true",
  "attempts": "3"
}
```

If the JSON contains actual numbers, booleans, arrays, nested objects, or mixed value types, change both occurrences of:

```java
Map<String, String>
```

to:

```java
Map<String, Object>
```

For test data with a stable, well-defined structure, a dedicated Java class or `record` provides stronger type safety than a map.

## Summary

Use this implementation when JSON test data is maintained under `src/test/resources`. Pass the classpath-relative resource name, reuse Gson and the generic `Type`, read with UTF-8, close resources automatically, and return one map per TestNG invocation.
