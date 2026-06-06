val modId: String by project
val minecraftVersion: String = libs.versions.minecraft.get()
val neoforgeVersion: String = libs.versions.neoforge.platform.get()
val neoforgeLoaderVersion: String = libs.versions.neoforge.loader.get()
val architecturyVersion: String = libs.versions.architectury.get()

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)

    runs {
        create("data") {
            data()
            programArgs("--all")
            programArgs("--mod", modId)
            programArgs("--output", file("src/generated/resources/").absolutePath)
            programArgs("--existing", project(":common").file("src/main/resources").absolutePath)
            programArgs("--existing", file("src/main/resources").absolutePath)
        }
    }
}

repositories {
    maven("https://maven.neoforged.net/releases")
    // Lootr (optional integration) is published here.
    maven("https://maven.blamejared.com")
}

dependencies {
    neoForge(libs.neoforge.platform)
    modImplementation(libs.neoforge.architectury)

    // Lootr - OPTIONAL integration (the Lootr scanner module). compileOnly keeps it a true soft
    // dependency: not bundled in our jar and not required at runtime. modLocalRuntime loads it in the
    // dev runs (not published) so the integration is testable in-dev; comment it out to test the
    // no-Lootr path (inert module + red "Lootr mod required" tooltip + hidden recipe).
    modCompileOnly(libs.lootr)
    modLocalRuntime(libs.lootr)

    // JEI - dev-runtime only (recipe/item lookup in the dev client). Not a compile dependency and not
    // bundled in the released jar.
    modLocalRuntime("mezz.jei:jei-1.21.1-neoforge:19.27.0.340")
}

tasks {
    processResources {
        val properties = mapOf(
            "version" to project.version,
            "minecraftVersion" to minecraftVersion,
            "neoforgeVersion" to neoforgeVersion,
            "loaderVersion" to neoforgeLoaderVersion,
            "architecturyVersion" to architecturyVersion
        )
        inputs.properties(properties)
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(properties)
        }
    }

    remapJar {
        atAccessWideners.add("${modId}.accesswidener")
    }
}
