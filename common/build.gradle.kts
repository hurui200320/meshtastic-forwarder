plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.meshtastic.protobuf)

    testImplementation(kotlin("test"))
}
