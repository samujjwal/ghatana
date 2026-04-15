/**
 * Precompiled [finance-domain-module.gradle.kts][Finance_domain_module_gradle] script plugin.
 *
 * @see Finance_domain_module_gradle
 */
public
class FinanceDomainModulePlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Finance_domain_module_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
