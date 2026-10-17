# 连接中断诊断

2026-10-17，小米 17 pudding，已安装 App 0.1.2。以下分析依据同机采集，不是实体写图成功记录。

## 可重放证据

输入 `build/examples/ink-nfc-hal-012.txt` 和 `build/examples/ink-nfc-action-log.txt`。前者是单次 15 秒 7A 会话的完整 HAL 日志，后者是 root 只读导出的系统 NFC 事件记录。

该会话按同样顺序重复 149 次接口激活、298 次发现通知及 298 次通用错误。代表性序列如下。

1. 同一 RF discovery ID 01、UID `11223344` 出现两个发现通知，协议分别 `04`（ISO-DEP）和 `90`（MIFARE）。SAK 为 `28`。
2. 手机发送 `210403010402`，选择 ID 01、协议 04、接口 02。
3. 控制器返回 `61052201020478FF000C0000040004112233440128007800000B0A7833A002901122334403`，包含 ISO-DEP 激活参数。
4. 系统事件记录同时出现 `NFA_ACTIVATED_EVT proto=0x04 mode=0x00 intf=0x02 POLL`。因此 HAL 中的模式 78 已经在栈内转换为 00，不能据其原始值推断系统不认识 NFC-A。
5. 没有发送 D1，也没有任何 NCI DATA 包；手机主动发送 `21060101`，请求进入 sleep。
6. 手机发送 `210403019090`，选择同一 ID 的 MIFARE 协议和接口。控制器返回 `600701A1`，重试一次仍失败，然后退回 idle 并重启寻卡。

标准 NCI `A1` 对应 ACTIVATION_FAILED，不能再将其笼统称为未定义的厂商错误。此处它发生于第二协议重选，不能据此否定先前 ISO-DEP 已激活。

## 假设与区分

- 自定义寻卡模式不被系统识别：系统事件已显示 mode 00 和 ISO-DEP 激活，现有证据不支持此解释。
- 多协议枚举让手机先断开有效连接，再尝试板子不支持或无法重新唤醒的 MIFARE 路径：与所有已采集循环吻合，是当前优先假设。
- 供电或位置不稳定：仍可能影响后续传图，但现有中断先由手机明确发出 sleep 请求，不能把当前现象直接归因于缺电。

## 源码与同机实现核对

[AOSP NfcTag.cpp](https://android.googlesource.com/platform/packages/apps/Nfc/+/b6d09fcab81023dceab8ed54fce8d14588a5a42f/nci/jni/NfcTag.cpp) 在尚有其他协议待枚举时延迟 Java 标签上报；后续选择另一个协议。此行为可解释为何 HAL 已激活而 App 没有 Tag 回调。

[AOSP NativeNfcManager.cpp 代码变更中的原实现](https://android.googlesource.com/platform/packages/apps/Nfc/+/7c08bb17ef0bdbf88c790b36a84517cc438199e6%5E2..7c08bb17ef0bdbf88c790b36a84517cc438199e6/) 包含激活后若仍有待枚举协议则先 sleep、再 selectNextTagIfExists 的路径。

[AOSP NCI 定义](https://android.googlesource.com/platform/system/nfc/+/7bef3988d7128b2de349f90b9d5f399426f35fb9/src/include/nci_defs.h) 用于核对 A1 和协议字段。

只读提取手机 APK 内 `lib/arm64-v8a/libstnfc_nci_jni.so` 到电脑 `build/examples/libstnfc_nci_jni.so`。动态符号确认该实现包含 NfcTag::selectNextTagIfExists、createNativeNfcTag、getNumDiscNtf、setNumDiscNtf 和多协议状态接口。尚未对手机进程应用兼容修改。

## 下一步验证边界

建议一次限时、仅进程内的兼容测试，仅对指定 UID 的有效 ISO-DEP 激活跳过后续 MIFARE 枚举。验证标准是 App 得到 Tag、ISO-DEP 可连接并获得 D1 原始回复，而不只是错误数减少。普通卡行为、补丁撤销和原轮询配置读回都需验证。

该方法需要对 NFC 服务做临时运行时拦截，风险与单纯调用 NFC 接口不同，已向用户说明并请求确认；确认前不实施。不修改系统镜像、固件或支付相关存储，不将未验证的兼容方法当作已完成修复。

## 临时兼容实测成功

用户确认继续兼容测试，并明确先不通过重启清除补丁。未重启手机或 NFC 服务。

使用官方 Frida 17.24.0 Android arm64，发布文件 SHA256 核对一致。服务器仅监听手机回环地址，使用 internal policy softener、disable-preload；通过 USB 转发连接。先只读观察 createNativeNfcTag 实参，确认每轮同一目标有效 ISO-DEP 激活时待枚举协议数为 1。随后临时拦截仅匹配该实参前 17 字节 `0104000400041122334400000000000028` 且待枚举数恰为 1，调用当前库原生 setter 将待枚举数改为 0，使有效连接能立即上报。其他 UID、其他协议、其他计数及禁用态不处理，守卫测试覆盖这些分支。

2026-10-17 13:35:50，App 0.1.3 收到 UID `11223344` 的 Android Tag，ISO-DEP 连接成功，实际发送 `00D1000000`，收到：

```text
A007F0C72006000228A10700120030FFFFFFB10102B20114B30100C004201EA001C10411223344D107012000000000009000
```

末尾状态字 9000，D1 自报 UID 与标签 UID 一致。解析为 552×768、四色、固件版本 20、现有刷图配置兼容检查通过。此前“目标身份未核实”结论由该次 D1 实测更新；这是实际驱动板设备信息，不是模拟 PN532 数据。13:36:05 原轮询配置恢复且逐字节读回一致，辅助进程退出 0。

本次只读查询成功，不代表传图、认证、压缩和刷新已完成。App 在查询后主动关闭连接，后续日志仍出现过 MIFARE 失败；本轮关键成功标准是实际 Tag 与 D1，不能宣称所有 A1 已消失。当前补丁仍依赖电脑端 tools/nfc_compat.py 的会话，尚未整合为脱离电脑的 App 功能，也不是系统永久修复。

App 0.1.3 同时将高频底层日志的界面更新合并为每 200ms 一次，界面仅渲染末尾 12000 字符，原始记录继续保存，避免长日志排队阻塞交互。

13:36:58 第二次独立点击 7A 探测再次收到同一 UID 的 Tag 和完全相同的 D1 回复，证明不是一次偶发标签发现。该次技术列表为正常的 IsoDep、NfcA；兼容脚本记录第二次匹配。
