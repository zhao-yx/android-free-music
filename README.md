# HappyHear · 本地音乐播放器

一个纯本地的 Android 音乐播放器：扫描设备上已有的音频文件，按歌曲/分组管理和播放，不联网、不上传、不修改任何音频文件。

- 应用名：HappyHear
- 包名：`com.local.musicplayer`
- 最低系统：Android 7.0（API 24），targetSdk 35

## 主要功能

- **本地扫描**：读取系统 MediaStore 中的音频，过滤 ExoPlayer 无法解码的格式（APE / WMA / WV / DSD 等），自动排除铃声、通知音、闹钟目录。
- **播放**：基于 Media3 的前台播放服务，支持后台播放、通知栏/锁屏控制、播放失败提示。
- **播放控制**：播放/暂停、上一首/下一首、进度拖动、音量由系统控制。
- **播放模式**：顺序播放 → 列表循环 → 单曲循环 → 随机播放，可循环切换并持久化。
- **状态恢复**：退出或进程被杀后，恢复上次的播放模式、队列、当前歌曲与进度（不自动续播）。
- **分组管理**：创建/重命名/删除分组，向分组批量添加歌曲（支持搜索与全选），分组内可上移/下移、单曲移出或进入多选批量移出。
- **列表移除**：音乐库与分组页左滑露出操作块，点击确认后才移除；仅从当前列表移出，不删除文件，重新扫描即可恢复，并提供「撤销」。
- **加入分组**：音乐库左滑可直接把单曲加入任意已有分组，重复加入会提示「已在组内」。
- **换肤**：经典白、经典黑、商务风、二次元风、轻松欢快风五款皮肤，应用内切换并持久化；后三款使用定制背景图，五款各有对应碟片。
- **播放页**：唱片随播放状态旋转（暂停停住、切歌归零），金属唱臂随播放状态落下/抬起，碟面标签区有低对比弧形英文小字。

## 技术栈与关键版本

| 分类 | 选型 | 版本 |
| --- | --- | --- |
| 语言 / 构建 | Kotlin / AGP / Gradle | 2.2.10 / 8.13.2 / 8.13 |
| UI | Jetpack Compose（BOM）+ Material 3 | 2025.06.01 |
| 播放 | AndroidX Media3（ExoPlayer + MediaSession） | 1.8.0 |
| 本地库 | Room（KSP） | 2.7.2 |
| 偏好存储 | DataStore Preferences | 1.1.7 |
| 图片加载 | Coil（含音频内嵌封面 Fetcher） | 2.7.0 |
| 导航 | navigation-compose | 2.9.0 |
| 协程 | kotlinx-coroutines | 1.10.2 |

## 目录结构

```
AndroidPlayer/
├─ app/
│  ├─ src/main/
│  │  ├─ java/com/local/musicplayer/
│  │  │  ├─ MainActivity.kt / MusicApplication.kt
│  │  │  ├─ data/
│  │  │  │  ├─ db/          Room 数据库、分组实体与 DAO
│  │  │  │  ├─ media/       MediaStore 扫描、音频内嵌封面 Fetcher
│  │  │  │  ├─ model/       Song / Group / Skin / PlayMode
│  │  │  │  └─ repo/        Library / Group / Prefs 仓库
│  │  │  ├─ playback/       PlaybackService、PlayerConnection、封面 BitmapLoader
│  │  │  ├─ ui/
│  │  │  │  ├─ components/  歌曲行、左滑操作、碟片、唱臂、迷你播放条等
│  │  │  │  ├─ groups/      分组列表 / 分组详情 / ViewModel
│  │  │  │  ├─ library/     音乐库 / ViewModel
│  │  │  │  ├─ nav/         AppNavHost
│  │  │  │  ├─ player/      播放页、旋转状态、ViewModel
│  │  │  │  └─ theme/       皮肤色板、全局背景
│  │  │  └─ util/           媒体项映射、权限、时间格式化
│  │  ├─ assets/skins/      皮肤背景：<key>/bg.webp
│  │  └─ res/
│  │     ├─ drawable-nodpi/ 碟片 WebP、无封面占位等
│  │     ├─ drawable/       图标与矢量插图
│  │     └─ mipmap-*/       桌面图标（自适应 + 传统方图/圆图）
│  ├─ proguard-rules.pro    release R8 保留规则
│  └─ build.gradle.kts
├─ gradle/libs.versions.toml
├─ build.gradle.kts / settings.gradle.kts
└─ gradle.properties
```

## 构建与运行

### 环境

本项目使用本机 Unity 自带的 Android 工具链（SDK + JDK），无需额外安装：

- JDK：`D:/Program Files/UnityHub/Edit/6000.5.3f1/Editor/Data/PlaybackEngines/AndroidPlayer/OpenJDK`
- SDK：同一目录下的 `Sdk`

### 本地配置

`local.properties`（不纳入版本管理，需要本机自行创建）：

```properties
sdk.dir=D\:/Program Files/UnityHub/Edit/6000.5.3f1/Editor/Data/PlaybackEngines/AndroidPlayer/Sdk
```

`gradle.properties` 中通过 `org.gradle.java.home` 指向上面的 JDK。换机器时修改该行，或删除后改用环境变量 `JAVA_HOME`。

### 命令行构建

```powershell
$env:JAVA_HOME="D:\Program Files\UnityHub\Edit\6000.5.3f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat assembleDebug        # 调试包：app/build/outputs/apk/debug/
.\gradlew.bat assembleRelease      # 正式包：app/build/outputs/apk/release/
```

### release 构建与签名现状

- release 已开启 R8 代码压缩（`isMinifyEnabled = true`）与资源裁剪（`isShrinkResources = true`），规则见 `app/proguard-rules.pro`（保留了 ViewModel、RoomDatabase 构造器以兼容反射）。
- 工程**未配置 signingConfig**，`assembleRelease` 产出的是 `app-release-unsigned.apk`，需要自行签名（Android Studio 的 Generate Signed Bundle/APK，或 `apksigner`）。
- 若 R8 后出现运行期异常，可临时把 `isMinifyEnabled` / `isShrinkResources` 改回 `false` 定位问题，debug 构建不受影响。

### 包体优化结果

| 分类 | 优化前 | 优化后 |
| --- | --- | --- |
| dex | 13.85 MB | 1.92 MB |
| res | 4.31 MB | 0.71 MB |
| assets | 6.02 MB | 0.15 MB |
| resources.arsc | 0.58 MB | 0.28 MB |
| **release APK 合计** | **25.0 MB** | **3.29 MB** |

做法：三张皮肤背景与五张碟片由 PNG 转为 WebP（背景 q85、碟片 q90 且 alpha 无损），自适应图标前景转 WebP，release 开启 R8 与资源裁剪。debug 包不做压缩裁剪（约 21.7 MB），体积偏大属正常。

## 换肤机制与资源约定

皮肤由 `Skin` 枚举定义，选择结果存入 DataStore（`PrefsRepository.skin`），`Skin.style` 提供对应的 ColorScheme、渐变、遮罩与资源 key。

| 皮肤 | 背景来源 | 碟片 |
| --- | --- | --- |
| 经典白 | 纯色/渐变（代码生成） | `disc_classic_light.webp` |
| 经典黑 | 纯色/渐变（代码生成） | `disc_brand.webp` |
| 商务风 | `assets/skins/business/bg.webp` | `disc_business.webp` |
| 二次元风 | `assets/skins/anime/bg.webp` | `disc_anime.webp` |
| 轻松欢快风 | `assets/skins/cheerful/bg.webp` | `disc_cheerful.webp` |

- 背景按 `assets/skins/<key>/bg.webp → bg.png → bg.jpg` 顺序查找，找不到时回退到皮肤渐变，不会崩溃。
- 碟片统一放在 `app/src/main/res/drawable-nodpi/`，命名 `disc_<key>.webp`，由 `Skin.discRes` 映射。
- 定制背景建议 9:20（如 1080×2400）、不透明、主体避开顶部状态栏与底部控件区域。
- 碟片建议 1024×1024、透明底正圆、自带中心轴孔与标签；播放页会按圆形裁切并叠加弧形小字。

## 已知限制与注意事项

- **只显示系统媒体库已收录的音频**。应用不会主动触发系统媒体扫描；新下载的歌如果没出现，请先重启手机或让系统媒体库完成扫描，再点刷新。加密/私有格式（如 `.qmc`、`.mflac`、`.ncm`）无法扫描和播放。
- 移除歌曲、移出分组只影响列表与分组关联，**不会删除文件，也不会改动磁盘数据**。
- 播放队列恢复只恢复歌曲与进度，不自动开始播放。
- 音频格式支持范围取决于 Media3/ExoPlayer，无损与常见有损格式可用，APE/WMA/DSD 等被主动过滤。
- 无网络权限，不包含在线音乐、歌词、云同步等功能。
- release 未签名，且 R8 结果尚未在真机回归；正式发布前建议在目标机型上完整走一遍播放、通知、分组与换肤流程。
- 构建环境依赖 Unity 自带 JDK/SDK 的本机路径，换机器需要改 `local.properties` 与 `gradle.properties`。

## 授权与免责

本项目允许自由使用、修改（二创）与再分发，商业与非商业均可，无需事先申请授权。

本项目按「现状」提供，不附带任何明示或暗示的担保，包括可用性、稳定性以及特定用途的适用性。使用、修改或分发本项目及其衍生版本所产生的任何后果——数据丢失、设备损坏、账号或法律纠纷等——均由使用者自行承担，作者不承担任何责任。

建议衍生版本在显著位置说明来源与修改内容，避免与原版混淆。
