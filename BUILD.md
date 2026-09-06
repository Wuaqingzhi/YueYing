# 月影（YueYing）构建说明

## 一、手机端构建（AndroidIDE，最快）

1. 解压 `YueYing月影-源码.zip` 到手机（如 `/storage/emulated/0/AndroidIDEProjects/YueYing`）
2. 打开 AndroidIDE → 打开项目 → 选择刚解压的目录
3. 等待 Gradle 同步完成（首次会下载依赖，需联网）
4. 点顶部 **Build**（锤子图标）→ **Build APK**，或直接点 ▶ 运行安装
5. 产物位置：
   - Debug 包（测试用）：`app/build/outputs/apk/debug/app-debug.apk`
   - Release 包（正式分发）：`app/build/outputs/apk/release/app-release.apk`

## 二、电脑端构建（Android Studio）

```bash
# 环境要求：JDK 17+、Android SDK 36
./gradlew assembleDebug     # 测试包
./gradlew assembleRelease   # 正式包
```

## 三、签名说明

| 包类型 | 签名 | 说明 |
|---|---|---|
| debug | `debug.keystore`（项目内置） | 仅自测/调试，人人可签，**不能对外分发** |
| release | `release.keystore`（项目根目录） | 正式签名，安装后无法更换签名，**务必备份** |

- Release 签名密码在 `gradle.properties` 的 `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD`
- ⚠️ 发布前请改掉默认密码并**不要把 keystore 和密码提交到公开仓库**

## 四、发布前待办

1. 建好你的 GitHub 仓库后，替换 3 处 `yourname/yueying` 占位：
   - `app/src/main/kotlin/com/yueying/app/data/update/UpdateChecker.kt`
   - `app/src/main/kotlin/com/yueying/app/ui/screens/AboutScreen.kt`
   - `app/src/main/kotlin/com/yueying/app/ui/screens/OnboardingScreen.kt`
2. 换自己的微信收款码：`app/src/main/res/drawable/weixin.png`
3. 生成 APK 后建议用 `apksigner verify` 检查签名：
   ```bash
   apksigner verify --print-certs app-release.apk
   ```
