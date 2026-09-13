package tests;

import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public final class ExampleTest extends BaseTest {
   //mvn -Dtest=ExampleTest -Dheadless=false -DslowMo=2000 test
    @Test
    public void playwrightWebsiteShouldOpen() {
        page().navigate(urls().mirUi());
        assertThat(page()).hasTitle(
                java.util.regex.Pattern.compile("Playwright")
        );
    }
}