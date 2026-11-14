package com.timestop.prediction;

import com.timestop.mixin.PredictionCreeperAccessor;
import com.timestop.mixin.PredictionFireballAccessor;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;

/** Copied impact metadata. No mutable item, live timer, or random source is retained. */
public final class PredictionEffects {
    public enum Kind { NONE, SPLASH, LINGERING, WATER, EXPLOSION, TNT, CREEPER }
    public record Effect(ResourceLocation id, int duration, int amplifier) {}
    public record Profile(Kind kind, ResourceLocation item, int color, float power, int fuse,
                          boolean conditional, List<Effect> effects) {
        public Profile {
            effects = List.copyOf(effects);
            if (effects.size()>16 || !Float.isFinite(power) || power<0 || power>128 || fuse<0 || fuse>32767)
                throw new IllegalArgumentException("Invalid prediction effect profile");
        }
        public void write(FriendlyByteBuf b) {
            b.writeEnum(kind);b.writeResourceLocation(item);b.writeInt(color);b.writeFloat(power);
            b.writeVarInt(fuse);b.writeBoolean(conditional);b.writeVarInt(effects.size());
            for(var e:effects) {b.writeResourceLocation(e.id());b.writeVarInt(e.duration());b.writeVarInt(e.amplifier());}
        }
        public static Profile read(FriendlyByteBuf b) {
            var kind=b.readEnum(Kind.class);var item=b.readResourceLocation();int color=b.readInt();float power=b.readFloat();
            int fuse=b.readVarInt();boolean conditional=b.readBoolean();int count=b.readVarInt();
            if(count<0 || count>16) throw new IllegalArgumentException("Invalid effect count");
            var effects=new java.util.ArrayList<Effect>();
            for(int i=0;i<count;i++) {
                var id=b.readResourceLocation();int duration=b.readVarInt(),amplifier=b.readVarInt();
                if(duration < -1 || amplifier<0 || amplifier>255) throw new IllegalArgumentException("Invalid potion effect");
                effects.add(new Effect(id,duration,amplifier));
            }
            return new Profile(kind,item,color,power,fuse,conditional,effects);
        }
    }
    public static final Profile NONE=new Profile(Kind.NONE,ResourceLocation.fromNamespaceAndPath("minecraft","air"),0xFFFFFF,0,0,false,List.of());
    private PredictionEffects() {}
    public static Profile item(ItemStack item) {
        Kind kind=Kind.NONE;
        var contents=item.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,PotionContents.EMPTY);
        var effects=java.util.stream.StreamSupport.stream(contents.getAllEffects().spliterator(),false).toList();
        if(item.is(Items.LINGERING_POTION) || item.is(Items.SPLASH_POTION)) {
            if(!effects.isEmpty()) kind=item.is(Items.LINGERING_POTION)?Kind.LINGERING:Kind.SPLASH;
            else if(contents.is(Potions.WATER)) kind=Kind.WATER;
        }
        var copied=effects.stream().limit(16).map(e -> new Effect(BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect().value()),e.getDuration(),e.getAmplifier())).toList();
        return new Profile(kind,BuiltInRegistries.ITEM.getKey(item.getItem()),kind==Kind.NONE?0xFFFFFF:contents.getColor(),0,0,false,copied);
    }
    public static Profile capture(Entity entity) {
        if(entity instanceof ThrownPotion p) return item(p.getItem());
        if(entity instanceof ThrowableItemProjectile p) return item(p.getItem());
        if(entity instanceof LargeFireball f) return blast(Kind.EXPLOSION,((PredictionFireballAccessor)f).timestop$explosionPower(),0,false);
        if(entity instanceof PrimedTnt t) return blast(Kind.TNT,4,t.getFuse(),false);
        if(entity instanceof Creeper c && (c.isIgnited() || c.getSwellDir()>0)) {
            var a=(PredictionCreeperAccessor)c;
            return blast(Kind.CREEPER,a.timestop$explosionRadius()*(c.isPowered()?2:1),Math.max(0,a.timestop$maxSwell()-a.timestop$swell()),!c.isIgnited());
        }
        return NONE;
    }
    private static Profile blast(Kind kind,int power,int fuse,boolean conditional) {
        return new Profile(kind,NONE.item(),0xFF8B87,Math.min(128,Math.max(0,power)),Math.min(32767,Math.max(0,fuse)),conditional,List.of());
    }
}
