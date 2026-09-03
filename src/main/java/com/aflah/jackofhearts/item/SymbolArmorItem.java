package com.aflah.jackofhearts.item;

import com.aflah.jackofhearts.JackOfHeartsMod;
import com.aflah.jackofhearts.client.ClientOnlyHooks;
import com.aflah.jackofhearts.game.Symbol;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Baju / celana seragam. Chestplate menyimpan simbol pemain di NBT dan
 * merender simbol itu di patch punggung. Pemakainya sendiri selalu melihat "?".
 */
public class SymbolArmorItem extends ArmorItem {
    public static final String SYMBOL_TAG = "JackSymbol";

    public SymbolArmorItem(ArmorItem.Type type, Properties properties) {
        super(JackArmorMaterial.UNIFORM, type, properties);
    }

    public static ItemStack withSymbol(ItemStack stack, @Nullable Symbol symbol) {
        CompoundTag tag = stack.getOrCreateTag();
        if (symbol == null) {
            tag.remove(SYMBOL_TAG);
        } else {
            tag.putString(SYMBOL_TAG, symbol.getId());
        }
        return stack;
    }

    @Nullable
    public static Symbol getSymbol(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : Symbol.byId(tag.getString(SYMBOL_TAG));
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        if (slot == EquipmentSlot.LEGS) {
            return JackOfHeartsMod.MOD_ID + ":textures/models/armor/uniform_layer_2.png";
        }
        Symbol symbol = getSymbol(stack);
        // Pemakainya sendiri tidak boleh tahu simbolnya: render kartu tanda tanya.
        if (symbol == null || (FMLEnvironment.dist == Dist.CLIENT && ClientOnlyHooks.isLocalPlayer(entity))) {
            return JackOfHeartsMod.MOD_ID + ":textures/models/armor/uniform_unknown_layer_1.png";
        }
        // Di awal tiap ronde simbol semua orang diacak ulang; selama animasinya
        // berjalan yang ditampilkan berganti-ganti dulu sebelum menetap.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            String shuffling = ClientOnlyHooks.shufflingSymbolId();
            if (shuffling != null) {
                return JackOfHeartsMod.MOD_ID + ":textures/models/armor/uniform_" + shuffling + "_layer_1.png";
            }
        }
        return JackOfHeartsMod.MOD_ID + ":textures/models/armor/uniform_" + symbol.getId() + "_layer_1.png";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.jackofhearts.uniform").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.jackofhearts.uniform.hidden").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(
                    net.minecraft.world.entity.LivingEntity entity,
                    ItemStack stack,
                    EquipmentSlot slot,
                    net.minecraft.client.model.HumanoidModel<?> original) {
                return com.aflah.jackofhearts.client.ClientSetup.getArmorModel(slot, original);
            }
        });
    }
}
