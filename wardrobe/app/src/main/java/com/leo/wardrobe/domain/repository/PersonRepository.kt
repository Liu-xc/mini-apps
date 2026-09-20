package com.leo.wardrobe.domain.repository

import com.leo.wardrobe.domain.model.Person

/**
 * 角色域仓储（it-021 接口拆分：WardrobeRepository 门面继承本接口）。
 * 实现负责：内存快照 SSOT + 原子持久化 + 数据不变量（specs/03-data-model.md 末节）。
 */
interface PersonRepository {
    /** 空库时创建默认角色「我」并返回（幂等） */
    suspend fun ensureDefaultPerson(): Person

    suspend fun addPerson(name: String, emoji: String): Person
    suspend fun updatePerson(id: String, name: String, emoji: String)

    /** it-017：设置/更换形象参考照（photoFile 为已导入的文件名；换照时旧文件物理删除） */
    suspend fun setPersonRefPhoto(id: String, photoFile: String)

    /** it-017：移除形象参考照（文件物理删除；未设置时为 no-op） */
    suspend fun removePersonRefPhoto(id: String)

    /** 级联：删其全部衣物/组合/笔记/打卡与心愿域 */
    suspend fun deletePerson(id: String)
}
