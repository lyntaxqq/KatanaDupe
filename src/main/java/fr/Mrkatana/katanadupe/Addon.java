package fr.Mrkatana.katanadupe;

import com.mojang.logging.LogUtils;
import fr.Mrkatana.katanadupe.commands.ItemFrameDrop;
import fr.Mrkatana.katanadupe.modules.AutoFrameDupe;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Addon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("Initializing KatanaDupe");

        Modules.get().add(new AutoFrameDupe());
        Commands.add(new ItemFrameDrop());
    }

    @Override
    public String getPackage() {
        return "fr.Mrkatana.katanadupe";
    }
}
