package com.leo.wardrobe.di

import android.content.Context
import com.leo.libs.store.FileMediaStore
import com.leo.libs.store.SnapshotStore
import com.leo.wardrobe.data.image.ImageFileStore
import com.leo.wardrobe.data.prefs.PrefsStore
import com.leo.wardrobe.data.repo.WardrobeRepositoryImpl
import com.leo.wardrobe.domain.model.WardrobeData
import com.leo.wardrobe.domain.repository.WardrobeRepository
import com.leo.wardrobe.domain.usecase.BuildOutfitPrompt
import com.leo.wardrobe.domain.usecase.PickRandomOutfit
import com.leo.wardrobe.export.OutfitImageComposer
import com.leo.wardrobe.export.ShareClipboard

/** 组合根：手动构造器装配（ADR-003）， specs/04-architecture.md */
class AppContainer(context: Context) {
    val snapshotStore: SnapshotStore<WardrobeData> = SnapshotStore(
        dir = context.filesDir,
        fileName = "wardrobe.json",
        serializer = WardrobeData.serializer(),
        default = { WardrobeData() },
        versionOf = { it.schemaVersion },
    )
    val imageStore: ImageFileStore = ImageFileStore(context, FileMediaStore(context.filesDir, "images"))
    val repository: WardrobeRepository = WardrobeRepositoryImpl(snapshotStore, imageStore)
    val prefs: PrefsStore = PrefsStore(context)
    val buildPrompt: BuildOutfitPrompt = BuildOutfitPrompt()
    val pickRandom: PickRandomOutfit = PickRandomOutfit()
    val imageComposer: OutfitImageComposer = OutfitImageComposer(imageStore)
    val share: ShareClipboard = ShareClipboard(context)
}
