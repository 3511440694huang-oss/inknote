#!/system/bin/sh
# 在 Android Shell（Shizuku / Root）环境执行，把墨笺安装到本机：
#   sh install-to-device.sh
# 注意：SELinux 下不能直接 pm install /storage 下的文件，必须先拷到 /data/local/tmp。
APK='/storage/emulated/0/Download/DSH-Go/墨笺-1.2.0.apk'
cp "$APK" /data/local/tmp/inknote.apk && pm install -r /data/local/tmp/inknote.apk
