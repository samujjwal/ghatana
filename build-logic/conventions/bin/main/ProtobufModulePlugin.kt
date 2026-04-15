/**
 * Precompiled [protobuf-module.gradle.kts][Protobuf_module_gradle] script plugin.
 *
 * @see Protobuf_module_gradle
 */
public
class ProtobufModulePlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Protobuf_module_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
