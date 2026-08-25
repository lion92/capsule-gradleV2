// Root consommateur — pattern article 0124 « Plugin Indépendant + Racine Consommateur ».
// Le root ne build pas le plugin. Il le consomme via mavenLocal comme un client externe.
// Le plugin vit dans capsule-plugin/ (build indépendant : wrapper, settings, catalogue).
//
// Workflow :
//   cd capsule-plugin && ./gradlew publishToMavenLocal && cd ..
//   ./gradlew tasks
//
// Zéro include(), zéro includeBuild(). Le root est un exemple de consommation réel.

plugins {
    alias(libs.plugins.capsule)
}

repositories {
    mavenLocal()
    mavenCentral()
}

// Dogfood : exercer le plugin capsule depuis la racine.
// capsule {
//     configPath = file("capsule-context.yml").absolutePath
// }
