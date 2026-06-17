package com.verdantgem.ledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ICON_MAP: Map<String, ImageVector> = mapOf(
    "出行交通" to Icons.Filled.Commute,
    "打车" to Icons.Filled.LocalTaxi,
    "公共交通" to Icons.Filled.DirectionsBus,
    "飞机" to Icons.Filled.Flight,
    "火车" to Icons.Filled.Train,
    "加油" to Icons.Filled.LocalGasStation,
    "购物消费" to Icons.Filled.ShoppingCart,
    "办公用品" to Icons.Filled.Description,
    "宠物用品" to Icons.Filled.Pets,
    "服饰运动" to Icons.Filled.Checkroom,
    "个护美妆" to Icons.Filled.Face,
    "配饰腕表" to Icons.Filled.Watch,
    "日常家居" to Icons.Filled.Chair,
    "生活电器" to Icons.Filled.Power,
    "手机数码" to Icons.Filled.Smartphone,
    "虚拟充值" to Icons.Filled.Redeem,
    "装修装饰" to Icons.Filled.FormatPaint,
    "健康医疗" to Icons.Filled.LocalHospital,
    "买药" to Icons.Filled.Medication,
    "医院" to Icons.Filled.LocalHospital,
    "滋补保健" to Icons.Filled.Spa,
    "居家生活" to Icons.Filled.Home,
    "电费" to Icons.Filled.ElectricBolt,
    "房租还贷" to Icons.Filled.Apartment,
    "话费宽带" to Icons.Filled.Wifi,
    "家政清洁" to Icons.Filled.CleaningServices,
    "水费" to Icons.Filled.WaterDrop,
    "其他" to Icons.Filled.MoreHoriz,
    "慈善捐助" to Icons.Filled.VolunteerActivism,
    "杂项" to Icons.Filled.Category,
    "食品餐饮" to Icons.Filled.Restaurant,
    "粮油调味" to Icons.Filled.LunchDining,
    "请客吃饭" to Icons.Filled.DinnerDining,
    "生鲜食品" to Icons.Filled.Egg,
    "晚餐" to Icons.Filled.DinnerDining,
    "午餐" to Icons.Filled.LunchDining,
    "休闲零食" to Icons.Filled.BakeryDining,
    "夜宵" to Icons.Filled.Nightlight,
    "饮料酒水" to Icons.Filled.LocalBar,
    "早餐" to Icons.Filled.BreakfastDining,
    "送礼人情" to Icons.Filled.CardGiftcard,
    "红包" to Icons.Filled.Redeem,
    "礼物" to Icons.Filled.CardGiftcard,
    "文化教育" to Icons.Filled.School,
    "培训考试" to Icons.Filled.Quiz,
    "书报杂志" to Icons.AutoMirrored.Filled.MenuBook,
    "学费" to Icons.Filled.School,
    "休闲娱乐" to Icons.Filled.Attractions,
    "电影唱歌" to Icons.Filled.Movie,
    "旅游度假" to Icons.Filled.FlightTakeoff,
    "棋牌桌游" to Icons.Filled.Casino,
    "游戏" to Icons.Filled.SportsEsports,
    "运动健身" to Icons.Filled.FitnessCenter,
    "足浴按摩" to Icons.Filled.Spa,
    "快递" to Icons.Filled.LocalShipping,
    "收入" to Icons.Filled.AccountBalanceWallet,
    "其他收入" to Icons.Filled.AccountBalance,
    "工资" to Icons.Filled.Payments,
    "奖金" to Icons.Filled.EmojiEvents,
    "报销" to Icons.Filled.Receipt,
    "补贴" to Icons.Filled.Savings,
    "二手闲置" to Icons.Filled.Storefront,
)

@Composable
fun CategoryIcon(
    icon: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val imageVector = ICON_MAP[icon]
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        if (imageVector != null && icon != "default_icon") {
            Icon(
                imageVector = imageVector,
                contentDescription = name,
                tint = tint,
                modifier = Modifier.size(size * 0.6f)
            )
        } else {
            Text(
                text = name.ifBlank { "?" }.take(1),
                color = tint,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp
            )
        }
    }
}
