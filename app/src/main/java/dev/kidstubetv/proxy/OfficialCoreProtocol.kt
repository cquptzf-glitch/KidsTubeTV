package dev.kidstubetv.proxy

import org.json.JSONObject

/**
 * v0.9.3 FlClash native RPC response parser. Its envelope differs from the
 * third-party libmihomo's code/data response. Kept JVM-testable.
 */
internal object OfficialCoreProtocol {
    fun parse(payload: String): String {
        val envelope = JSONObject(payload)
        envelope.optJSONObject("error")?.let { failure ->
            throw IllegalStateException(
                failure.optString("message").ifBlank { "FlClash 内核调用失败" }
            )
        }
        check(envelope.has("result")) { "FlClash response is missing result" }
        val result = envelope.opt("result")
        return if (result == null || result == JSONObject.NULL) "{}" else result.toString()
    }

    /** Native proxy.select can return a result with a message but no RPC error. */
    fun validateSelection(payload: String): String {
        val result = JSONObject(payload)
        val message = result.optString("message")
        require(message.isBlank()) { message }
        return payload
    }
}
