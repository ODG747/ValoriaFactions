package com.massivecraft.factions.util;

import com.massivecraft.factions.FactionsPlugin;

public final class ValoriaModules {

    private ValoriaModules() {
    }

    public static boolean enabled(String module) {
        return enabled(module, false);
    }

    public static boolean enabled(String module, boolean defaultValue) {
        return FactionsPlugin.getInstance().getConfig().getBoolean("modules." + module, defaultValue);
    }
}
