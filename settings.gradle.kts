pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "bunny-app"
include(":app")
// docs/phase-1.md § 3: the packs join only on request, never because an archive is on disk.
if (providers
        .gradleProperty("mapPacks")
        .map {
            it.isEmpty() || it.toBoolean()
        }.getOrElse(false)
) {
    file("scripts/regions").listFiles { file -> file.extension == "poly" }!!.forEach { polygon ->
        val region = polygon.nameWithoutExtension
        val archive = file("region_$region/src/main/assets/$region.pmtiles")
        check(archive.exists()) {
            "-PmapPacks needs $archive. Build it with `python3 scripts/build-map-data.py`."
        }
        include(":region_$region")
        project(":region_$region").buildFileName = "../gradle/region-pack.gradle.kts"
    }
    listOf("relief", "contours").forEach { name ->
        check(file("relief/src/main/assets/$name.pmtiles").exists()) {
            "-PmapPacks needs relief/src/main/assets/$name.pmtiles. Fetch it with `python3 scripts/fetch-relief.py`."
        }
    }
    include(":relief")
    project(":relief").buildFileName = "../gradle/region-pack.gradle.kts"
    listOf("trails.network", "places.sqlite").forEach { name ->
        check(file("map-data/base-assets/$name").exists()) {
            "-PmapPacks needs map-data/base-assets/$name. Build it with `python3 scripts/build-map-data.py`."
        }
    }
}
