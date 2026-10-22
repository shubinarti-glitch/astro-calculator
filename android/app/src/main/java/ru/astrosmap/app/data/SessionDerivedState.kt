package ru.astrosmap.app.data

/** Device preferences are preserved; derived account access is never inherited by another owner. */
internal object SessionDerivedState {
    fun premiumAllowed(currentOwner: String?, cachedOwner: String?, cachedPremium: Boolean): Boolean =
        currentOwner?.matches(Regex("user:[1-9][0-9]*")) == true &&
            currentOwner == cachedOwner && cachedPremium

    fun clearNotices(previousOwner: String?, currentOwner: String?): Boolean =
        currentOwner == null || previousOwner != currentOwner
}
