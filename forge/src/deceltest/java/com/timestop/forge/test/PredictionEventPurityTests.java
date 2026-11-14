package com.timestop.forge.test;

import com.timestop.prediction.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("timestop")
@PrefixGameTestTemplate(false)
public class PredictionEventPurityTests {
    public static final class ApplicationSpy {
        int calls;
        @SubscribeEvent public void applicable(MobEffectEvent.Applicable event) {calls++;}
    }
    @GameTest(template="deceleration_empty",batch="prediction_outcomes")
    public static void forecastsNeverDispatchPotionApplicationEvents(GameTestHelper h) {
        var p=h.absolutePos(new BlockPos(4,4,4));var pos=new Vec3(p.getX()+.5,220,p.getZ()+.5);h.getLevel().getChunkAt(BlockPos.containing(pos));
        var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(pos.add(1,0,0));h.getLevel().addFreshEntity(cow);
        var spy=new ApplicationSpy();MinecraftForge.EVENT_BUS.register(spy);
        try {
            var motion=new PredictionMotion(999,-1,PredictionMotion.Kind.THROWABLE,pos.add(0,1,0),new Vec3(0,-.5,0),Vec3.ZERO,.05,.99,.8,.25F,.25F,false,false,false,0,1)
                    .withEffects(PredictionEffects.item(PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION),Potions.POISON)));
            var path=new PredictionEngine.Path(motion,List.of(new PredictionEngine.Point(motion.position(),0),new PredictionEngine.Point(pos,1)),true,-1);
            var target=PredictionEngine.forecast(h.getLevel(),PredictionServer.capture(cow),v -> 1,Map.of());
            var event=PredictionOutcomes.event(h.getLevel(),path,Map.of(cow.getId(),target));
            h.assertTrue(event.affected().size()==1 && spy.calls==0,"Potential splash eligibility cannot dispatch Forge gameplay application events");
        } finally {MinecraftForge.EVENT_BUS.unregister(spy);cow.discard();}
        h.succeed();
    }
}
