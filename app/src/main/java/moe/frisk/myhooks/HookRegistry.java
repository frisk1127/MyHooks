package moe.frisk.myhooks;

import moe.frisk.myhooks.qq.MultiForwardAvatarUrlHook;

public final class HookRegistry {

    private HookRegistry() {
    }

    public static final AppHook[] HOOKS = new AppHook[]{
        new MultiForwardAvatarUrlHook()
    };
}
