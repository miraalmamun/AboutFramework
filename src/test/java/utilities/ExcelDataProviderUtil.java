package utilities;

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

public final class ExcelDataProviderUtil {

    private ExcelDataProviderUtil() {
        // Prevent utility-class instantiation
    }

    public static Object[][] getExcelDataFromClasspath(
            String resourceName,
            String sheetName
    ) throws IOException {

        InputStream inputStream = ExcelDataProviderUtil.class
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