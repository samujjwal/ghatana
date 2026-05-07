plugins {
    base
}

val aepGradleModules = listOf(
    "operator-contracts",
    "central-runtime",
    "engine",
    "registry",
    "analytics",
    "security",
    "event-bridge",
    "agent-runtime",
    "api",
    "scaling",
    "observability",
    "orchestrator",
    "server",
    "identity",
    "compliance",
    "kernel-bridge"
)

tasks.register("test") {
    group = "verification"
    description = "Runs tests for all Gradle-backed AEP modules now hosted under Data Cloud."
    dependsOn(aepGradleModules.map { ":products:data-cloud:planes:action:$it:test" })
}

tasks.named("check") {
    dependsOn("test")
}

tasks.named("build") {
    dependsOn(aepGradleModules.map { ":products:data-cloud:planes:action:$it:build" })
}
