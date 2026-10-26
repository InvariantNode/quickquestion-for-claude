package io.github.invariantnode.quickquestion

/** Builds the text of a user message. Kept free of platform classes so it is easy to test. */
object MessageComposer {
    /**
     * Puts [selectionBlock] in front of [question], unless it is null or identical to [lastSentBlock]
     * (the same selection was already sent earlier in this chat).
     */
    fun compose(question: String, selectionBlock: String?, lastSentBlock: String?): String =
        if (selectionBlock != null && selectionBlock != lastSentBlock) "$selectionBlock\n\n$question" else question
}
