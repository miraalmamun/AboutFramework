package config;

public final class ServiceUrlsTwo {
    public final String mirUi;
    public final String mirApi;
    public final String adminApi;

    private ServiceUrlsTwo(String mirUi, String mirApi, String adminApi) {
        this.mirUi = mirUi;
        this.mirApi = mirApi;
        this.adminApi = adminApi;
    }

    public static ServiceUrlsTwo load() {
        return new ServiceUrlsTwo(
                FrameworkConfig.requiredText("MIR.SERVICE.UI.URL"),
                FrameworkConfig.requiredText("MIR.SERVICE.API.URL"),
                FrameworkConfig.requiredText("ADMINISTRATION.SERVICE.API.URL")
        );
    }
}