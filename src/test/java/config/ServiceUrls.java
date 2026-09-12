package config;

public final class ServiceUrls {
    public final String mirUi;
    public final String mirApi;
    public final String adminApi;

    private ServiceUrls(String mirUi, String mirApi, String adminApi) {
        this.mirUi = mirUi;
        this.mirApi = mirApi;
        this.adminApi = adminApi;
    }

    public static ServiceUrls load() {
        return new ServiceUrls(
                FrameworkConfig.requiredText("MIR.SERVICE.UI.URL"),
                FrameworkConfig.requiredText("MIR.SERVICE.API.URL"),
                FrameworkConfig.requiredText("ADMINISTRATION.SERVICE.API.URL")
        );
    }
}