package atomicstryker.infernalmobs.common;

import java.util.HashMap;
import java.util.Map.Entry;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class EntityEventHandler
{

    private final boolean antiMobFarm;
    private final long mobFarmCheckIntervals;
    private final float mobFarmDamageTrigger;

    private final HashMap<Tuple<Integer, Integer>, Float> damageMap;
    private long nextMapEvaluation;

    /**
     * Links the Forge Event Handler to the registered Entity MobModifier Events
     * (if present) Also keeps track of the anti mobfarm mechanic if enabled
     */
    public EntityEventHandler()
    {
        Configuration config = InfernalMobsCore.instance().config;

        config.load();
        antiMobFarm = config.get(Configuration.CATEGORY_GENERAL, "AntiMobfarmingEnabled", true, "Anti Mob farming mechanic. Might cause overhead if enabled.").getBoolean(true);
        mobFarmCheckIntervals = config
                .get(Configuration.CATEGORY_GENERAL, "AntiMobFarmCheckInterval", 30, "time in seconds between mob check intervals. Higher values cost more performance, but might be more accurate.")
                .getInt() * 1000L;
        mobFarmDamageTrigger = (float) config.get(Configuration.CATEGORY_GENERAL, "mobFarmDamageThreshold", 150D, "Damage in chunk per interval that triggers anti farm effects").getDouble(150D);
        config.save();

        damageMap = new HashMap<>();
        nextMapEvaluation = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onEntityJoinedWorld(EntityJoinWorldEvent event)
    {
        if (event.getEntity() instanceof EntityLivingBase)
        {
            String savedMods = event.getEntity().getEntityData().getString(InfernalMobsCore.instance().getNBTTag());
            if (!savedMods.equals(""))
            {
                InfernalMobsCore.instance().addEntityModifiersByString((EntityLivingBase) event.getEntity(), savedMods);
            }
            else
            {
                InfernalMobsCore.instance().processEntitySpawn((EntityLivingBase) event.getEntity());
            }
        }
    }

    @SubscribeEvent
    public void onEntityLivingDeath(LivingDeathEvent event)
    {
        if (!event.getEntity().world.isRemote)
        {
            MobModifier mod = InfernalMobsCore.getMobModifiers(event.getEntityLiving());
            if (mod != null)
            {
                if (mod.onDeath())
                {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public void onEntityLivingSetAttackTarget(LivingSetAttackTargetEvent event)
    {
        if (!event.getEntity().world.isRemote)
        {
            MobModifier mod = InfernalMobsCore.getMobModifiers(event.getEntityLiving());
            if (mod != null)
            {
                mod.onSetAttackTarget(event.getTarget());
            }
        }
    }

    @SubscribeEvent
    public void onEntityLivingAttacked(LivingAttackEvent event)
    {
        /* fires both client and server before hurt, but we dont need this */
    }

    /**
     * Hook into EntityLivingHurt. Is always serverside, assured by mc itself
     */
    @SubscribeEvent
    public void onEntityLivingHurt(LivingHurtEvent event)
    {
        // dont allow masochism
        if (event.getSource().getTrueSource() != event.getEntityLiving())
        {
            MobModifier mod = InfernalMobsCore.getMobModifiers(event.getEntityLiving());
            if (mod != null)
            {
                event.setAmount(mod.onHurt(event.getEntityLiving(), event.getSource(), event.getAmount()));
            }

            /*
             * We use the Hook two-sided, both with the Mob as possible target
             * and attacker
             */
            Entity attacker = event.getSource().getTrueSource();
            if (attacker != null && attacker instanceof EntityLivingBase)
            {
                mod = InfernalMobsCore.getMobModifiers((EntityLivingBase) attacker);
                if (mod != null)
                {
                    event.setAmount(mod.onAttack(event.getEntityLiving(), event.getSource(), event.getAmount()));
                }
            }

            if (antiMobFarm)
            {
                /*
                 * check for an environmental/automated damage type, aka mob
                 * farms
                 */
                if (event.getSource() == DamageSource.CACTUS || event.getSource() == DamageSource.DROWN || event.getSource() == DamageSource.FALL || event.getSource() == DamageSource.IN_WALL
                        || event.getSource() == DamageSource.LAVA || event.getSource().getTrueSource() instanceof FakePlayer)
                {
                    Tuple<Integer, Integer> cpair = new Tuple<Integer, Integer>((int) event.getEntityLiving().posX, (int) event.getEntityLiving().posZ);
                    Float value = damageMap.get(cpair);
                    if (value == null)
                    {
                        for (Entry<Tuple<Integer, Integer>, Float> e : damageMap.entrySet())
                        {
                            if (Math.abs(e.getKey().getFirst() - cpair.getFirst()) < 3)
                            {
                                if (Math.abs(e.getKey().getSecond() - cpair.getSecond()) < 3)
                                {
                                    e.setValue(e.getValue() + event.getAmount());
                                    break;
                                }
                            }
                        }
                    }
                    else
                    {
                        damageMap.put(cpair, value + event.getAmount());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onEntityLivingFall(LivingFallEvent event)
    {
        if (!event.getEntity().world.isRemote)
        {
            MobModifier mod = InfernalMobsCore.getMobModifiers(event.getEntityLiving());
            if (mod != null)
            {
                event.setCanceled(mod.onFall(event.getDistance()));
            }
        }
    }

    @SubscribeEvent
    public void onEntityLivingJump(LivingEvent.LivingJumpEvent event)
    {
        if (!event.getEntity().world.isRemote)
        {
            MobModifier mod = InfernalMobsCore.getMobModifiers(event.getEntityLiving());
            if (mod != null)
            {
                mod.onJump(event.getEntityLiving());
            }
        }
    }

    @SubscribeEvent
    public void onEntityLivingUpdate(LivingEvent.LivingUpdateEvent event)
    {
        if (!event.getEntityLiving().world.isRemote)
        {
            MobModifier mod = InfernalMobsCore.getMobModifiers(event.getEntityLiving());
            if (mod != null)
            {
                mod.onUpdate(event.getEntityLiving());
            }

            if (antiMobFarm && System.currentTimeMillis() > nextMapEvaluation)
            {
                if (!damageMap.isEmpty())
                {
                    float maxDamage = 0f;
                    float val;
                    Tuple<Integer, Integer> maxC = null;
                    for (Entry<Tuple<Integer, Integer>, Float> e : damageMap.entrySet())
                    {
                        val = e.getValue();
                        if (val > maxDamage)
                        {
                            maxC = e.getKey();
                            maxDamage = val;
                        }
                    }

                    if (maxC != null)
                    {
                        System.out.println("Infernal Mobs AntiMobFarm damage check, max detected chunk damage value " + maxDamage + " near coords " + maxC.getFirst() + ", " + maxC.getSecond());
                        if (maxDamage > mobFarmDamageTrigger)
                        {
                            MinecraftForge.EVENT_BUS
                                    .post(new MobFarmDetectedEvent(event.getEntityLiving().world.getChunkFromChunkCoords(maxC.getFirst(), maxC.getSecond()), mobFarmCheckIntervals, maxDamage));
                        }
                    }
                    damageMap.clear();
                }
                nextMapEvaluation = System.currentTimeMillis() + mobFarmCheckIntervals;
            }
        }
    }

    public static class MobFarmDetectedEvent extends ChunkEvent
    {
        public final long triggeringInterval;
        public final float triggeringDamage;

        public MobFarmDetectedEvent(Chunk chunk, long ti, float td)
        {
            super(chunk);
            triggeringInterval = ti;
            triggeringDamage = td;
        }
    }

    @SubscribeEvent
    public void onEntityLivingDrops(LivingDropsEvent event)
    {
        if (!event.getEntity().world.isRemote)
        {
            MobModifier mod = InfernalMobsCore.getMobModifiers(event.getEntityLiving());
            if (mod != null)
            {
                mod.onDropItems(event.getEntityLiving(), event.getSource(), event.getDrops(), event.getLootingLevel(), event.isRecentlyHit(), event.getLootingLevel());
                InfernalMobsCore.removeEntFromElites(event.getEntityLiving());
            }
        }
    }
}
