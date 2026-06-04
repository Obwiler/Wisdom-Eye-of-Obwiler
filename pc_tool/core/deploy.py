"""WEO deploy orchestrator — stateless, using new AdbClient."""

import time
from pathlib import Path
from core.adb_client import AdbClient, AdbError
from core.constants import WEOConfig
from core.config_sync import ConfigSync


def deploy(
    adb: AdbClient,
    config: WEOConfig,
    apk_path: Path,
    config_dict: dict | None = None,
    serial: str | None = None,
) -> tuple[bool, str]:
    """Run the full WEO deploy pipeline: stop, install, push config, launch.

    Returns (success, message).
    """
    steps = []
    try:
        # 1. stop
        adb.force_stop(config.pkg_name, serial=serial)
        steps.append("已停止应用")
        time.sleep(1.0)

        # 2. install APK
        if not apk_path.exists():
            return False, f"APK 文件不存在: {apk_path}"
        if adb.install(str(apk_path), serial=serial):
            steps.append("APK 安装成功")
        else:
            steps.append("APK 安装失败")
            return False, "\n".join(steps)

        # 3. push config
        if config_dict:
            sync = ConfigSync(adb, config)
            if sync.push(config_dict, serial=serial):
                steps.append("配置已推送")
            else:
                steps.append("配置推送失败（继续启动）")

        # 4. launch
        adb.start_activity(config.activity, serial=serial)
        steps.append("应用已启动")

        # 5. dismiss any permission dialog (keyevent 66 = ENTER)
        time.sleep(2.0)
        try:
            adb.shell("input keyevent 66", serial=serial)
            steps.append("已确认权限对话框")
        except AdbError:
            pass  # no dialog, that's fine

        # 6. verify app is running (poll pidof)
        time.sleep(1.5)
        try:
            pid = adb.shell(f"pidof {config.pkg_name}", serial=serial)
            if pid:
                steps.append(f"应用运行中 (PID {pid})")
            else:
                steps.append("应用已启动（PID 确认超时，请检查眼镜屏幕）")
        except AdbError:
            steps.append("应用已启动（无法确认 PID）")
        return True, "\n".join(steps)

    except AdbError as e:
        steps.append(f"部署异常: {e}")
        return False, "\n".join(steps)


def check_status(adb: AdbClient, config: WEOConfig, serial: str | None = None) -> str:
    """Return a human-readable status string for the WEO app."""
    if adb.is_installed(config.pkg_name, serial=serial):
        try:
            model = adb.getprop("ro.product.model", serial=serial)
            android = adb.getprop("ro.build.version.release", serial=serial)
            return f"已安装 | {model} | Android {android}"
        except AdbError:
            return "已安装 | 设备信息获取失败"
    return "未安装"


def install_only(adb: AdbClient, apk_path: str, serial: str | None = None) -> tuple[bool, str]:
    if adb.install(apk_path, serial=serial):
        return True, "安装成功"
    return False, "安装失败"


def uninstall_only(adb: AdbClient, pkg: str, serial: str | None = None) -> tuple[bool, str]:
    """Uninstall the app. Returns (ok, message) with ADB output on failure."""
    r = adb._run_for_serial(serial, "uninstall", pkg, timeout=30)
    if r.ok and "Success" in r.stdout:
        return True, "卸载成功"
    # Gather details
    err = r.stderr.strip() or r.stdout.strip() or "未知错误"
    # Check common cases
    if "DELETE_FAILED_INTERNAL_ERROR" in err:
        return False, f"卸载失败: 应用可能未安装\n  ADB: {err}"
    if "not installed" in err.lower() or "Unknown package" in err:
        return False, "卸载失败: 应用未安装（眼镜上不存在 WEO）"
    return False, f"卸载失败\n  ADB: {err}"
