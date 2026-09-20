package com.leo.eats.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.leo.eats.domain.model.PlaceCategory
import com.leo.eats.domain.model.PlaceKind
import com.leo.eats.domain.usecase.SpinFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("eats_prefs")

/**
 * 转盘过滤配置（specs/04-architecture.md 状态与导航）：分类/类型/忌口/排除天数/只抽愿望跨启动保留。
 * categories / kinds 为空集 = 全选。
 */
class SpinPrefsStore(private val context: Context) {

    private val keyCategories = stringSetPreferencesKey("spin_categories")
    private val keyKinds = stringSetPreferencesKey("spin_kinds")
    private val keyExcludedTags = stringSetPreferencesKey("spin_excluded_tags")
    private val keyExcludeRecent = booleanPreferencesKey("spin_exclude_recent")
    private val keyRecentDays = intPreferencesKey("spin_recent_days")
    private val keyWishOnly = booleanPreferencesKey("spin_wish_only")

    val config: Flow<SpinFilter> = context.store.data.map { prefs ->
        val categories = prefs[keyCategories].orEmpty()
            .mapNotNull { name -> PlaceCategory.entries.firstOrNull { it.name == name } }
            .toSet()
        val kinds = prefs[keyKinds].orEmpty()
            .mapNotNull { name -> PlaceKind.entries.firstOrNull { it.name == name } }
            .toSet()
        SpinFilter(
            categories = categories.ifEmpty { PlaceCategory.entries.toSet() },
            kinds = kinds.ifEmpty { PlaceKind.entries.toSet() },
            excludedTags = prefs[keyExcludedTags].orEmpty(),
            excludeRecentDays = if (prefs[keyExcludeRecent] != false) prefs[keyRecentDays] ?: 14 else null,
            wishOnly = prefs[keyWishOnly] == true,
        )
    }

    suspend fun setCategories(categories: Set<PlaceCategory>) {
        context.store.edit { it[keyCategories] = categories.map { c -> c.name }.toSet() }
    }

    suspend fun setKinds(kinds: Set<PlaceKind>) {
        context.store.edit { it[keyKinds] = kinds.map { k -> k.name }.toSet() }
    }

    suspend fun setExcludedTags(tags: Set<String>) {
        context.store.edit { it[keyExcludedTags] = tags }
    }

    suspend fun setExcludeRecent(enabled: Boolean, days: Int) {
        context.store.edit {
            it[keyExcludeRecent] = enabled
            it[keyRecentDays] = days
        }
    }

    suspend fun setWishOnly(enabled: Boolean) {
        context.store.edit { it[keyWishOnly] = enabled }
    }
}
