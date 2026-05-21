# 简记账 (Ledger)

一款简洁的 Android 记账应用，支持快速记账、高级记账、统计图表、WebDAV 同步和 XLS 账单导入。

## 功能

- **快速记账** — 在主界面直接输入文字，自动解析金额和类别
- **高级记账** — 完整表单，支持选择类别、备注、时间和定位
- **统计图表** — 饼图、柱状图、分类排名，支持周/月/年环比对比
- **预算管理** — 设置月度预算，Dashboard 实时查看进度
- **WebDAV 同步** — 多设备数据同步，支持 AES-256-GCM 加密
- **XLS 导入** — 从 Excel 文件批量导入账单
- **主题切换** — 跟随系统 / 浅色 / 深色

## 下载

前往 [GitHub Releases](https://github.com/{owner}/{repo}/releases) 下载最新 APK。

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3
- **架构**：MVVM (ViewModel + StateFlow + Repository)
- **依赖注入**：Hilt
- **数据库**：Room + Paging 3
- **网络**：OkHttp (WebDAV)
- **解析**：Apache POI (XLS)
- **定位**：高德定位 SDK

## 构建

```bash
# Debug
./gradlew assembleDebug

# Release（R8 混淆压缩）
./gradlew assembleRelease

# 静态分析
./gradlew lint
```

## 配置说明

### 定位功能
在 `AndroidManifest.xml` 中替换高德 API Key：
```xml
<meta-data
    android:name="com.amap.api.v2.apikey"
    android:value="你的KEY" />
```

### WebDAV 同步
1. 进入「设置 → 数据同步设置」
2. 配置服务器地址、用户名、密码
3. 测试连接
4. 开启自动同步

## 最低要求

- Android 8.0 (API 26) 及以上

## 许可

本项目仅供个人学习使用。
