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
import java.util.function.Function;
import jp.llv.reflective.Refl;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author Toyblocks
 */
public class Saddled extends JavaPlugin implements Listener {

    private final Set<Item> watching = new HashSet<>();
    private static final Function<Location, Item> cushion = (l) -> {
        Item item = l.getWorld().dropItem(l, new ItemStack(Material.STONE_BUTTON));
        item.setPickupDelay(32767);
        Refl.wrap(item).invoke("getHandle").set("ticksLived", -32768);
        return item;
    };

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getScheduler().runTaskTimer(this, this::removeUnnecessaryCushion, 20L, 10L);
    }

    @Override
    public void onDisable() {
        watching.forEach(Saddled::removeEntity);
    }

    @EventHandler
    public void on(PlayerInteractAtEntityEvent eve) {
        ItemStack is = eve.getPlayer().getItemInHand();
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
            Item c1 = cushion.apply(riden.getLocation()),
                    c2 = cushion.apply(riden.getLocation()),
                    c3 = cushion.apply(riden.getLocation());
            watching.add(c1);
            watching.add(c2);
            watching.add(c3);
            riden.setPassenger(c1);
            c1.setPassenger(c2);
            c2.setPassenger(c3);
            c3.setPassenger(rider);
        } else {
            riden.setPassenger(rider);
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
                return;
            case SPECTATOR:
                return;
        }
        if (is.getAmount() <= 1) {
            eve.getPlayer().setItemInHand(null);
        } else {
            is.setAmount(is.getAmount() - 1);
        }
    }

    @EventHandler
    public void on(PlayerDropItemEvent e) {
        if (e.getPlayer().getPassenger() == null) {
            return;
        }
        if (!e.getPlayer().hasPermission("saddled.drop")) {
            return;
        }
        e.setCancelled(true);
        e.getPlayer().eject();
        PlayerUtil.sendMessage(ChatMessageType.ACTION_BAR, e.getPlayer(), "降ろしました");
    }
    
    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getDamager();
        if (p.getItemInHand().getType() != Material.BONE) {
            return;
        }
        if (!p.hasPermission("saddled.break")) {
            return;
        }
        List<Entity> totem = new ArrayList<>();
        totem.add(e.getEntity());
        totem.addAll(getVehicles(e.getEntity()));
        totem.addAll(getRiders(e.getEntity()));
        if (totem.stream().map(Entity::getType).anyMatch(t -> t==EntityType.PLAYER)) {
            return;
        }
        if (totem.size() <= 1) {
            return;
        }
        e.setCancelled(true);
        totem.forEach(Entity::eject);
        PlayerUtil.sendMessage(ChatMessageType.ACTION_BAR, p, "分離しました");
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
                } else if (riders.get(riders.size() - 1).getType() == EntityType.DROPPED_ITEM) {
                    removeEntity(i);
                    it.remove();
                } else if (vehicles.get(vehicles.size() - 1).getType() != EntityType.PLAYER) {
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

    public static List<Entity> getRiders(Entity e) {
        List<Entity> result = new ArrayList<>();
        Entity c = e;
        while (c.getPassenger() != null) {
            c = c.getPassenger();
            result.add(c);
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
            vehicle.setPassenger(passenger);
        }
    }

}
