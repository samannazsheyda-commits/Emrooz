package com.nameemrooz.journal.writing

/** Preserve technical tokens and numerical values including their separators and case. */
object ProtectedWritingTokens {
    private val pattern = Regex(
        "(?:https?://|www\\.)\\S+|[A-Za-z0-9]+(?:[._:/\\\\@#%+&=\\-][A-Za-z0-9]+)+|[A-Za-z][A-Za-z0-9]*|\\p{Sc}?[+−-]?[0-9۰-۹٠-٩]+(?:[./:٫٬,_-][0-9۰-۹٠-٩]+)*[%٪]?"
    )

    fun extract(text: String): List<String> = pattern.findAll(text).map {
        it.value.trimEnd('.', ',', '،', '؛', ';', '!', '?', '؟', ')', '»')
    }.toList()
}
