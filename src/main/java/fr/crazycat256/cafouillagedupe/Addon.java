package fr.crazycat256.cafouillagedupe;

import com.mojang.logging.LogUtils;
import fr.crazycat256.cafouillagedupe.commands.*;
import fr.crazycat256.cafouillagedupe.modules.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Addon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("Initializing AutoFrameDupe");

        // Modules
        Modules.get().add(new AutoFrameDupe());

        // Commands
        Commands.add(new ItemFrameDrop());

    }

    @Override
    public String getPackage() {
        return "fr.crazycat256.cafouillagedupe";
    }
}
