package tests;

import dataproviders.CommonDataProviders;
import org.testng.annotations.Test;

import java.util.Map;

public class LoginExcelTest {

    @Test(
            dataProvider = "excelLoginData",
            dataProviderClass = CommonDataProviders.class
    )
    public void loginTest(Map<String, String> data) {

        String username = data.get("username");
        String password = data.get("password");
        String expectedResult = data.get("expectedResult");

        System.out.println("Username: " + username);
        System.out.println("Expected result: " + expectedResult);
        System.out.println("Password: " + password);
//        System.out.println("Hello: " + data.get("Hello"));

        // Never print the password.
        // loginPage.login(username, password);
    }
}