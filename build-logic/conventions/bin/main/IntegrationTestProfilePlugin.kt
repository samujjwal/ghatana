/**
 * Precompiled [integration-test-profile.gradle.kts][Integration_test_profile_gradle] script plugin.
 *
 * @see Integration_test_profile_gradle
 */
public
class IntegrationTestProfilePlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Integration_test_profile_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
