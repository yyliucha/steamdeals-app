package cn.yyliucha.steamdeals.ui.components

/**
 * CM-4（docs/APP-PARITY.md §3）：复制游戏名，与 Web 完全一致
 * （frontend/src/utils/gameNames.js nameCopyText，AO 批口径）：
 * 中英文两行（`中文\n英文`）；英文缺失或与中文相同 → 回退单行；全缺 → 空串。
 */
fun nameCopyText(name: String?, nameEn: String?): String {
    val zh = name?.trim().orEmpty()
    val en = nameEn?.trim().orEmpty()
    if (en.isEmpty() || en == zh) return zh.ifEmpty { en }
    if (zh.isEmpty()) return en
    return "$zh\n$en"
}
