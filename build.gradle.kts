plugins {
    // Apply plugins with explicit versions at the root so module files can use them via "id(...)"
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
