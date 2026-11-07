package dev.kidstubetv.data

/** Pure, non-logging decision for a Google TVs / Limited Input OAuth client. */
internal object TvOauthClientPolicy {
    data class Update(val id: String, val secret: String, val changed: Boolean)

    fun prepare(
        currentId: String,
        currentSecret: String,
        enteredId: String,
        enteredSecret: String,
    ): Update {
        val id = enteredId.trim()
        require(id.length in 27..200 &&
            id.endsWith(".apps.googleusercontent.com") &&
            !id.any(Char::isWhitespace)) {
            "Client ID 格式不正确，应以 .apps.googleusercontent.com 结尾"
        }
        // Hidden stored secrets are intentionally never shown in the TV form;
        // empty input means retain the previously saved secret.
        val secret = enteredSecret.trim().ifEmpty { currentSecret }
        require(secret.isNotBlank()) { "请填写 Google TV OAuth Client Secret" }
        return Update(id, secret, id != currentId || secret != currentSecret)
    }
}
