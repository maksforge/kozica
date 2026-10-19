plugins {
    alias(libs.plugins.android.asset.pack)
}

val archive = file("src/main/assets/tatry.pmtiles")
check(archive.exists()) {
    "-PmapPacks needs $archive. Build it with `python3 scripts/build-map-data.py`."
}

assetPack {
    packName.set("region_tatry")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}
