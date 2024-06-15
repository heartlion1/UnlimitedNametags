package org.alexdev.unlimitednametags;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.google.common.collect.Maps;
import com.jonahseguin.drink.CommandService;
import com.jonahseguin.drink.Drink;
import lombok.Getter;
import org.alexdev.unlimitednametags.api.UNTAPI;
import org.alexdev.unlimitednametags.commands.MainCommand;
import org.alexdev.unlimitednametags.config.ConfigManager;
import org.alexdev.unlimitednametags.events.*;
import org.alexdev.unlimitednametags.hook.Hook;
import org.alexdev.unlimitednametags.hook.MiniPlaceholdersHook;
import org.alexdev.unlimitednametags.hook.OraxenHook;
import org.alexdev.unlimitednametags.hook.TypeWriterListener;
import org.alexdev.unlimitednametags.nametags.NameTagManager;
import org.alexdev.unlimitednametags.packet.PacketManager;
import org.alexdev.unlimitednametags.placeholders.PlaceholderManager;
import org.alexdev.unlimitednametags.vanish.VanishManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

@Getter
public final class UnlimitedNameTags extends JavaPlugin {

    private boolean isPaper;
    private ConfigManager configManager;
    private NameTagManager nametagManager;
    private PlaceholderManager placeholderManager;
    private VanishManager vanishManager;
    private PacketEventsListener packetEventsListener;
    private PacketManager packetManager;
    private PlayerListener playerListener;
    private TrackerManager trackerManager;
    private Map<Class<? extends Hook>, Hook> hooks;
    private TaskScheduler taskScheduler;

    @Override
    public void onLoad() {
        hooks = Maps.newConcurrentMap();
    }

    @Override
    public void onEnable() {
        isPaper = isPaperSupported();

        taskScheduler = UniversalScheduler.getScheduler(this);
        configManager = new ConfigManager(this);
        if (!loadConfig()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        trackerManager = new TrackerManager(this);
        nametagManager = new NameTagManager(this);
        placeholderManager = new PlaceholderManager(this);
        vanishManager = new VanishManager(this);
        packetManager = new PacketManager(this);


        loadCommands();
        loadListeners();
        loadHooks();

        UNTAPI.register(this);
        getLogger().info("API registered");
        getLogger().info("UnlimitedNameTags has been enabled!");
    }

    private boolean loadConfig() {
        final Optional<Throwable> error = configManager.loadConfigs();
        if (error.isPresent()) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to load configuration", error.get());
            return false;
        }
        return true;
    }

    private void loadListeners() {
        playerListener = new PlayerListener(this);
        Bukkit.getPluginManager().registerEvents(playerListener, this);

        if (isPaper) {
            getLogger().info("Paper found, using Paper's tracker");
            Bukkit.getPluginManager().registerEvents(new PaperTrackerListener(this), this);
        } else {
            if (!isCorrectSpigotVersion()) {
                getLogger().severe("Unsupported Spigot version, please use 1.20.2 or higher");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            getLogger().info("Paper not found, using Spigot's tracker");
            new SpigotTrackerListener(this);
        }

        getLogger().info("PacketEvents found, hooking into it");
        packetEventsListener = new PacketEventsListener(this);
        packetEventsListener.onEnable();
    }

//    private void loadLibs() {
//        final BukkitLibraryManager libraryManager = new BukkitLibraryManager(this);
//        final List<Library> libraries = List.of(
//                Library.builder()
//                        .groupId("com.github.Anon8281")
//                        .artifactId("universalScheduler")
//                        .version("1.0.0")
//                        .build(),
//        );
//
//    }

    private boolean isCorrectSpigotVersion() {
        final String version = Bukkit.getServer().getBukkitVersion().split("-")[0];
        final String[] split = version.split("\\.");
        if (split.length < 2) {
            return false;
        }

        final int major = Integer.parseInt(split[1]);
        final int minor = Integer.parseInt(split[2]);

        if (major < 20) {
            return false;
        } else return major != 20 || minor >= 2;
    }

    private boolean isPaperSupported() {
        try {
            Class.forName("io.papermc.paper.event.player.PlayerTrackEntityEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void loadHooks() {
        if (Bukkit.getPluginManager().isPluginEnabled("TypeWriter")) {
            hooks.put(TypeWriterListener.class, new TypeWriterListener(this));
            getLogger().info("TypeWriter found, hooking into it");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            hooks.put(MiniPlaceholdersHook.class, new MiniPlaceholdersHook(this));
            getLogger().info("MiniPlaceholders found, hooking into it");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("Oraxen")) {
            hooks.put(OraxenHook.class, new OraxenHook(this));
            getLogger().info("Oraxen found, hooking into it");
        }

        hooks.values().forEach(Hook::onEnable);
    }

    private void loadCommands() {
        final CommandService drink = Drink.get(this);

        drink.register(new MainCommand(this), "unt", "unlimitednametags");
        drink.registerCommands();
    }

    public <H extends Hook> Optional<H> getHook(@NotNull Class<H> hookType) {
        return Optional.ofNullable(hooks.get(hookType)).map(hookType::cast);
    }

    @Override
    public void onDisable() {
        UNTAPI.unregister();

        hooks.values().forEach(Hook::onDisable);

        trackerManager.onDisable();
        packetEventsListener.onDisable();
        nametagManager.removeAll();
        placeholderManager.close();
        packetManager.close();
        taskScheduler.cancelTasks();
    }

}
