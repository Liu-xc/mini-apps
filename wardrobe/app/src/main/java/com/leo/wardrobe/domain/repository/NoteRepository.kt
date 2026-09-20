package com.leo.wardrobe.domain.repository

import com.leo.wardrobe.domain.model.Note
import com.leo.wardrobe.domain.model.NoteParent

/** 笔记域仓储（it-021 接口拆分） */
interface NoteRepository {
    suspend fun addNote(parentType: NoteParent, parentId: String, text: String): Note
    suspend fun deleteNote(id: String)
}
