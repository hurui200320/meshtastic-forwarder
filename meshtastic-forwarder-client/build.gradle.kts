plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-jackson:3.0.0")
    implementation(libs.meshtastic.protobuf)

    testImplementation(kotlin("test"))
}
