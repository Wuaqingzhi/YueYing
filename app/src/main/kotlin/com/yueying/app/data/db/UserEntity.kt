/*
 * YueYing (月影) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 月影 (YueYing) Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.yueying.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 月影应用账号（v2.0 新增）：注册/登录使用，密码以 PBKDF2 哈希落库。
 */
@Entity(tableName = "yy_user")
data class UserEntity(
    @PrimaryKey
    val email: String,
    val passHash: String,
    val salt: String,
    val createdAt: Long = System.currentTimeMillis()
)
