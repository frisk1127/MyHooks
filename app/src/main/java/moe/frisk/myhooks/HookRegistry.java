package moe.frisk.myhooks;

import moe.frisk.myhooks.bili.AutoClickViewOriginalImageHook;
import moe.frisk.myhooks.qq.MultiForwardAvatarUrlHook;
import moe.frisk.myhooks.systemui.HyperOsNavBarHook;

public final class HookRegistry {

    private HookRegistry() {
    }

    public static final AppHook[] HOOKS = new AppHook[]{
        new MultiForwardAvatarUrlHook(),
        new AutoClickViewOriginalImageHook(),
        new HyperOsNavBarHook()
    };
}
