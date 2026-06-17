package com.verdantgem.ledger.data.repository

import com.verdantgem.ledger.data.model.Category

object DefaultCategories {
    fun getAll(): List<Category> = listOf(
        Category(name = "出行交通", icon = "出行交通", isIncome = false),
        Category(name = "购物消费", icon = "购物消费", isIncome = false),
        Category(name = "健康医疗", icon = "健康医疗", isIncome = false),
        Category(name = "居家生活", icon = "居家生活", isIncome = false),
        Category(name = "其他", icon = "其他", isIncome = false),
        Category(name = "食品餐饮", icon = "食品餐饮", isIncome = false),
        Category(name = "送礼人情", icon = "送礼人情", isIncome = false),
        Category(name = "文化教育", icon = "文化教育", isIncome = false),
        Category(name = "休闲娱乐", icon = "休闲娱乐", isIncome = false),
        Category(name = "快递", icon = "快递", isIncome = false, prompts = "快递"),
        Category(name = "收入", icon = "收入", isIncome = true),

        Category(name = "打车", icon = "打车", parentName = "出行交通", prompts = "打车", isIncome = false),
        Category(name = "公共交通", icon = "公共交通", parentName = "出行交通", prompts = "公交,地铁", isIncome = false),
        Category(name = "飞机", icon = "飞机", parentName = "出行交通", prompts = "机票", isIncome = false),
        Category(name = "火车", icon = "火车", parentName = "出行交通", prompts = "火车,高铁,动车,普速,12306", isIncome = false),
        Category(name = "加油", icon = "加油", parentName = "出行交通", prompts = "加油,汽油,加油站", isIncome = false),

        Category(name = "办公用品", icon = "办公用品", parentName = "购物消费", prompts = "办公,文具,打印", isIncome = false),
        Category(name = "宠物用品", icon = "宠物用品", parentName = "购物消费", prompts = "宠物,猫粮,狗粮,猫砂", isIncome = false),
        Category(name = "服饰运动", icon = "服饰运动", parentName = "购物消费", prompts = "衣服,鞋子", isIncome = false),
        Category(name = "个护美妆", icon = "个护美妆", parentName = "购物消费", prompts = "洗发水,沐浴露,洗面奶,护肤品,化妆品", isIncome = false),
        Category(name = "配饰腕表", icon = "配饰腕表", parentName = "购物消费", prompts = "手表,配饰,首饰,项链", isIncome = false),
        Category(name = "日常家居", icon = "日常家居", parentName = "购物消费", prompts = "家居,日用,收纳,厨房用品", isIncome = false),
        Category(name = "生活电器", icon = "生活电器", parentName = "购物消费", prompts = "电器,冰箱,洗衣机,电饭煲", isIncome = false),
        Category(name = "手机数码", icon = "手机数码", parentName = "购物消费", prompts = "手机,数码,充电器,耳机,电脑", isIncome = false),
        Category(name = "虚拟充值", icon = "虚拟充值", parentName = "购物消费", prompts = "会员", isIncome = false),
        Category(name = "装修装饰", icon = "装修装饰", parentName = "购物消费", prompts = "装修,装饰,家具,灯具", isIncome = false),

        Category(name = "买药", icon = "买药", parentName = "健康医疗", prompts = "买药,药品", isIncome = false),
        Category(name = "医院", icon = "医院", parentName = "健康医疗", prompts = "医院,看病,挂号,体检,门诊", isIncome = false),
        Category(name = "滋补保健", icon = "滋补保健", parentName = "健康医疗", prompts = "保健,维生素,钙片,滋补", isIncome = false),

        Category(name = "电费", icon = "电费", parentName = "居家生活", prompts = "电费,电", isIncome = false),
        Category(name = "房租还贷", icon = "房租还贷", parentName = "居家生活", prompts = "房租,还贷,房贷,租金,月供", isIncome = false),
        Category(name = "话费宽带", icon = "话费宽带", parentName = "居家生活", prompts = "话费,宽带,网费,流量,手机费", isIncome = false),
        Category(name = "家政清洁", icon = "家政清洁", parentName = "居家生活", prompts = "家政,保洁,清洁,打扫,钟点工", isIncome = false),
        Category(name = "水费", icon = "水费", parentName = "居家生活", prompts = "水费,水,打水", isIncome = false),

        Category(name = "慈善捐助", icon = "慈善捐助", parentName = "其他", prompts = "慈善,捐助,捐款,公益", isIncome = false),
        Category(name = "杂项", icon = "杂项", parentName = "其他", prompts = "杂项,其他", isIncome = false),

        Category(name = "粮油调味", icon = "粮油调味", parentName = "食品餐饮", prompts = "粮油,米油,调味料,酱油,醋", isIncome = false),
        Category(name = "请客吃饭", icon = "请客吃饭", parentName = "食品餐饮", prompts = "请客,宴请,聚餐,饭店", isIncome = false),
        Category(name = "生鲜食品", icon = "生鲜食品", parentName = "食品餐饮", prompts = "生鲜,肉,蔬菜,水果,买菜", isIncome = false),
        Category(name = "晚餐", icon = "晚餐", parentName = "食品餐饮", prompts = "晚餐,晚饭", isIncome = false),
        Category(name = "午餐", icon = "午餐", parentName = "食品餐饮", prompts = "午餐,午饭", isIncome = false),
        Category(name = "休闲零食", icon = "休闲零食", parentName = "食品餐饮", prompts = "零食", isIncome = false),
        Category(name = "夜宵", icon = "夜宵", parentName = "食品餐饮", prompts = "夜宵,宵夜", isIncome = false),
        Category(name = "饮料酒水", icon = "饮料酒水", parentName = "食品餐饮", prompts = "饮料,奶茶,咖啡,啤酒,矿泉水", isIncome = false),
        Category(name = "早餐", icon = "早餐", parentName = "食品餐饮", prompts = "早餐,早饭", isIncome = false),

        Category(name = "红包", icon = "红包", parentName = "送礼人情", prompts = "红包,压岁钱,份子钱", isIncome = false),
        Category(name = "礼物", icon = "礼物", parentName = "送礼人情", prompts = "礼物,送礼,礼品,伴手礼", isIncome = false),

        Category(name = "培训考试", icon = "培训考试", parentName = "文化教育", prompts = "培训,考试,课程,辅导班", isIncome = false),
        Category(name = "书报杂志", icon = "书报杂志", parentName = "文化教育", prompts = "书,书籍,杂志,图书,教材", isIncome = false),
        Category(name = "学费", icon = "学费", parentName = "文化教育", prompts = "学费,学杂费", isIncome = false),

        Category(name = "电影唱歌", icon = "电影唱歌", parentName = "休闲娱乐", prompts = "电影,唱歌,KTV,电影院", isIncome = false),
        Category(name = "旅游度假", icon = "旅游度假", parentName = "休闲娱乐", prompts = "旅游,旅行,度假,酒店,门票", isIncome = false),
        Category(name = "棋牌桌游", icon = "棋牌桌游", parentName = "休闲娱乐", prompts = "棋牌,桌游,麻将,扑克", isIncome = false),
        Category(name = "游戏", icon = "游戏", parentName = "休闲娱乐", prompts = "游戏", isIncome = false),
        Category(name = "运动健身", icon = "运动健身", parentName = "休闲娱乐", prompts = "运动,健身,游泳,瑜伽,跑步", isIncome = false),
        Category(name = "足浴按摩", icon = "足浴按摩", parentName = "休闲娱乐", prompts = "足浴,按摩,洗脚,推拿,洗澡", isIncome = false),

        Category(name = "报销", icon = "报销", parentName = "收入", prompts = "报销,报销款", isIncome = true),
        Category(name = "补贴", icon = "补贴", parentName = "收入", prompts = "补贴,补助,津贴", isIncome = true),
        Category(name = "二手闲置", icon = "二手闲置", parentName = "收入", prompts = "二手,闲置,卖掉,转卖", isIncome = true),
        Category(name = "工资", icon = "工资", parentName = "收入", prompts = "工资,薪水,薪资,月薪", isIncome = true),
        Category(name = "奖金", icon = "奖金", parentName = "收入", prompts = "奖金,年终奖,绩效,奖学金", isIncome = true),
        Category(name = "其他", icon = "其他收入", parentName = "收入", prompts = "其他收入,理财,利息", isIncome = true),
    )
}
