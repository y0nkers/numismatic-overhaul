package com.glisco.numismaticoverhaul.network;

import com.glisco.numismaticoverhaul.ModComponents;
import com.glisco.numismaticoverhaul.NumismaticOverhaul;
import com.glisco.numismaticoverhaul.currency.CurrencyStack;
import com.glisco.numismaticoverhaul.item.CoinItem;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RequestPurseActionC2SPacket {

    public static final Identifier ID = new Identifier(NumismaticOverhaul.MOD_ID, "request-purse-action");
    private static final Logger LOGGER = LogManager.getLogger();

    public static void onPacket(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buffer, PacketSender sender) {

        int[] values = buffer.readIntArray();

        Action action = Action.values()[values[0]];

        server.execute(() -> {
            if (player.currentScreenHandler instanceof PlayerScreenHandler) {
                if (action == Action.STORE_ALL) {

                    //Iterate through all items in the player's inventory and put them into the purse if they're coins
                    for (int i = 0; i < player.inventory.size(); i++) {
                        ItemStack stack = player.inventory.getStack(i);
                        if (!(stack.getItem() instanceof CoinItem)) continue;

                        CoinItem currency = (CoinItem) stack.getItem();
                        ModComponents.CURRENCY.get(player).pushTransaction(currency.currency.getRawValue(stack.getCount()));

                        player.inventory.removeOne(stack);
                    }

                    ModComponents.CURRENCY.get(player).commitTransactions();

                } else if (action == Action.EXTRACT) {

                    //Check if we can actually extract this much money to prevent cheeky packet forgery
                    if (ModComponents.CURRENCY.get(player).getValue() < values[1]) return;

                    CurrencyStack currencyStack = new CurrencyStack(values[1]);
                    currencyStack.getAsItemStackList().forEach(itemStack -> player.inventory.offerOrDrop(player.world, itemStack));

                    ModComponents.CURRENCY.get(player).modify(-values[1]);
                } else if (action == Action.EXTRACT_ALL) {
                    CurrencyStack currencyStack = new CurrencyStack(ModComponents.CURRENCY.get(player).getValue());
                    CurrencyStack.splitAtMaxCount(currencyStack.getAsItemStackList()).forEach(itemStack -> player.inventory.offerOrDrop(player.world, itemStack));

                    ModComponents.CURRENCY.get(player).modify(-ModComponents.CURRENCY.get(player).getValue());
                }
            }
        });
    }

    public static Packet<?> create(Action action) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        int[] values = new int[]{action.ordinal()};
        buffer.writeIntArray(values);
        return ClientPlayNetworking.createC2SPacket(ID, buffer);
    }

    public static Packet<?> create(Action action, int value) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        int[] values = new int[]{action.ordinal(), value};
        buffer.writeIntArray(values);
        return ClientPlayNetworking.createC2SPacket(ID, buffer);
    }

    public enum Action {
        STORE_ALL, EXTRACT, EXTRACT_ALL
    }
}