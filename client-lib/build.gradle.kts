plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-jackson:3.0.0")
    implementation(libs.meshtastic.protobuf)

    testImplementation("ch.qos.logback:logback-classic:1.5.18")
    testImplementation("com.openai:openai-java:3.1.2")
    testImplementation(kotlin("test"))
}
