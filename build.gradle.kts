plugins {
    id("net.neoforged.moddev") version "2.0.141"
}

val modId = "scannable"

group = "li.cil.scannable"
version = (findProperty("modVersion") as String?) ?: "0.0.0"
base.archivesName = "ScannableReforged-MC26.1.2-neoforge"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    // Just Enough Items (dev/test only — recipe + item lookup in the runClient).
    maven("https://maven.blamejared.com")
}

dependencies {
    // JEI is loaded only in the dev runs (not a compile dependency, not bundled in the jar). Lets us
    // verify the 26.1 module recipes show up correctly in-game.
    runtimeOnly("mezz.jei:jei-26.1.2-neoforge:29.6.2.31")
}

neoForge {
    version = "26.1.2.73"

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            client()
        }
        register("data") {
            data()
            programArgument("--all")
            programArguments.addAll("--mod", modId)
            programArguments.addAll("--output", file("src/generated/resources").absolutePath)
            programArguments.addAll("--existing", file("src/main/resources").absolutePath)
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

// The datagen providers target the 1.21.1 datagen API; excluded from compilation pending
// the 26.1 datagen-API migration. Generated resources (src/generated) are already committed.
sourceSets.main.get().java.exclude("li/cil/scannable/data/**")

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${modId}" }
    }
}

tasks.named<ProcessResources>("processResources") {
    // src/main/resources (committed data) and src/generated/resources (datagen output) can carry
    // the same model/recipe/tag JSONs; prefer the committed copy and skip the generated duplicate.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    val props = mapOf(
        "version" to version.toString(),
        "minecraftVersion" to "26.1.2",
        "neoforgeVersion" to "26.1.2.73",
        "loaderVersion" to "1",
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
    filteringCharset = "UTF-8"
}
