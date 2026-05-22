# 简记账 (Ledger) 项目提示词

## 项目信息
- 包名：`com.verdantgem.ledger`
- 技术栈：Kotlin + Jetpack Compose + Hilt + Room + Paging 3 + OkHttp + Apache POI (XLS)
- 最低 SDK：26 (Android 8.0)
- 目标 SDK：36 (Android 16)

## 构建命令
- `./gradlew assembleDebug` — Debug 构建
- `./gradlew assembleRelease` — Release 构建（已启用 R8 混淆）
- `./gradlew lint` — 静态分析

## 定位功能 (高德定位 SDK)
- **依赖**：`com.amap.api:location:6.5.1`（`gradle/libs.versions.toml` → `amap-location`）
- **API Key**：在 `AndroidManifest.xml` 中替换 `<meta-data android:name="com.amap.api.v2.apikey" android:value="你的KEY"/>` 的 `你的KEY` 为实际 Key
- **定位服务**：`LocationProvider`（Hilt `@Singleton`），`getAddress()` 每次调用创建/销毁 `AMapLocationClient`，`finally` 中确保 `onDestroy()` 释放系统连接
- **生命周期**：进入记账界面 → `startLocation()` → 定位完成自动 `onDestroy()`；退出界面 → `DisposableEffect.onDispose` / `onCleared` → `stopLocation()` 取消 job；保存前 `locationJob?.join()` 等待定位完成
- **防重复**：`startLocation()` 有双重 guard — 已有有效地址不重复获取，已有正在执行的 job 不重复启动
- **数据库版本**：v5（`MIGRATION_4_5` 新增 `updatedAt`、`deleted` 字段）
- **RecordDetailScreen**：仍然显示已保存的 `Record.address` 字段（若有）

## 预算功能
- **数据模型**：`Budget` Entity（单例，id=1），`monthlyAmount` 存月预算金额
- **DAO**：`BudgetDao`（`getBudgetFlow`、`upsertBudget`、`deleteBudget`）
- **Dashboard 双卡片**：
  - `IncomeExpenseCard`（Primary 深色背景）：展示 收入 | 支出 | 结余 三列
  - `BudgetCard`（Surface 白底）：展示预算进度条、剩余/总额、日均支出/预算，点击跳转 `BudgetEditScreen`
- **BudgetEditScreen**：独立管理页面，可设置/更新/清除月度预算
- **月度统计**：`RecordDao` 新增 `getMonthlyExpenseFlow` / `getMonthlyIncomeFlow`，按当月起止时间戳过滤
- **Dashboard 修复**：之前"本月支出/收入"显示的是全量数据，现改为真正的当月数据

## 键盘与 IME
- **`windowSoftInputMode="adjustNothing"`**（`AndroidManifest.xml` 中配置）—— 因为项目启用了 `enableEdgeToEdge`，采用 `adjustNothing` + 手动 `imeBottomDp` 方案
- **`AddRecordScreen` 不再使用 `Scaffold`** — 改用 `Box(fillMaxSize().background().padding(bottom = imeBottomDp))` + `Column` 手动布局
- **`imeBottomDp`** = `WindowInsets.ime.getBottom(density).toDp()`（与 `DashboardScreen.kt:341-343` 一致）
  - `Box.background(Color)` 在 `padding` 外层 → 全屏覆盖无白底
- **自定义键盘** 不再使用 `AnimatedVisibility` — 改用**绝对值相减模型**：
  - `actualKeyboardDp = maxOf(0.dp, customKeyboardHeight - imeBottomDp)` — 键盘高度随 IME 等比例缩小
  - `keyboardAlpha = actualKeyboardDp / customKeyboardHeight` — 同步淡出
  - `Box(height = actualKeyboardDp).alpha(keyboardAlpha).clipToBounds()` + 内部 `Surface.align(TopCenter)` — 从底部裁剪，顶不动
  - 效果：键盘布局高度与 IME 高度绝对值相减 → 总底部空间恒定 → 无弹跳

## 架构规范
- **MVVM 模式**：UI 层 Composable → ViewModel (StateFlow) → Repository → DAO
- **依赖注入**：全部使用 Hilt（`@HiltViewModel`, `@AndroidEntryPoint`, `@Singleton`）
- **分页**：使用 Paging 3，pageSize = 20，initialLoadSize = 20
- **数据库查询**：聚合计算（SUM）在 SQL 层完成，不把全量数据加载到 Kotlin 层
- **线程安全**：所有网络请求和文件 IO 必须使用 `withContext(Dispatchers.IO)`

## 主题设置
- **入口**：`SettingsScreen` 顶部"主题设置"ListItem，右侧显示当前模式
- **ThemeSettingScreen**：独立管理页面，三种模式：跟随系统 / 浅色 / 深色
- **ThemeMode**：`ThemeMode.SYSTEM` / `LIGHT` / `DARK`，存储在 `SharedPreferences`

## 数据同步（WebDAV）
### 新增依赖
- `kotlinx-serialization-json:1.7.3` — JSON 序列化同步快照
- `androidx.lifecycle:lifecycle-process:2.7.0` — ProcessLifecycleOwner 监听后台事件

### 同步策略：Last-Write-Wins 增量合并
- 导出全量数据为 JSON 快照，上传到 WebDAV 的 `简记账/{用户名}/ledger_sync.json`
- 下载远程快照，逐实体比对 `updatedAt`，时间戳较新的覆盖
- 使用 `kotlinx.serialization` 格式化，Hilt `@Singleton` 管理状态

### 多用户隔离
- 每个用户通过「同步身份」设置中的用户名隔离到独立子目录
- 路径格式：`{URL}/简记账/{syncUsername}/`（使用 `Uri.encode` 编码中文/特殊字符）
- 不同用户使用不同用户名，数据完全隔离，不互相干扰
- 自动备份也按用户隔离：`简记账/{用户名}/ledger_backup_*.db`
- 同一用户的多台设备使用相同用户名即可互通

### 加密（可选）
- `CryptoManager`：AES-256-GCM + PBKDF2-HMAC-SHA256（100,000 轮），零额外依赖
- 用户设置同步加密密码，数据加密后上传；未设置密码则明文传输
- 加密后格式：`Base64(Salt[16B] + IV[12B] + CipherText)`

### 软删除机制
- `Record`、`Category`、`Budget` 均新增 `deleted: Boolean` 字段
- UI 查询自动过滤 `WHERE deleted = 0`，同步时不过滤
- 删除操作改为 `UPDATE ... SET deleted = 1`，用于跨设备传播删除
- 定时清理删除超过 7 天的记录（`purgeDeletedRecords`）

### 连接测试
- `testConnection()`：先 **MKCOL** 创建 `{URL}/简记账/` 目录（已存在则 405 忽略），再 PROPFIND 验证连通性
- `saveConfig()`：仅在 url/user/pass **发生变更**时才重置 `connectionTestSuccess`，配置不变则保留已测试状态
- WebDAV 参数和加密密码字段均使用 `PasswordVisualTransformation`，支持点击眼睛图标切换显示/隐藏

### 触发机制
- **启动时**：`LedgerApplication.onCreate()` → 如果 `autoSyncEnabled`，执行一次 `fullSync()`
- **数据变更时**（运行中）：`DataChangeNotifier`（SharedFlow）→ `SyncManager` 30 秒 debounce → `fullSync()`
- **回前台时**（App 从后台切回）：`ProcessLifecycleOwner.onStart()` → `SyncManager.onAppForegrounded()` → 如果后台停留超过 30 秒，自动拉取同步
- **退出时**（App 进入后台）：`ProcessLifecycleOwner.onStop()` → 如果 dirty 标记为 true → 同步 + 备份
- **脏标记优化**：`AtomicBoolean dirty`，同步成功后置 false，退出时仅 dirty=true 才同步，避免重复
- **手动触发**：设置页「立即同步」按钮 / 总览页同步按钮（搜索按钮旁，同步中显示转圈动画）
- **同步状态**：`SyncManager.isSyncing`（`StateFlow<Boolean>`）暴露给 UI，Dashboard 和设置页均可观察

### 自动备份
- `SyncManager.backupDatabase()` — 上传 SQLite `.db` 文件到 `简记账/ledger_backup_*.db`
- 保留最近 5 个备份，自动清理旧文件
- 仅在 App 退出时、且 `autoBackupEnabled=true` 时触发

### 核心文件
| 文件 | 职责 |
|------|------|
| `data/remote/WebDavClient.kt` | 封装 OKHttp WebDAV 操作（PUT/GET/PROPFIND/DELETE/MKCOL）；`testConnection()` 先 MKCOL 创建 `简记账/` 目录再 PROPFIND |
| `data/remote/CryptoManager.kt` | AES-256-GCM 加解密 |
| `data/remote/SyncSnapshot.kt` | 同步快照数据结构 + Entity ↔ Sync 转换函数 |
| `data/remote/SyncManager.kt` | 核心同步编排：pull → merge → push + backup；`ensureDir()` 确保远程目录存在后操作；`isSyncing` StateFlow 暴露全局同步状态 |
| `data/DataChangeNotifier.kt` | 数据变更事件通知器 |
| `ui/screens/settings/SettingsViewModel.kt` | 同步状态管理（加密密码、自动备份开关、手动触发）；`saveConfig()` 仅在配置变化时重置测试状态 |
| `ui/screens/settings/WebDavConfigScreen.kt` | 配置 UI（加密密码弹窗、同步状态展示） |
| `ui/screens/dashboard/DashboardViewModel.kt` | 注入 SyncManager，暴露 `isSyncing` 和 `triggerSync()` 供总览页同步按钮使用 |

### 界面结构
- `SettingsScreen` → "数据同步设置" → `WebDavConfigScreen`
  1. WebDAV参数配置 — 弹出对话框配置 url/user/pass（密码字段可切换显示/隐藏）+ 测试连接（自动创建 `简记账/` 目录）
  2. 同步身份 — 弹出对话框设置用户名 + 可选加密密码（密码字段可切换显示/隐藏）
  3. 自动同步 — Switch（配置 + 连接测试成功后可操作）
  4. 自动备份（SQLite数据库） — Switch
  5. 同步状态 — 上次同步时间 + 当前状态 + [立即同步] 按钮
- 所有文件上传到 `{URL}/简记账/` 子目录下（`Uri.encode("简记账")`）

## 账单导入功能（XLS）
- **依赖**：`org.apache.poi:poi:5.2.5`（`gradle/libs.versions.toml` → `apache-poi`）
- **导入器**：`XlsImporter`（Hilt `@Singleton`），解析 .xls 文件（HSSFWorkbook），支持多列索引（日期/收支类型/金额/一级分类/二级分类/备注/地址）
- **匹配策略**：子分类精确匹配 → 一级分类精确匹配 → 按收支类型兜底 → 任意可用分类
- **UI 流程**：`SettingsScreen` → "导入导出" → `ImportExportScreen` → SAF 文件选择 → 解析预览（总条数/支出/收入/匹配/不匹配） → 带进度条批量导入
- **数据变更通知**：批量导入完成后触发 `DataChangeNotifier` 一次，避免逐条触发
- **R8 保留规则**：`proguard-rules.pro` 中 `pickFirst "/META-INF/services/**"` 确保 POI ServiceLoader 正常工作

## 快速记账类别选择
- **类别选择**：`QuickRecordOverlay` 内部使用 `QuickCategoryPicker`（内联于 Surface 中），不再使用 AlertDialog；显示父类别为标签头、子类别为平铺网格（4列）
- **手动类别优先**：类别 Chip 显示优先使用 `selectedCategory`（手动选择），回退到 `matchedCategory`（文本解析）
- **匹配算法**：精确匹配名称 → 精确匹配 prompts → 包含匹配 prompts → 名称包含匹配；收入/支出类别各自匹配，若同时匹配到则返回收入

## 高级记账（AddRecordScreen）自动选择类别
- **自动匹配**：监听 `note` 字段变化，复用与快速记账相同的类别匹配算法，自动设置 `selectedSub` 和 `expandedParent`，并联动切换 `isIncome`
- **防干扰**：`userTouchedCategory` 状态追踪用户是否手动操作过类别网格或收入/支出切换，一旦手动操作即停止自动匹配，避免覆盖用户选择

## 时间功能
- **双层时间模型**：`Record.date` = 账单时间（用户可修改），`Record.createdAt` = 记录时间（系统自动，不可修改）
- **DateTimePickerDialog**（`ui/components/DateTimePicker.kt`）：两步时间选择器 — 先 Material3 DatePickerDialog（选日期），确认后切换 TimePicker（选时间），24 小时制；提供 `formatDateTime(millis, pattern)` 工具函数
- **高级记账（AddRecordScreen）**：底部备注栏左侧嵌入日历图标按钮，点击弹出 DateTimePickerDialog
- **快速记账（DashboardScreen QuickRecordOverlay）**：类别 Chip 旁新增日期 Chip（格式 MM-dd HH:mm），未匹配类别时显示"类别"文字
- **账单详情（RecordDetailScreen）**："账单时间"显示 `date` 且可编辑（Edit 图标），"记录时间"显示 `createdAt` 只读（灰色图标）
- **编辑账单时间**：`RecordDao.updateBillDate()` → `LedgerRepository.updateRecordBillDate()` → `RecordDetailViewModel.updateBillDate(newDate)`，更新后自动刷新页面

## 主界面时间轴
- **分组标题**：`DashboardViewModel.getDateGroupLabel()` 保留 "今天""昨天" 为文字标签，其余显示具体日期
- **日期格式**：本年显示 `MM-dd`（如 `03-15`），非本年显示 `yyyy-MM-dd`（如 `2025-12-01`）
- **每日收支**：标题行右侧显示当日收支汇总 `"收入 xxx 支出 xxx"`，数据来源于 `DashboardViewModel.dailyAggregates`（基于近 6 个月记录按 `getDateGroupLabel` 分组汇总）

## 统计界面排名列表
- **数据**：`StatisticsViewModel.activeRanking`（`StateFlow<List<CategoryRank>>`），从 `activeCategoryDistribution` 派生，按金额降序，含百分比
- **UI 组件**：`CategoryRankingList`（`StatisticsScreen.kt`），位于柱状图下方，每行含分类名 + LinearProgressIndicator + 百分比 + 金额
- **柱状图**：`SimpleBarChart` 点击柱子弹出悬浮框，显示时间标签 + 收支类型 + 金额；Y轴刻度显示整数
- **饼图**：`CategoryPieChart` 用连线在饼图内直接显示标签名称和百分比，不再使用图例
- **标题显示**："分类收入占比" → "收入占比"，"分类支出占比" → "支出占比"
- **快捷切换**：占比/明细标题行右侧嵌入 `CompactToggle` 组件（切换支出/收入）+ `TextButton`（切换一级/二级分类）
- **分类层级**：`showDetail` 控制一级（父类别汇总）或二级（子类别）显示，`activeCategoryDistribution` 自动切换，默认一级
- **小数精度**：统计页面所有数值统一保留两位小数（柱状图Y轴/标签、饼图、排名列表百分比和金额）
- **子类别前缀**：二级模式下子类别名显示为 `父类别-子类别` 格式（如 `食品餐饮-午餐`），`activeRanking` 和 `activeComparisonMap` 同步处理 name 映射

### 统计模式与日期导航
- **StatsMode** 三种模式：日常（RECENT，本周逐日查看）、月（MONTH，当月逐日）、年（YEAR，当年逐月），通过 Tab 切换
- **日期导航**：月/年模式下点击日期文字弹出 `DatePickerDialog`，选中后自动更新；日常模式无日期选择
- **收支切换**：移至日期行右侧，与明细区域一致使用 `CompactToggle` 小开关
- **汇总卡片**：TabRow 下方显示「收入｜支出｜结余」三列汇总，数据源为 `StatisticsViewModel.totalIncome/totalExpense/totalSurplus`
- **生命周期**：离开统计页面时（ON_PAUSE）自动重置为默认模式（日常/支出/一级分类）

### 环比对比功能
- **对比周期**：日常模式对比上周、月模式对比上月、年模式对比上年
- **数据源**：`StatisticsViewModel.prevPeriodDistribution` 独立计算上一周期分布，`activeComparisonMap` 逐分类计算差值
- **展示方式**：`CategoryRankingList` 每行进度条下方右对齐显示对比信息（↑/↓ 百分比 + 金额）
- **颜色规则**：支出上升（更差）→ 红色 `#E53935`，支出下降（更好）→ 绿色 `#43A047`；收入上升（更好）→ 绿色，收入下降（更差）→ 红色；新增分类 → 蓝色 `#1E88E5`；持平 → 灰色
- **金额符号**：支出带 `-` 前缀，收入带 `+` 前缀，均保留两位小数

## 生命周期与数据刷新
- **DashboardScreen**：使用 `DisposableEffect` + `LifecycleEventObserver` 监听 ON_RESUME → `viewModel.onResume()`，修复切回前台后日期/月度统计不刷新
- **WebDavConfigScreen**：ON_RESUME → `viewModel.loadConfig()`，修复同步时间不刷新
- **StatisticsScreen**：ON_PAUSE → `viewModel.resetToDefault()`，离开时重置统计模式

## 默认类别（DefaultCategories.kt）
- **初始化**：`LedgerRepository.seedDefaultCategories()` 在数据库类别数为 0 时写入默认类别；`resetToDefaultCategories()` 可重置
- **支出父类别**：出行交通、购物消费、健康医疗、居家生活、其他、食品餐饮、送礼人情、文化教育、休闲娱乐、快递
- **收入父类别**：收入
- **子类别示例**：打车（出行交通）、生鲜食品（食品餐饮）、奖金/奖学金/工资（收入）等；每个子类别带有 prompts 用于快速记账文本匹配

## 性能注意事项
- LazyColumn/LazyRow 必须设置 `key` 参数
- `filter`、`map` 等集合操作必须用 `remember` 包装
- `collectAsState()` 统一放在 Composable 函数顶部
- ViewModel 中使用 `SharingStarted.WhileSubscribed(5000)`
- Compose 状态收集：`collectAsLazyPagingItems()` 用于分页数据
