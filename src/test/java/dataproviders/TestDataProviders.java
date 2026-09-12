package dataproviders;

import org.testng.annotations.DataProvider;
import utilities.ExcelDataProvider;

import java.io.IOException;

public final class TestDataProviders {

    private TestDataProviders() {
        // Prevent utility-class instantiation
    }

    @DataProvider(name = "excelLoginData")
    public static Object[][] excelLoginData() throws IOException {
        return ExcelDataProvider.getExcelDataFromClasspath(
                "testdata/loginData.xlsx",
                "LoginData"
        );
    }
}