package dev.codex.remote

import android.content.SharedPreferences
import android.graphics.Typeface
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat

enum class GlassSkin(val label: String) { Smoke("烟灰玻璃"), Frost("极简磨砂"), Clear("清透玻璃") }
enum class GridEffect(val label: String) { Off("关闭"), Snake("随机光蛇"), Blocks("随机方块"), Rhythm("网格律动"), Random("随机格子"), Scan("扫光"), Breathe("呼吸"), Stars("星点") }
enum class GridPalette(val label: String, val dark: Long, val hue: Float) {
    Graphite("石墨黑",0xFF151516,210f), Silver("雾银灰",0xFF202123,210f),
    Teal("冷青",0xFF101B19,165f), Violet("紫灰",0xFF19161E,275f), Warm("暖灰",0xFF1D1915,35f)
}
enum class BodyFont(val label: String, val resource: Int?) {
    System("系统黑体",null), MaShan("马善政 · 饱满手写",R.font.mashanzheng),
    KuaiLe("站酷快乐体 · 圆润",R.font.zcoolkuaile), QingKe("站酷庆科黄油体",R.font.zcoolqingkehuangyou),
    XiaoWei("站酷小薇体",R.font.zcoolxiaowei), LongCang("龙藏体",R.font.longcang),
    ZhiMang("志莽行书",R.font.zhimangxing), LiuJian("刘建毛草",R.font.liujianmaocao),
    WenKai("霞鹜文楷 TC",R.font.lxgwwenkai), NotoSans("思源系黑体",R.font.notosanssc), NotoSerif("思源系宋体",R.font.notoserifsc)
}
data class TerminalSkin(
    val glass: GlassSkin = GlassSkin.Clear, val font: BodyFont = BodyFont.MaShan,
    val bright: Boolean = true, val textShadow: Boolean = true,
    val effect: GridEffect = GridEffect.Blocks, val palette: GridPalette = GridPalette.Graphite,
    val speed: Float = 1f, val brightness: Float = 1f, val idleStill: Boolean = false, val rainbow: Boolean = true,
)
private inline fun <reified T:Enum<T>> SharedPreferences.enum(key:String, fallback:T):T = enumValues<T>().find { it.name == getString(key,null) } ?: fallback
internal fun readTerminalSkin(p:SharedPreferences) = TerminalSkin(
    p.enum("skin_glass",GlassSkin.Clear),p.enum("skin_font",BodyFont.MaShan),p.getBoolean("skin_bright",true),p.getBoolean("skin_shadow",true),
    p.enum("skin_effect",GridEffect.Blocks),p.enum("skin_palette",GridPalette.Graphite),
    boundedMotion(p.getFloat("skin_speed",1f)),boundedMotion(p.getFloat("skin_brightness",1f)),p.getBoolean("skin_idle_still",false),p.getBoolean("skin_rainbow",true))
internal fun boundedMotion(value:Float) = if(value.isFinite()) value.coerceIn(.25f,2f) else 1f
internal fun gridPace(running:Boolean, idleStill:Boolean, motion:Boolean, speed:Float):Float =
    if(!motion) 0f else boundedMotion(speed) * if(running) 1.5f else if(idleStill) 0f else .25f
internal fun skinInk(s:TerminalSkin,night:Boolean)= if(night) Color(if(s.bright) 0xFFE1E8F0 else 0xFFB5C0CD) else Color(0xFF20242C)
private val bodyTypes=mutableMapOf<BodyFont,Typeface>()
internal fun configureSkinText(view:TextView, skin:TerminalSkin, night:Boolean) {
    configureChatText(view)
    view.typeface=bodyTypes.getOrPut(skin.font) { skin.font.resource?.let { ResourcesCompat.getFont(view.context,it) } ?: Typeface.create("sans-serif",Typeface.NORMAL) }
    view.setTextColor(skinInk(skin,night).toArgb())
    if(skin.textShadow && night) view.setShadowLayer(1.5f*view.resources.displayMetrics.density,0f,view.resources.displayMetrics.density,android.graphics.Color.argb(180,0,0,0)) else view.setShadowLayer(0f,0f,0f,0)
}

@Composable internal fun TerminalSkinSettings(prefs:SharedPreferences) {
    val skin=LocalChatAppearance.current.skin
    val p=LocalAppPalette.current
    Text("终端皮肤",color=p.ink,fontSize=16.sp,modifier=Modifier.padding(top=16.dp))
    SkinChoice("玻璃",GlassSkin.entries,skin.glass,{it.label}){prefs.edit().putString("skin_glass",it.name).apply()}
    SkinChoice("对话字体",BodyFont.entries,skin.font,{it.label}){prefs.edit().putString("skin_font",it.name).apply()}
    SkinChoice("背景配色",GridPalette.entries,skin.palette,{it.label}){prefs.edit().putString("skin_palette",it.name).apply()}
    SkinChoice("背景动效",GridEffect.entries,skin.effect,{it.label}){prefs.edit().putString("skin_effect",it.name).apply()}
    SkinToggle("灰白文字（关闭为深灰）",skin.bright){prefs.edit().putBoolean("skin_bright",it).apply()}
    SkinToggle("文字阴影",skin.textShadow){prefs.edit().putBoolean("skin_shadow",it).apply()}
    SkinToggle("彩色光格",skin.rainbow){prefs.edit().putBoolean("skin_rainbow",it).apply()}
    SkinToggle("停止或等待回答时保持静止",skin.idleStill){prefs.edit().putBoolean("skin_idle_still",it).apply()}
    Text("速度 · ${"%.2f".format(skin.speed)}×",color=p.ink,fontSize=13.sp)
    Slider(skin.speed,{prefs.edit().putFloat("skin_speed",it).apply()},valueRange=.25f..2f)
    Text("亮度 · ${(skin.brightness*100).toInt()}%",color=p.ink,fontSize=13.sp)
    Slider(skin.brightness,{prefs.edit().putFloat("skin_brightness",it).apply()},valueRange=.25f..2f)
    Text("运行速度 ×1.5；空闲慢速 ×0.25。后台、减少动画或系统关闭动画时停止。字体离线可用，代码保持等宽。",color=p.muted,fontSize=12.sp)
}
@Composable private fun SkinToggle(label:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),color=LocalAppPalette.current.ink,fontSize=13.sp)
        Switch(value,onChange)
    }
}
@Composable private fun <T> SkinChoice(label:String,values:List<T>,selected:T,name:(T)->String,onChange:(T)->Unit) {
    var open by remember { mutableStateOf(false) }
    Box { TextButton(onClick={open=true}) { Text("$label · ${name(selected)}",color=LocalAppPalette.current.ink) }
        DropdownMenu(open,{open=false}) { values.forEach { value -> DropdownMenuItem(text={Text(name(value)+(if(value==selected) " ✓" else ""))},onClick={onChange(value);open=false}) } }
    }
}
