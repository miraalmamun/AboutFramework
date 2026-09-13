package config;

/**
 * Contains the application and API URLs for the selected environment.
 *
 * <p>The object is immutable and safe to share between parallel tests.</p>
 *
 * @param mirUi MIR user-interface URL
 * @param fileApi File Service API URL
 * @param mirApi MIR Service API URL
 * @param administrationApi Administration Service API URL
 * @param submissionSupportApi Submission Support Service API URL
 * @param profileApi Profile Service API URL
 * @param mirTwoKidsApi MIR 2 Kids Service API URL
 * @param wifeApi WIFE Service API URL
 */
public record ServiceUrls(
        String mirUi,
        String fileApi,
        String mirApi,
        String administrationApi,
        String submissionSupportApi,
        String profileApi,
        String mirTwoKidsApi,
        String wifeApi
) {

    /**
     * Loads the service URLs from the selected environment's
     * configuration.properties file.
     *
     * @return immutable collection of service URLs
     * @throws IllegalStateException if any required URL is missing or blank
     */
    public static ServiceUrls load() {
        return new ServiceUrls(
                FrameworkConfig.requiredText(
                        "MIR.SERVICE.UI.URL"
                ),
                FrameworkConfig.requiredText(
                        "FILE.SERVICE.API.URL"
                ),
                FrameworkConfig.requiredText(
                        "MIR.SERVICE.API.URL"
                ),
                FrameworkConfig.requiredText(
                        "ADMINISTRATION.SERVICE.API.URL"
                ),
                FrameworkConfig.requiredText(
                        "SUBMISSION.SUPPORT.SERVICE.API.URL"
                ),
                FrameworkConfig.requiredText(
                        "PROFILE.SERVICE.API.URL"
                ),
                FrameworkConfig.requiredText(
                        "MIR.2.KIDS.SERVICE.API.URL"
                ),
                FrameworkConfig.requiredText(
                        "WIFE.SERVICE.API.URL"
                )
        );
    }
}