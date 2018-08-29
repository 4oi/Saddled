/* 
 * Copyright (C) 2015 Toyblocks
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jp.llv.saddled;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 *
 * @author Toyblocks
 */
public class Saddled extends JavaPlugin implements Listener {

    private static final Material CUSHION_MATERIAL = Material.STONE_BUTTON;

    private final Set<Item> watching = new HashSet<>();

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getScheduler().runTaskTimer(this, this::removeUnnecessaryCushion, 20L, 10L);
    }

    @Override
    public void onDisable() {
        watching.forEach(Saddled::removeEntity);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(PlayerInteractAtEntityEvent eve) {
        ItemStack is;
        switch (eve.getHand()) {
            case HAND:
                is = eve.getPlayer().getInventory().getItemInMainHand();
                break;
            case OFF_HAND:
                is = eve.getPlayer().getInventory().getItemInOffHand();
                break;
            default:
                return;
        }
        if (is.getType() != Material.BONE) {
            return;
        }
        eve.setCancelled(true);

        Entity rider;//乗る側
        Entity riden;//乗られる側
        String perm;

        if (eve.getPlayer().isSneaking()) {//スニーク中なら頭にのせる
            perm = "saddled.puton." + eve.getRightClicked().getType().toString().toLowerCase();
            rider = eve.getRightClicked();
            riden = eve.getPlayer();
        } else {//そうでないなら乗る
            perm = "saddled.rideon." + eve.getRightClicked().getType().toString().toLowerCase();
            rider = eve.getPlayer();
            riden = eve.getRightClicked();
        }

        if (!eve.getPlayer().hasPermission(perm)) {
            return;
        }

        List<Entity> ridersOfRiden = getRiders(riden);

        if (riden == rider || ridersOfRiden.contains(rider) || getVehicles(riden).contains(rider)) {
            PlayerUtil.sendMessage(ChatMessageType.ACTION_BAR, eve.getPlayer(), "既に乗っています");
            return;
        }

        riden = ridersOfRiden.isEmpty() ? riden : ridersOfRiden.get(ridersOfRiden.size() - 1);//実際には乗れる人にターゲットをスイッチ
        if (riden instanceof Player) {
            Entity e = riden;
            Item c = e.getWorld().dropItem(e.getLocation(), new ItemStack(CUSHION_MATERIAL));
            c.setPickupDelay(32767);
            c.setTicksLived(Integer.MAX_VALUE);
            watching.add(c);
            e.addPassenger(c);
            c.addPassenger(rider);
        } else {
            riden.addPassenger(rider);
        }

        getVehicles(rider).stream().filter(e -> e instanceof Player)
                .forEach(p -> PlayerUtil.sendMessage(ChatMessageType.ACTION_BAR, (Player) p,
                rider.getName() + "に乗られました"));
        if (rider instanceof Player) {
            PlayerUtil.sendMessage(ChatMessageType.ACTION_BAR, (Player) rider,
                    riden.getName() + "に乗りました");
        }

        GameMode gm = eve.getPlayer().getGameMode();
        switch (gm) {
            case CREATIVE:
            case SPECTATOR:
                return;
        }
        if (is.getAmount() <= 1) {
            eve.getPlayer().getInventory().remove(is);
        } else {
            is.setAmount(is.getAmount() - 1);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(PlayerDropItemEvent e) {
        if (e.getPlayer().getPassengers().isEmpty()) {
            return;
        }
        if (!e.getPlayer().hasPermission(
                "saddled.drop." + e.getPlayer().getPassenger().getType().toString().toLowerCase()
        )) {
            return;
        }
        e.setCancelled(true);
        e.getPlayer().eject();
        e.getPlayer().getPassengers().forEach(entity -> entity.setFallDistance(0));
        PlayerUtil.sendMessage(ChatMessageType.ACTION_BAR, e.getPlayer(), "降ろしました");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getDamager();
        if (getRiders(e.getDamager()).contains(e.getEntity())) {
            e.setCancelled(true);
            if (p.hasPermission("saddled.pitch")) {
                Entity ent = e.getEntity();
                ent.getVehicle().eject();
                ent.setVelocity(p.getEyeLocation().getDirection().add(new Vector(0.0, 0.6, 0.0).normalize()));
                ent.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, ent.getLocation(), 1, 0.0, 0.0, 0.0, 0.0);
                ent.getWorld().playSound(ent.getLocation(), Sound.ENTITY_ENDERDRAGON_FLAP, 2.0f, 2.0f);
            }
            return;
        }
        ItemStack is = p.getInventory().getItemInMainHand();
        if (is.getType() != Material.BONE) {
            return;
        }

        if (p.isSneaking()) {
            if (!p.hasPermission("saddled.reverse")) {
                return;
            }
            List<Entity> totem = new ArrayList<>();
            totem.addAll(getRiders(e.getEntity()));
            Collections.reverse(totem);
            totem.add(e.getEntity());
            totem.addAll(getVehicles(e.getEntity()));
            if (totem.stream().map(Entity::getType).anyMatch(t -> t == EntityType.PLAYER)) {
                return;
            }
            if (totem.size() <= 1) {
                return;
            }
            e.setCancelled(true);
            totem.forEach(Entity::eject);
            for (int i = 0; i < totem.size() - 1; i++) {
                totem.get(i).addPassenger(totem.get(i + 1));
            }
            PlayerUtil.sendMessage(ChatMessageType.ACTION_BAR, p, "反転しました");
        } else {
            if (!p.hasPermission("saddled.break")) {
                return;
            }
            List<Entity> totem = new ArrayList<>();
            totem.add(e.getEntity());
            totem.addAll(getVehicles(e.getEntity()));
            totem.addAll(getRiders(e.getEntity()));
            if (totem.stream().map(Entity::getType).anyMatch(t -> t == EntityType.PLAYER)) {
                return;
            }
            if (totem.size() <= 1) {
                return;
            }
            e.setCancelled(true);
            totem.forEach(entity -> {
                entity.eject();
                entity.setFallDistance(0);
            });
            PlayerUtil.sendMessage(ChatMessageType.ACTION_BAR, p, "分離しました");
        }

        GameMode gm = p.getGameMode();
        switch (gm) {
            case CREATIVE:
                return;
            case SPECTATOR:
                return;
        }
        if (is.getAmount() <= 1) {
            p.getInventory().setItemInMainHand(null);
        } else {
            is.setAmount(is.getAmount() - 1);
        }
    }

    public void removeUnnecessaryCushion() {
        Iterator<Item> it = this.watching.iterator();
        while (it.hasNext()) {
            Item i = it.next();
            if (!i.getLocation().getChunk().isLoaded()) {
                removeEntity(i);
                it.remove();
            } else if (i.getVehicle() == null) {
                removeEntity(i);
                it.remove();
            } else if (i.getVehicle().getType() == EntityType.DROPPED_ITEM) {
                List<Entity> riders = getRiders(i), vehicles = getVehicles(i);
                if (riders.isEmpty() || vehicles.isEmpty()) {
                    removeEntity(i);
                    it.remove();
                } else if (riders.stream().map(Entity::getType).allMatch(EntityType.DROPPED_ITEM::equals)) {
                    removeEntity(i);
                    it.remove();
                } else if (vehicles.stream().map(Entity::getType).allMatch(EntityType.DROPPED_ITEM::equals)) {
                    removeEntity(i);
                    it.remove();
                }
            } else if (i.getVehicle().getType() != EntityType.PLAYER) {
                removeEntity(i);
                it.remove();
            } else if (i.getPassenger() == null) {
                removeEntity(i);
                it.remove();
            }
        }
    }

    public static void removeEntity(Entity e) {
        Chunk c = e.getLocation().getChunk();
        if (c.isLoaded()) {
            e.remove();
        } else {
            c.load(false);
            e.remove();
            c.unload(true);
        }
    }

    public static List<Entity> getRiders(Entity entity) {
        List<Entity> result = new ArrayList<>();
        for (Entity e : entity.getPassengers()) {
            result.add(e);
            result.addAll(getRiders(e));
        }
        return Collections.unmodifiableList(result);
    }

    public static List<Entity> getVehicles(Entity e) {
        List<Entity> result = new ArrayList<>();
        Entity c = e;
        while (c.getVehicle() != null) {
            c = c.getVehicle();
            result.add(c);
        }
        return Collections.unmodifiableList(result);
    }

    public static void setPassenger(Entity vehicle, Entity passenger) {
        if (passenger instanceof Player) {
            ArmorStand as = vehicle.getWorld().spawn(vehicle.getLocation(), ArmorStand.class);
            as.setSmall(true);
            as.setVisible(false);
            as.setCustomNameVisible(false);
        } else {
            vehicle.addPassenger(passenger);
        }
    }

}
