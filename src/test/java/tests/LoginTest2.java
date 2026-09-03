package tests;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import utilities.DataProviderUtil;

import java.io.IOException;
import java.util.Map;

public class LoginTest2 {

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

        // Replace this with your actual login-page call.
        System.out.println("Password: " + password);
    }
}