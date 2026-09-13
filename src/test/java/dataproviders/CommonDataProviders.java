package dataproviders;

import org.testng.annotations.DataProvider;

import java.io.IOException;

/**
 * Defines reusable TestNG data-provider entry points for test classes.
 *
 * <p>Each provider is {@code static} because tests refer to this separate
 * utility class through {@code dataProviderClass}. TestNG requires a provider
 * on an external provider class to be static.</p>
 */
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
    public static Object[][] jsonLoginData() throws IOException {
        return JsonDataProvider.getJsonDataFromClasspath(
                "testdata/loginData.json"
        );
    }

}
