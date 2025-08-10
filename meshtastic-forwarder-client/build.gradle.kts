plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation("org.eclipse.jetty.websocket:jetty-websocket-jetty-client:12.0.24")
    implementation(libs.meshtastic.protobuf)

    testImplementation(kotlin("test"))
}
