package com.leo.wardrobe.di

import android.content.Context
import com.leo.wardrobe.data.image.ImageFileStore
import com.leo.wardrobe.data.json.JsonFileStore
import com.leo.wardrobe.data.prefs.PrefsStore
import com.leo.wardrobe.data.repo.WardrobeRepositoryImpl
import com.leo.wardrobe.domain.repository.WardrobeRepository
import com.leo.wardrobe.domain.usecase.BuildOutfitPrompt
import com.leo.wardrobe.domain.usecase.PickRandomOutfit
import com.leo.wardrobe.export.OutfitImageComposer
import com.leo.wardrobe.export.ShareClipboard
import java.io.File

/** 组合根：手动构造器装配（ADR-003）， specs/04-architecture.md */
class AppContainer(context: Context) {
    val jsonStore: JsonFileStore = JsonFileStore(context.filesDir ?: File("."))
    val imageStore: ImageFileStore = ImageFileStore(context)
    val repository: WardrobeRepository = WardrobeRepositoryImpl(jsonStore, imageStore)
    val prefs: PrefsStore = PrefsStore(context)
    val buildPrompt: BuildOutfitPrompt = BuildOutfitPrompt()
    val pickRandom: PickRandomOutfit = PickRandomOutfit()
    val imageComposer: OutfitImageComposer = OutfitImageComposer(imageStore)
    val share: ShareClipboard = ShareClipboard(context)
}
