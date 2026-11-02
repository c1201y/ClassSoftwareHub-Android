package com.classsoftwarehub.android.util.units

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

/** 单位换算的类别 */
enum class UnitCategory(val label: String) {
    LENGTH("长度"),
    MASS("质量"),
    AREA("面积"),
    VOLUME("容积"),
    TEMPERATURE("温度"),
    DATA("数据"),
    TIME("时间"),
    SPEED("速度"),
}

/**
 * 一个单位：[toBase] 换到该类别的基准单位，[fromBase] 从基准单位换回。
 * 温度这种带偏移的量用自定义函数，其余都是比例换算。
 */
class UnitDef(
    val id: String,
    val label: String,
    private val toBase: (Double) -> Double,
    private val fromBase: (Double) -> Double,
) {
    fun toBaseValue(value: Double): Double = toBase(value)
    fun fromBaseValue(value: Double): Double = fromBase(value)
}

private fun linear(id: String, label: String, factor: Double) = UnitDef(
    id = id,
    label = label,
    toBase = { it * factor },
    fromBase = { it / factor },
)

object UnitConverter {

    /** 每个类别的单位表（第一个是基准单位） */
    val units: Map<UnitCategory, List<UnitDef>> = linkedMapOf(
        UnitCategory.LENGTH to listOf(
            linear("m", "米", 1.0),
            linear("km", "千米", 1000.0),
            linear("cm", "厘米", 0.01),
            linear("mm", "毫米", 0.001),
            linear("inch", "英寸", 0.0254),
            linear("ft", "英尺", 0.3048),
            linear("li", "里", 500.0),
        ),
        UnitCategory.MASS to listOf(
            linear("kg", "千克", 1.0),
            linear("g", "克", 0.001),
            linear("t", "吨", 1000.0),
            linear("jin", "斤", 0.5),
            linear("lb", "磅", 0.45359237),
            linear("oz", "盎司", 0.028349523125),
        ),
        UnitCategory.AREA to listOf(
            linear("m2", "平方米", 1.0),
            linear("cm2", "平方厘米", 0.0001),
            linear("km2", "平方千米", 1_000_000.0),
            linear("ha", "公顷", 10_000.0),
            linear("mu", "亩", 2000.0 / 3.0),
            linear("ft2", "平方英尺", 0.09290304),
        ),
        UnitCategory.VOLUME to listOf(
            linear("l", "升", 1.0),
            linear("ml", "毫升", 0.001),
            linear("m3", "立方米", 1000.0),
            linear("gal", "加仑（美）", 3.785411784),
        ),
        UnitCategory.TEMPERATURE to listOf(
            linear("c", "摄氏度 ℃", 1.0),
            UnitDef("f", "华氏度 ℉", { (it - 32.0) * 5.0 / 9.0 }, { it * 9.0 / 5.0 + 32.0 }),
            UnitDef("k", "开尔文 K", { it - 273.15 }, { it + 273.15 }),
        ),
        UnitCategory.DATA to listOf(
            linear("b", "字节 B", 1.0),
            linear("kb", "千字节 KB", 1024.0),
            linear("mb", "兆字节 MB", 1024.0 * 1024.0),
            linear("gb", "吉字节 GB", 1024.0 * 1024.0 * 1024.0),
            linear("tb", "太字节 TB", 1024.0 * 1024.0 * 1024.0 * 1024.0),
            linear("bit", "位 bit", 0.125),
        ),
        UnitCategory.TIME to listOf(
            linear("s", "秒", 1.0),
            linear("min", "分钟", 60.0),
            linear("h", "小时", 3600.0),
            linear("d", "天", 86_400.0),
            linear("week", "周", 604_800.0),
        ),
        UnitCategory.SPEED to listOf(
            linear("mps", "米/秒", 1.0),
            linear("kmh", "千米/时", 1.0 / 3.6),
            linear("mph", "英里/时", 0.44704),
            linear("knot", "节", 0.5144444444444445),
        ),
    )

    fun unitsOf(category: UnitCategory): List<UnitDef> =
        units[category] ?: units.getValue(UnitCategory.LENGTH)

    fun defaultFrom(category: UnitCategory): UnitDef =
        unitsOf(category).first()

    fun defaultTo(category: UnitCategory): UnitDef {
        val list = unitsOf(category)
        val preferred = when (category) {
            UnitCategory.DATA -> "mb"
            UnitCategory.TIME -> "h"
            UnitCategory.SPEED -> "kmh"
            else -> return list.getOrElse(1) { list.first() }
        }
        return list.firstOrNull { it.id == preferred } ?: list.first()
    }

    fun find(category: UnitCategory, id: String): UnitDef? =
        unitsOf(category).firstOrNull { it.id == id }

    fun convert(category: UnitCategory, fromId: String, toId: String, value: Double): Double? {
        val from = find(category, fromId) ?: return null
        val to = find(category, toId) ?: return null
        return to.fromBaseValue(from.toBaseValue(value))
    }

    /** 六位有效小数、去掉末尾 0；极端量级退回科学计数法 */
    fun formatNumber(value: Double): String {
        if (!value.isFinite()) return "—"
        if (value == 0.0) return "0"
        val abs = abs(value)
        if (abs >= 1e12 || abs < 1e-6) {
            return String.format(Locale.US, "%.6e", value)
        }
        return BigDecimal(value)
            .setScale(6, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }
}
