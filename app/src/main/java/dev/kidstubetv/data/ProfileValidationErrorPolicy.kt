package dev.kidstubetv.data

/** Error kinds intentionally contain no provider URL, proxy name or credential. */
internal enum class ProfileValidationError(val message: String) {
    MISSING_FILE("配置引用了不存在的文件或本地 provider，请导出完整 Mihomo YAML"),
    MISSING_PROXY("代理组引用了找不到的节点，请检查 proxy-groups 和 proxies"),
    YAML("Mihomo 无法解析该 YAML，请检查节点协议和配置字段"),
    PROVIDER("Mihomo 无法加载 proxy-providers / rule-providers，请检查 provider 地址与缓存"),
    CORE("Mihomo 内核未能初始化，请检查配置与当前设备兼容性"),
    TIMEOUT("Mihomo 内核校验超时，原配置未修改"),
    SERVICE("Android 无法启动配置校验进程，原配置未修改")
}

internal class ProfileValidationException(val kind: ProfileValidationError) :
    IllegalStateException(kind.message)

internal object ProfileValidationErrorPolicy {
    fun classify(raw: String?): ProfileValidationError {
        val error = raw.orEmpty().lowercase()
        return when {
            error.contains("no such file") || error.contains("not exist") ||
                error.contains("open ") && error.contains("file") ->
                ProfileValidationError.MISSING_FILE
            error.contains("proxy") && (error.contains("not found") ||
                error.contains("not exist") || error.contains("unknown")) ->
                ProfileValidationError.MISSING_PROXY
            error.contains("proxy provider") || error.contains("rule provider") ||
                error.contains("provider") && error.contains("download") ->
                ProfileValidationError.PROVIDER
            error.contains("yaml") || error.contains("unmarshal") ||
                error.contains("parse") || error.contains("config") ->
                ProfileValidationError.YAML
            else -> ProfileValidationError.CORE
        }
    }
}
