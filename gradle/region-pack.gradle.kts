plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName.set(project.name)
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}
