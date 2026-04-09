/**
 * ${MODULE_NAME}
 *
 * @doc.type build-script
 * @doc.purpose ${MODULE_PURPOSE}
 * @doc.layer ${MODULE_LAYER}
 * @doc.pattern ${MODULE_PATTERN}
 */
plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.testing-conventions")
    ${SPECIALIZED_PLUGINS}
}

group = "${MODULE_GROUP}"
version = rootProject.version
description = "${MODULE_DESCRIPTION}"

dependencies {
    ${DEPENDENCIES}
}
