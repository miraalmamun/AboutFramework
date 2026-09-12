package dataproviders;

import org.testng.annotations.DataProvider;

import java.io.IOException;


public final class CommonDataProviders {

    private CommonDataProviders() {
        // Prevent utility-class instantiation
    }

    @DataProvider(name = "excelLoginData")
    public static Object[][] excelLoginData() throws IOException {
        return ExcelDataProvider.getExcelDataFromClasspath(
                "testdata/loginData.xlsx",
                "LoginData"
        );
    }

    @DataProvider(name = "jsonLoginData")
    public Object[][] jsonLoginData() throws IOException {
        return JsonDataProvider.getJsonDataFromClasspath(
                "testdata/loginData.json"
        );
    }

}