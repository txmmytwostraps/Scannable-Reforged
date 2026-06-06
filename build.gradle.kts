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

    // Lootr — OPTIONAL integration (the Lootr scanner module). Lootr's 26.1.2 build is published to
    // CurseForge only (not the BlameJared maven), so it's referenced as a local jar. compileOnly keeps
    // it a true soft dependency: not bundled in our jar and not required at runtime. When Lootr is
    // absent the Lootr module is inert (its `lootr:containers` tag is empty) and none of Lootr's
    // classes are ever loaded (every reference is gated behind a ModList check).
    compileOnly(files("libs/lootr-neoforge-26.1.2-1.22.36.109.jar"))
    // ...and load it in the dev runs (like JEI above) so the integration actually activates in-dev for
    // testing. runtimeOnly is NOT bundled into the release jar. Comment this out to test the no-Lootr
    // path (inert module + red "Lootr mod required" tooltip + hidden recipe).
    runtimeOnly(files("libs/lootr-neoforge-26.1.2-1.22.36.109.jar"))
}

neoForge {
    version = "26.1.2.71"

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            client()
        }
        // 26.1 splits datagen into client (resource pack: item models) and server (data pack:
        // recipes, tags) runs, matching GatherDataEvent.Client / .Server.
        register("clientData") {
            clientData()
            programArguments.addAll("--mod", modId)
            programArguments.addAll("--output", file("src/generated/resources").absolutePath)
            programArguments.addAll("--existing", file("src/main/resources").absolutePath)
        }
        register("serverData") {
            serverData()
            programArguments.addAll("--mod", modId)
            programArguments.addAll("--output", file("src/generated/resources").absolutePath)
            programArguments.addAll("--existing", file("src/main/resources").absolutePath)
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

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
        "neoforgeVersion" to "26.1.2.71",
        "loaderVersion" to "1",
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
    filteringCharset = "UTF-8"
}
