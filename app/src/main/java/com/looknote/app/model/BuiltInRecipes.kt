package com.looknote.app.model

import com.looknote.app.model.LookParam.CLARITY
import com.looknote.app.model.LookParam.CONTRAST
import com.looknote.app.model.LookParam.FADE
import com.looknote.app.model.LookParam.HIGHLIGHTS
import com.looknote.app.model.LookParam.SATURATION
import com.looknote.app.model.LookParam.SHADOWS
import com.looknote.app.model.LookParam.SHARPNESS
import com.looknote.app.model.LookParam.SHARPNESS_RANGE

/**
 * Bo cong thuc mau khoi dau do LookNote tu soan (khong sao chep tu ung dung khac).
 * Nguoi dung co the sua, nhan ban hoac xoa.
 */
object BuiltInRecipes {
    private fun r(
        id: String,
        name: String,
        look: CreativeLook,
        vararg p: Pair<LookParam, Int>,
        wb: WhiteBalance = WhiteBalance.AUTO,
        kelvin: Int = 5500,
        ab: Double = 0.0,
        gm: Double = 0.0,
        dro: String = "",
        note: String = "",
    ) = Recipe(
        id = "builtin-$id",
        name = name,
        look = look,
        params = p.toMap(),
        wb = wb,
        kelvin = kelvin,
        wbAB = ab,
        wbGM = gm,
        dro = dro,
        notes = note,
        builtIn = true,
        createdAt = 0L,
        updatedAt = 0L,
    )

    val all: List<Recipe> = listOf(
        r("warm-film", "Chiều phim ấm", CreativeLook.FL,
            CONTRAST to 1, HIGHLIGHTS to -2, SHADOWS to 1, FADE to 2, SATURATION to -1,
            SHARPNESS to 0, SHARPNESS_RANGE to 3, CLARITY to -1,
            wb = WhiteBalance.DAYLIGHT, ab = 2.0, gm = 0.5, dro = "Auto",
            note = "Hợp nắng chiều, đường phố, chân dung ngoài trời."),
        r("quiet-morning", "Sáng sớm trong trẻo", CreativeLook.SH,
            CONTRAST to -1, HIGHLIGHTS to -1, SHADOWS to 2, FADE to 1, SATURATION to -2,
            SHARPNESS to 0, SHARPNESS_RANGE to 3, CLARITY to 0,
            wb = WhiteBalance.AUTO, ab = -0.5, gm = 0.25,
            note = "Tông sáng, nhẹ; hợp quán cà phê, cửa sổ, đồ vật."),
        r("street-contrast", "Phố tương phản", CreativeLook.VV2,
            CONTRAST to 3, HIGHLIGHTS to -2, SHADOWS to -2, FADE to 0, SATURATION to -2,
            SHARPNESS to 1, SHARPNESS_RANGE to 2, CLARITY to 2,
            wb = WhiteBalance.DAYLIGHT, ab = 1.0,
            note = "Màu đậm, bóng sâu; hợp nắng gắt giữa trưa."),
        r("faded-instant", "Ảnh lấy liền phai", CreativeLook.IN,
            CONTRAST to -2, HIGHLIGHTS to -1, SHADOWS to 1, FADE to 4, SATURATION to -1,
            SHARPNESS to -1, SHARPNESS_RANGE to 3, CLARITY to -2,
            wb = WhiteBalance.CLOUDY, gm = -0.5,
            note = "Phẳng, mềm, hơi hoài cổ."),
        r("teal-dusk", "Hoàng hôn xanh ngọc", CreativeLook.FL,
            CONTRAST to 2, HIGHLIGHTS to -3, SHADOWS to 0, FADE to 1, SATURATION to 1,
            SHARPNESS to 0, SHARPNESS_RANGE to 3, CLARITY to 1,
            wb = WhiteBalance.KELVIN, kelvin = 4800, ab = -1.0, gm = -0.5,
            note = "Giờ xanh, đèn đường; dùng nhiệt độ màu cố định."),
        r("soft-portrait", "Chân dung mềm", CreativeLook.PT,
            CONTRAST to -1, HIGHLIGHTS to -2, SHADOWS to 1, FADE to 1, SATURATION to 0,
            SHARPNESS to -2, SHARPNESS_RANGE to 4, CLARITY to -3,
            wb = WhiteBalance.AUTO, ab = 1.0, gm = -0.25,
            note = "Da mịn, sáng; hợp ánh sáng cửa sổ."),
        r("deep-green", "Rừng lá đậm", CreativeLook.NT,
            CONTRAST to 2, HIGHLIGHTS to -1, SHADOWS to -1, FADE to 0, SATURATION to 2,
            SHARPNESS to 1, SHARPNESS_RANGE to 3, CLARITY to 2,
            wb = WhiteBalance.SHADE, ab = -1.0, gm = 1.0,
            note = "Phong cảnh cây lá, trời âm u."),
        r("silver-bw", "Đen trắng bạc", CreativeLook.BW,
            CONTRAST to 3, HIGHLIGHTS to -1, SHADOWS to -2, FADE to 0, SATURATION to 0,
            SHARPNESS to 2, SHARPNESS_RANGE to 2, CLARITY to 3,
            dro = "Lv2",
            note = "Đen trắng nhiều chi tiết, hợp kiến trúc và phố."),
        r("matte-bw", "Đen trắng lì", CreativeLook.BW,
            CONTRAST to -1, HIGHLIGHTS to -2, SHADOWS to 2, FADE to 5, SATURATION to 0,
            SHARPNESS to -1, SHARPNESS_RANGE to 3, CLARITY to -1,
            note = "Đen trắng phẳng, dịu."),
        r("old-sepia", "Nâu kỷ niệm", CreativeLook.SE,
            CONTRAST to 0, HIGHLIGHTS to -2, SHADOWS to 1, FADE to 3, SATURATION to 0,
            SHARPNESS to -1, SHARPNESS_RANGE to 3, CLARITY to -1,
            note = "Tông nâu cổ điển."),
    )
}
