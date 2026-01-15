package com.julflips.nerv_printer;

import com.julflips.nerv_printer.modules.CarpetPrinter;
import com.julflips.nerv_printer.modules.FullBlockPrinter;
import com.julflips.nerv_printer.modules.MapNamer;
import com.julflips.nerv_printer.modules.StaircasedPrinter;
import com.julflips.nerv_printer.utils.MapAreaCache;
import com.julflips.nerv_printer.utils.SlaveSystem;
import com.julflips.nerv_printer.utils.Utils;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Addon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Nerv Printer");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Nerv Printer");
        // Subscribe Util classes to Events
        MeteorClient.EVENT_BUS.subscribe(Utils.class);
        MeteorClient.EVENT_BUS.subscribe(MapAreaCache.class);
        MeteorClient.EVENT_BUS.subscribe(SlaveSystem.class);

        // Modules
        Modules.get().add(new CarpetPrinter());
        Modules.get().add(new FullBlockPrinter());
        Modules.get().add(new StaircasedPrinter());
        Modules.get().add(new MapNamer());

    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.julflips.nerv_printer";
    }
}
