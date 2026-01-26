package com.julflips.nerv_printer.utils;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class SlaveTableController {
    private final WTable table;
    private final GuiTheme theme;
    private final boolean staircased;

    public SlaveTableController(WTable table, GuiTheme theme, boolean staircased) {
        this.table = table;
        this.theme = theme;
        this.staircased = staircased;
    }

    public void rebuild() {
        table.clear();

        if (SlaveSystem.isSlave()) {
            table.add(theme.label("Slave user can not control other slaves."));
            table.row();
            return;
        }


        table.add(theme.label("Multi-User: "));

        WButton register = table.add(theme.button("Register players in range")).widget();
        register.action = SlaveSystem::registerSlaves;

        WButton pause = table.add(theme.button("Pause all")).widget();
        pause.action = () -> {
            SlaveSystem.pauseAllSlaves();
            rebuild();
        };

        WButton start = table.add(theme.button("Start all")).widget();
        start.action = () -> {
            SlaveSystem.startAllSlaves();
            rebuild();
        };

        if (staircased) {
            WButton skipNextBuilding = table.add(theme.button("Skip next building")).widget();
            skipNextBuilding.action = () -> {
                SlaveSystem.skipNextBuilding();
                rebuild();
            };
        }

        table.row();

        if (!SlaveSystem.slaves.isEmpty()) {
            table.add(theme.horizontalSeparator("Slaves")).expandX();
            table.row();
        }

        for (String slave : SlaveSystem.slaves) {
            WLabel name = table.add(theme.label(slave)).expandCellX().widget();

            WCheckbox visible = table.add(theme.checkbox(SlaveSystem.activeSlavesDict.get(slave))).widget();
            visible.action = () -> {
                SlaveSystem.activeSlavesDict.put(slave, visible.checked);
                SlaveSystem.queueDM(slave, visible.checked ? "start" : "pause");
                rebuild();
            };

            if (!visible.checked) name.color = Color.GRAY;

            WMinus remove = table.add(theme.confirmedMinus()).widget();
            remove.action = () -> {
                SlaveSystem.removeSlave(slave);
                rebuild();
            };

            table.row();
        }
    }
}
