package moe.frisk.myhooks.dexkit;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.result.MethodData;

public interface DexKitMethodLocator {

    String getCacheKey();

    MethodData find(DexKitBridge bridge) throws Exception;
}
