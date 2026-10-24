/*
 * YueYing (月影) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
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

package com.yueying.app.util

import android.os.Environment
import java.io.File

/**
 * 下载目录的公共口径（内置分片下载器与 Gopeed 引擎共用，别各自写死一份）。
 *
 * 两套下载器的落盘方式完全不同 —— 内置的走 MediaStore/SAF（写 `content://`，Android 10+ 免存储权限），
 * 引擎是原生核心、**只能按真实文件路径写** —— 但**默认目录是同一个**：
 * 系统公共存储根目录下新建的「月影解析」文件夹，即 `/storage/emulated/0/月影解析/`。
 */
object StorageDirs {

    /** 默认下载目录的文件夹名（显示名，也是 MediaStore RELATIVE_PATH 的根） */
    const val DEFAULT_DIR_NAME = "月影解析"

    /**
     * 默认下载目录：系统公共存储根目录下的「月影解析」文件夹。
     *
     * 内置下载器落点就是这里（MediaStore 的 `RELATIVE_PATH = 月影解析`）；引擎直接拿绝对路径。
     * 注意 `getExternalStorageDirectory` 已废弃（分区存储时代推荐 MediaStore），
     * 但引擎本来就必须用真实路径，这里没有替代品；Android 10+ 内置下载器走 MediaStore，
     * 只有引擎（需「所有文件访问」权限）走这里的真实路径。
     */
    @Suppress("DEPRECATION")
    fun defaultDownloadDir(): File =
        File(Environment.getExternalStorageDirectory(), DEFAULT_DIR_NAME)

    /** 默认下载目录的绝对路径（UI 展示用） */
    fun defaultDownloadPath(): String = defaultDownloadDir().absolutePath
}
