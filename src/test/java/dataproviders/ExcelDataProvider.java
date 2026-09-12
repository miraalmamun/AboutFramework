package dataproviders;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads tabular test data from Excel workbooks and converts it into the
 * Object[][] format expected by TestNG data providers.
 *
 * <h2>Why is this class called ExcelDataProvider?</h2>
 *
 * <p>The name describes the responsibility of the class:</p>
 *
 * <ul>
 *     <li>ExcelData means the source of the test data is an Excel workbook.</li>
 *     <li>Provider means the method provides Excel data to another
 *     component, normally a TestNG data-provider method.</li>
 * </ul>
 *
 * <p>Technically, this class is an Excel reader/helper. It does not itself
 * register a method with TestNG because its methods do not have the
 * {@code @DataProvider} annotation. The actual TestNG provider is normally
 * placed in a separate class such as {@code TestDataProviders}.</p>
 *
 * <h2>Why can this class be used by a TestNG data provider?</h2>
 *
 * <p>TestNG data-provider methods commonly return an {@code Object[][]}.
 * This class returns exactly that type:</p>
 *
 * <pre>{@code
 * Object[][] data
 * }</pre>
 *
 * <p>Each inner {@code Object[]} represents one test invocation. In this
 * implementation, each inner array contains one map representing one Excel
 * row:</p>
 *
 * <pre>{@code
 * Object[][] {
 *     { row1Map },
 *     { row2Map }
 * }
 * }</pre>
 *
 * <p>Because the returned structure matches the TestNG data-provider
 * contract, a method annotated with {@code @DataProvider} can simply return
 * the result of {@link #getExcelDataFromClasspath(String, String)}.</p>
 *
 * <h2>Example usage with TestNG</h2>
 *
 * <pre>{@code
 * // TestDataProviders.java
 *
 * package dataproviders;
 *
 * import org.testng.annotations.DataProvider;
 *
 * import java.io.IOException;
 *
 * public final class TestDataProviders {
 *
 *     private TestDataProviders() {
 *     }
 *
 *     @DataProvider(name = "excelLoginData", parallel = false)
 *     public static Object[][] excelLoginData() throws IOException {
 *         return ExcelDataProvider.getExcelDataFromClasspath(
 *                 "testdata/loginData.xlsx",
 *                 "LoginData"
 *         );
 *     }
 * }
 *
 *
 * // LoginExcelTest.java
 *
 * package tests;
 *
 * import dataproviders.TestDataProviders;
 * import org.testng.annotations.Test;
 *
 * import java.util.Map;
 *
 * public final class LoginExcelTest {
 *
 *     @Test(
 *             dataProvider = "excelLoginData",
 *             dataProviderClass = TestDataProviders.class
 *     )
 *     public void loginTest(Map<String, String> data) {
 *         String username = data.get("username");
 *         String password = data.get("password");
 *         String expectedResult = data.get("expectedResult");
 *
 *         // Use the values in the test.
 *     }
 * }
 * }</pre>
 *
 * <h2>How TestNG uses the result</h2>
 *
 * <p>If the Excel worksheet contains two data rows, TestNG invokes the test
 * method twice:</p>
 *
 * <pre>{@code
 * Excel row 1 -> loginTest(row1Map)
 * Excel row 2 -> loginTest(row2Map)
 * }</pre>
 *
 * <p>This class is responsible for reading and formatting Excel values.
 * Business-specific validation and conversion to domain objects such as
 * {@code LoginData} should be handled by a separate mapper or loader.</p>
 *
 * @see org.apache.poi.ss.usermodel.WorkbookFactory
 * @see org.apache.poi.ss.usermodel.DataFormatter
 */
public final class ExcelDataProvider {

    /**
     * Prevents instantiation because this utility class exposes static methods.
     */
    private ExcelDataProvider() {
        // Prevent utility-class instantiation
    }

    /**
     * Reads test data from an Excel workbook located on the classpath.
     *
     * <p>The resource path must be relative to the classpath. In a Maven
     * project, a workbook under {@code src/test/resources/testdata} is loaded
     * using:</p>
     *
     * <pre>{@code
     * testdata/loginData.xlsx
     * }</pre>
     *
     * <p>Do not include {@code src/test/resources} in the resource name.</p>
     *
     * <p>The selected worksheet must contain:</p>
     *
     * <ol>
     *     <li>A header row.</li>
     *     <li>At least one nonblank data row.</li>
     *     <li>Unique, nonblank column headers.</li>
     * </ol>
     *
     * <p>This method can be used inside a TestNG data-provider method because
     * it returns {@code Object[][]}, which is a valid TestNG data-provider
     * return type.</p>
     *
     * <p>This method does not perform browser actions. It only reads the
     * workbook and prepares the data.</p>
     *
     * @param resourceName the classpath-relative path of the Excel workbook
     * @param sheetName the name of the worksheet containing test data
     *
     * @return a TestNG-compatible two-dimensional array. Each outer element
     *         represents one test invocation, and each inner element contains
     *         one unmodifiable map representing one Excel row
     *
     * @throws IOException if the Excel resource cannot be found, the workbook
     *                     cannot be opened, the worksheet is missing, a header
     *                     is invalid, or no data rows are available
     */
    public static Object[][] getExcelDataFromClasspath(
            String resourceName,
            String sheetName
    ) throws IOException {

        InputStream inputStream = ExcelDataProvider.class
                .getClassLoader()
                .getResourceAsStream(resourceName);

        if (inputStream == null) {
            throw new IOException(
                    "Excel resource not found on classpath: "
                            + resourceName
            );
        }

        try (InputStream stream = inputStream;
             Workbook workbook = WorkbookFactory.create(stream)) {

            return convertSheetToDataProvider(
                    workbook,
                    sheetName,
                    resourceName
            );
        }
    }

    /**
     * Converts one Excel worksheet into the Object[][] structure expected by
     * TestNG data providers.
     *
     * <p>The first available row is treated as the header row. Each following
     * nonblank row becomes one test invocation. Cell values are converted to
     * strings using {@link DataFormatter}. Formula values are evaluated using
     * {@link FormulaEvaluator}.</p>
     *
     * @param workbook the opened Excel workbook
     * @param sheetName the worksheet name to convert
     * @param source a source description used in diagnostic messages
     *
     * @return a two-dimensional array containing one unmodifiable map per
     *         Excel data row
     *
     * @throws IOException if the worksheet does not exist, the headers are
     *                     invalid, or no nonblank data rows are found
     */
    private static Object[][] convertSheetToDataProvider(
            Workbook workbook,
            String sheetName,
            String source
    ) throws IOException {

        Sheet sheet = workbook.getSheet(sheetName);

        if (sheet == null) {
            throw new IOException(
                    "Sheet '" + sheetName
                            + "' was not found in Excel resource: "
                            + source
            );
        }

        int headerRowIndex = sheet.getFirstRowNum();
        Row headerRow = sheet.getRow(headerRowIndex);

        if (headerRow == null) {
            throw new IOException(
                    "Header row was not found in sheet: " + sheetName
            );
        }

        int firstColumn = headerRow.getFirstCellNum();
        int lastColumn = headerRow.getLastCellNum();

        if (firstColumn < 0 || lastColumn <= firstColumn) {
            throw new IOException(
                    "No column headers were found in sheet: " + sheetName
            );
        }

        DataFormatter formatter = new DataFormatter();

        FormulaEvaluator evaluator = workbook
                .getCreationHelper()
                .createFormulaEvaluator();

        List<String> headers = getHeaders(
                headerRow,
                firstColumn,
                lastColumn,
                formatter,
                evaluator,
                sheetName
        );

        List<Object[]> testRows = new ArrayList<>();

        for (int rowIndex = headerRowIndex + 1;
             rowIndex <= sheet.getLastRowNum();
             rowIndex++) {

            Row row = sheet.getRow(rowIndex);

            if (row == null || isBlankRow(
                    row,
                    firstColumn,
                    lastColumn,
                    formatter,
                    evaluator
            )) {
                continue;
            }

            Map<String, String> rowData = new LinkedHashMap<>();

            for (int columnIndex = firstColumn;
                 columnIndex < lastColumn;
                 columnIndex++) {

                String value = formatter.formatCellValue(
                        row.getCell(
                                columnIndex,
                                Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                        ),
                        evaluator
                );

                rowData.put(
                        headers.get(columnIndex - firstColumn),
                        value
                );
            }

            testRows.add(
                    new Object[]{
                            Collections.unmodifiableMap(rowData)
                    }
            );
        }

        if (testRows.isEmpty()) {
            throw new IOException(
                    "No test-data rows were found in sheet '"
                            + sheetName
                            + "' from "
                            + source
            );
        }

        return testRows.toArray(Object[][]::new);
    }

    /**
     * Reads and validates the header row of the worksheet.
     *
     * <p>Every header is formatted and trimmed. Blank and duplicate headers
     * are rejected because they could cause missing or overwritten values in
     * the row map.</p>
     *
     * @param headerRow the row containing the column headers
     * @param firstColumn the first column index to read
     * @param lastColumn the column index immediately after the last column
     * @param formatter the formatter used to convert cells to strings
     * @param evaluator the formula evaluator used for formula cells
     * @param sheetName the worksheet name used in error messages
     *
     * @return an immutable ordered list of validated header names
     *
     * @throws IOException if a header is blank or duplicated
     */
    private static List<String> getHeaders(
            Row headerRow,
            int firstColumn,
            int lastColumn,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            String sheetName
    ) throws IOException {

        List<String> headers = new ArrayList<>();
        Set<String> uniqueHeaders = new LinkedHashSet<>();

        for (int columnIndex = firstColumn;
             columnIndex < lastColumn;
             columnIndex++) {

            String header = formatter.formatCellValue(
                    headerRow.getCell(
                            columnIndex,
                            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                    ),
                    evaluator
            ).strip();

            if (header.isBlank()) {
                throw new IOException(
                        "Blank header found at column "
                                + (columnIndex + 1)
                                + " in sheet: "
                                + sheetName
                );
            }

            if (!uniqueHeaders.add(header)) {
                throw new IOException(
                        "Duplicate header '" + header
                                + "' found in sheet: "
                                + sheetName
                );
            }

            headers.add(header);
        }

        return List.copyOf(headers);
    }

    /**
     * Checks whether all cells in the specified range are blank.
     *
     * <p>Missing cells and cells whose formatted values are empty are treated
     * as blank. Completely blank rows are skipped instead of being returned
     * as test invocations.</p>
     *
     * @param row the row to inspect
     * @param firstColumn the first column index to inspect
     * @param lastColumn the column index immediately after the last column
     * @param formatter the formatter used to convert cells to strings
     * @param evaluator the formula evaluator used for formula cells
     *
     * @return {@code true} if every inspected cell is blank; otherwise
     *         {@code false}
     */
    private static boolean isBlankRow(
            Row row,
            int firstColumn,
            int lastColumn,
            DataFormatter formatter,
            FormulaEvaluator evaluator
    ) {

        for (int columnIndex = firstColumn;
             columnIndex < lastColumn;
             columnIndex++) {

            String value = formatter.formatCellValue(
                    row.getCell(
                            columnIndex,
                            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                    ),
                    evaluator
            );

            if (!value.isBlank()) {
                return false;
            }
        }

        return true;
    }
}