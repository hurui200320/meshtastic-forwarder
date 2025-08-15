plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation("org.eclipse.jetty.websocket:jetty-websocket-jetty-client:12.0.24")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-jackson:3.0.0")
    implementation(libs.meshtastic.protobuf)

    testImplementation(kotlin("test"))
}
