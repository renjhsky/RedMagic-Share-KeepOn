# 红魔互传常开 · 1.0

为 NX789J 的红魔互传取消闲置 10 分钟自动关闭。

[下载最新版本](https://github.com/renjhsky/RedMagic-Share-KeepOn/releases/latest)

## 使用

1. 安装 Release 中的 APK。
2. 在 LSPosed / Vector 中启用本模块，作用域只选择「红魔互传」，然后重启手机。
3. 打开本应用，通过两个开关使用：

| 开关 | 功能 |
| --- | --- |
| 启用模块 | 开启常开效果；关闭后恢复原来的自动关闭计时 |
| 打开红魔互传 | 直接开启或关闭系统互传 |

右上角「检查更新」打开 GitHub 最新版本页面。模块开关控制常开效果，首次仍需在框架中启用模块。

## 适配

- 机型：NX789J，Android 14 及以上。
- 互传包名：`com.zte.cn.zteshare`。
- 互传版本：`16.0.000.000.2607131724`。
- DEX SHA-256：`a65f3ebc49931f25e3d04da3e3c94743f996384b341db0a93bc02e45a8b1d840`。

代码指纹不匹配时不修改互传逻辑。版本名称为 **1.0**，内部版本号 **100**，可同签名覆盖安装之前的测试版。

## 编译

Linux x86_64、Python 3.10+、Java 17+（含 keytool）：

```bash
python3 build.py
```

脚本下载固定版本的官方构建工具并校验 SHA-256；使用前请自行阅读并遵守各依赖许可。
本地构建默认生成开发签名。官方安装包使用保留的私有签名，私钥不包含在仓库中。
发行构建可以指定自己的密钥：

```bash
export RMSK_KEYSTORE_PASSWORD='your-password'
python3 build.py --keystore /path/to/private-release.p12 --alias your-alias
```

Xposed API 仅参与编译，不随 APK 打包。原系统 APK 和反编译的系统代码不包含在仓库中。

## 验证

见 [VALIDATION.md](VALIDATION.md)。已进行原 APK 字节码审计、主机 Hook 检查及 Android 框架界面回归。
无线发现、熄屏和实际文件收发仍需在设备上验证。

## 发布

`v1.0` Release 提供已签名的安装包和 SHA-256 校验文件。
源码可从 GitHub 下载；签名私钥和本地构建缓存始终排除。

MIT License.
