package moe.frisk.myhooks

import moe.frisk.myhooks.qq.MultiForwardAvatarUrlHook

object HookRegistry {
    val hooks: List<AppHook> = listOf(
        MultiForwardAvatarUrlHook(),
    )
}
