package com.rakey.sneakmeow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SneakMeowPlugin extends JavaPlugin implements Listener {

    private Set<Material> triggerItems;
    private String meowSound;
    private double soundVolume;
    private float soundPitch;
    private boolean showActionbar;
    private int cooldown;
    private int hearDistance;
    private boolean sendChatMessage;
    private String chatMessage;

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();

        // 加载配置
        reloadPluginConfig();

        // 注册事件监听器 - 关键修复：确保监听器正确注册
        Bukkit.getPluginManager().registerEvents(this, this);

        // 注册命令执行器
        Objects.requireNonNull(getCommand("sneakmeowreload")).setExecutor((sender, command, label, args) -> {
            if (sender.hasPermission("sneakmeow.reload")) {
                reloadPlugin();
                sender.sendMessage(Component.text("§aSneakMeow 配置已重新加载！"));
            } else {
                sender.sendMessage(Component.text("§c你没有权限执行此命令！"));
            }
            return true;
        });

        getLogger().info("SneakMeow 插件已启动！");
        getLogger().info("持鱼潜行喵叫功能已激活");
    }

    @Override
    public void onDisable() {
        getLogger().info("SneakMeow 插件已卸载");
    }

    private void reloadPluginConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();

        // 加载触发物品
        triggerItems = new HashSet<>();
        List<String> itemNames = config.getStringList("trigger-items");
        for (String itemName : itemNames) {
            try {
                Material material = Material.valueOf(itemName.toUpperCase());
                triggerItems.add(material);
            } catch (IllegalArgumentException e) {
                getLogger().warning("未知的物品类型: " + itemName);
            }
        }

        // 加载声音配置
        meowSound = config.getString("meow-sound", "ENTITY_CAT_AMBIENT");
        soundVolume = config.getDouble("sound-volume", 1.0);
        soundPitch = (float) config.getDouble("sound-pitch", 1.2);

        // 加载其他配置
        showActionbar = config.getBoolean("show-actionbar", true);
        cooldown = config.getInt("cooldown", 3);
        hearDistance = config.getInt("hear-distance", 16);
        sendChatMessage = config.getBoolean("send-chat-message", true);
        chatMessage = config.getString("chat-message", "&e🐱 &6%player% &f发出了可爱的喵叫声！");
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        // 只在开始潜行时触发，结束潜行不触发
        if (!event.isSneaking()) {
            return;
        }

        // 检查权限
        if (!player.hasPermission("sneakmeow.use")) {
            return;
        }

        // 检查冷却
        if (isOnCooldown(player)) {
            return;
        }

        // 检查主手物品
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // 检查是否持有触发物品（主手或副手）
        if (isTriggerItem(mainHand) || isTriggerItem(offHand)) {
            // 触发喵叫
            triggerMeow(player);
        }
    }

    private boolean isTriggerItem(ItemStack item) {
        return item != null && triggerItems.contains(item.getType());
    }

    private boolean isOnCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            long lastTime = cooldowns.get(playerId);
            long elapsed = (currentTime - lastTime) / 1000;

            if (elapsed < cooldown) {
                if (showActionbar) {
                    int remaining = cooldown - (int) elapsed;
                    // 使用新的 Adventure API 发送动作栏
                    player.sendActionBar(Component.text("§c喵叫冷却中... §7(" + remaining + "秒)"));
                }
                return true;
            }
        }

        cooldowns.put(playerId, currentTime);
        return false;
    }

    private void triggerMeow(Player player) {
        // 播放声音
        try {
            Sound sound = Sound.valueOf(meowSound);
            player.getWorld().playSound(player.getLocation(), sound, (float) soundVolume, soundPitch);

            // 让附近的玩家也能听到
            for (Player nearby : player.getWorld().getPlayers()) {
                if (nearby != player && nearby.getLocation().distance(player.getLocation()) <= hearDistance) {
                    nearby.playSound(nearby.getLocation(), sound, (float) soundVolume, soundPitch);
                }
            }
        } catch (IllegalArgumentException e) {
            getLogger().warning("未知的声音类型: " + meowSound);
            // 使用默认的猫叫声
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, (float) soundVolume, soundPitch);
        }

        // 显示动作栏提示（使用新的 Adventure API）
        if (showActionbar) {
            player.sendActionBar(Component.text("§a🐱 喵呜~"));
        }

        // 发送聊天消息（使用新的 Adventure API）
        if (sendChatMessage) {
            String message = chatMessage.replace("%player%", player.getName());
            Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);

            // 异步发送消息，避免阻塞事件线程
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 使用新的广播方法
                    Bukkit.getServer().sendMessage(component);
                }
            }.runTask(this);
        }

        getLogger().info(player.getName() + " 在持鱼状态下发出了喵叫声");
    }

    // 重新加载配置的方法
    private void reloadPlugin() {
        reloadPluginConfig();
        getLogger().info("SneakMeow 插件配置已重新加载！");
    }
}