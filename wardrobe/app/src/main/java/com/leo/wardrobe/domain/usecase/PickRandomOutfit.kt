package com.leo.wardrobe.domain.usecase

import com.leo.wardrobe.domain.model.Item
import com.leo.wardrobe.domain.model.WardrobeCategory
import kotlin.random.Random

/** 随机一套（US-05）：每个非空品类独立随机取一件。 */
class PickRandomOutfit(private val random: Random = Random.Default) {
    operator fun invoke(items: List<Item>): Map<WardrobeCategory, Item> =
        items.groupBy { it.category }.mapValues { (_, group) -> group.random(random) }
}
