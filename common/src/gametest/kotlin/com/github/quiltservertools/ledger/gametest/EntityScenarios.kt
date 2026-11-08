package com.github.quiltservertools.ledger.gametest

import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.ConversionParams
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.animal.cow.Cow
import net.minecraft.world.entity.animal.golem.CopperGolem
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

private const val LONG_ATTEMPTS = 110
private const val VERY_LONG_ATTEMPTS = 280

internal fun GameTestHelper.interact(player: ServerPlayer, entity: Entity, item: Item?, at: Vec3 = Vec3.ZERO) {
    player.setItemInHand(InteractionHand.MAIN_HAND, item?.let(::ItemStack) ?: ItemStack.EMPTY)
    player.interactOn(entity, InteractionHand.MAIN_HAND, at)
}

/** An item frame hanging at the centre, on a stone block to the south. */
internal fun GameTestHelper.itemFrame(): ItemFrame {
    setBlock(CENTER.south(), Blocks.STONE)
    val frame = ItemFrame(level, absolutePos(CENTER), Direction.NORTH)
    level.addFreshEntity(frame)
    return frame
}

private val CHEST_HEIGHT = Vec3(0.0, 1.0, 0.0)

val ENTITY_SCENARIOS = listOf(
    Scenario("sheep_dyed") { helper ->
        helper.floor(2)
        val sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, CENTER)
        helper.interact(helper.player(CENTER.north()), sheep, Items.BLUE_DYE)
        helper.succeedWhenLogged(CENTER, Expected("entity-change", "dye", id("sheep")), radius = 2)
    },
    Scenario("wolf_collar_dyed") { helper ->
        helper.floor(2)
        val player = helper.player(CENTER.north())
        val wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, CENTER)
        wolf.tame(player)
        helper.interact(player, wolf, Items.BLUE_DYE)
        helper.succeedWhenLogged(CENTER, Expected("entity-change", "dye", id("wolf")), radius = 2)
    },
    Scenario("cat_collar_dyed") { helper ->
        helper.floor(2)
        val player = helper.player(CENTER.north())
        val cat = helper.spawnWithNoFreeWill(EntityType.CAT, CENTER)
        cat.tame(player)
        helper.interact(player, cat, Items.BLUE_DYE)
        helper.succeedWhenLogged(CENTER, Expected("entity-change", "dye", id("cat")), radius = 2)
    },
    Scenario("armor_stand_dressed_undressed_and_broken", maxTicks = 1500) { helper ->
        helper.floor(2)
        val player = helper.player(CENTER.north())
        val stand = helper.spawn(EntityType.ARMOR_STAND, CENTER)
        helper.interact(player, stand, Items.DIAMOND_CHESTPLATE, CHEST_HEIGHT)
        helper.whenLogged(CENTER, Expected("entity-change", "equip", id("armor_stand")), radius = 2) {
            helper.interact(player, stand, null, CHEST_HEIGHT)
            helper.whenLogged(CENTER, Expected("entity-change", "remove", id("armor_stand")), radius = 2) {
                // It takes two quick hits to break one.
                val damage = helper.level.damageSources().playerAttack(player)
                stand.hurtServer(helper.level, damage, 1f)
                stand.hurtServer(helper.level, damage, 1f)
                helper.succeedWhenLogged(CENTER, Expected("entity-kill", "player", id("armor_stand")), radius = 2)
            }
        }
    },
    Scenario("item_frame_filled_rotated_emptied_and_broken", maxTicks = 1500) { helper ->
        val frame = helper.itemFrame()
        val player = helper.player(CENTER.north())
        val damage = helper.level.damageSources().playerAttack(player)
        helper.interact(player, frame, Items.DIAMOND)
        helper.whenLogged(CENTER, Expected("entity-change", "equip", id("item_frame")), radius = 1) {
            helper.interact(player, frame, null)
            helper.whenLogged(CENTER, Expected("entity-change", "rotate", id("item_frame")), radius = 1) {
                frame.hurtServer(helper.level, damage, 1f)
                helper.assertTrue(frame.item.isEmpty, Component.literal("the hit didn't knock the item out"))
                helper.whenLogged(CENTER, Expected("entity-change", "remove", id("item_frame")), radius = 1) {
                    frame.hurtServer(helper.level, damage, 1f)
                    helper.succeedWhenLogged(CENTER, Expected("entity-kill", "player", id("item_frame")), radius = 1)
                }
            }
        }
    },
    Scenario("item_frame_loses_support") { helper ->
        helper.itemFrame()
        helper.setBlock(CENTER.south(), Blocks.AIR)
        helper.succeedWhenLogged(CENTER, Expected("entity-kill", objectId = id("item_frame")), radius = 1)
    },
    Scenario("creeper_ignited_by_player") { helper ->
        helper.floor(3, Blocks.DIRT)
        val player = helper.player(CENTER.north(3))
        val creeper = helper.spawn(EntityType.CREEPER, CENTER)
        helper.interact(player, creeper, Items.FLINT_AND_STEEL)
        helper.succeedWhenLogged(FLOOR, Expected("block-break", "creeper", id("dirt"), player.scoreboardName), radius = 3)
    },
    Scenario("end_crystal_blown_up_by_player") { helper ->
        helper.floor(3, Blocks.DIRT)
        val player = helper.player(CENTER.north(3))
        val crystal = helper.spawn(EntityType.END_CRYSTAL, CENTER)
        crystal.hurtServer(helper.level, helper.level.damageSources().playerAttack(player), 1f)
        helper.succeedWhenLogged(FLOOR, Expected(action = "block-break", objectId = id("dirt"), player = player.scoreboardName), radius = 3)
    },
    Scenario("ghast_fireball_blames_its_target") { helper ->
        helper.floor(4, Blocks.DIRT)
        // Mobs can't target players in creative mode.
        val player = helper.uniqueMockPlayer(gameType = GameType.SURVIVAL)
        player.abilities.invulnerable = false
        val ghast = helper.spawnWithNoFreeWill(EntityType.GHAST, CENTER.above(8))
        ghast.setTarget(player)
        helper.assertTrue(ghast.target == player, Component.literal("ghast has no target"))
        val fireball = LargeFireball(helper.level, ghast, Vec3(0.0, -1.0, 0.0), 2)
        val at = helper.absoluteVec(Vec3.atCenterOf(CENTER))
        fireball.setPos(at.x, at.y, at.z)
        helper.level.addFreshEntity(fireball)
        helper.succeedWhenLogged(FLOOR, Expected("block-break", "fireball", id("dirt"), player.scoreboardName), radius = 4)
    },
    Scenario("ender_dragon_breaks_blocks") { helper ->
        helper.setBlock(CENTER, Blocks.STONE)
        val dragon = helper.spawnWithNoFreeWill(EntityType.ENDER_DRAGON, CENTER.above(6))
        // It breaks whatever it flies through, which is hard to arrange, so call that step directly.
        val checkWalls = EnderDragon::class.java.getDeclaredMethod("checkWalls", ServerLevel::class.java, AABB::class.java)
        checkWalls.isAccessible = true
        checkWalls.invoke(dragon, helper.level, AABB(helper.absolutePos(CENTER)))
        helper.succeedWhenLogged(CENTER, Expected("block-break", "ender_dragon", id("stone")))
    },
    Scenario("mob_converted") { helper ->
        helper.floor(2)
        val pig = helper.spawnWithNoFreeWill(EntityType.PIG, CENTER)
        pig.convertTo(EntityType.COW, ConversionParams.single(pig, false, false), ConversionParams.AfterConversion<Cow> { })
        helper.succeedWhenLogged(CENTER, Expected("entity-kill", "conversion", id("pig")), radius = 1)
    },
    Scenario("evoker_turns_sheep_red", maxTicks = 1500) { helper ->
        helper.floor(4)
        val sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, CENTER.east(3))
        sheep.setColor(DyeColor.BLUE)
        helper.spawn(EntityType.EVOKER, CENTER)
        helper.succeedWhenLogged(CENTER.east(3), Expected("entity-change", "dye", id("sheep")), radius = 1, attempts = LONG_ATTEMPTS)
    },
    Scenario("silverfish_calls_for_help", maxTicks = 1500) { helper ->
        // A floor it can't burrow into, or it can vanish before it calls for help.
        helper.floor(3, Blocks.OAK_PLANKS)
        helper.setBlock(CENTER.east(), Blocks.INFESTED_STONE)
        val silverfish = helper.spawn(EntityType.SILVERFISH, CENTER)
        val player = helper.player(CENTER.west(3))
        silverfish.hurtServer(helper.level, helper.level.damageSources().playerAttack(player), 1f)
        helper.succeedWhenLogged(CENTER.east(), Expected("block-break", "silverfish", id("infested_stone")), attempts = LONG_ATTEMPTS)
    },
    Scenario("silverfish_infests_block", maxTicks = 1500) { helper ->
        for (x in -1..1) {
            for (y in -1..1) {
                for (z in -1..1) {
                    if (x != 0 || y != 0 || z != 0) helper.setBlock(CENTER.offset(x, y, z), Blocks.STONE)
                }
            }
        }
        helper.spawn(EntityType.SILVERFISH, CENTER)
        helper.succeedWhenLogged(CENTER, Expected("block-change", "silverfish", id("infested_stone")), radius = 1, attempts = LONG_ATTEMPTS)
    },
    Scenario("ravager_breaks_leaves", maxTicks = 1500) { helper ->
        helper.floor(4)
        for (x in -3..3) {
            for (z in -3..3) {
                for (y in 0..2) {
                    if (x == -3 || x == 3 || z == -3 || z == 3) helper.setBlock(CENTER.offset(x, y, z), Blocks.OAK_LEAVES)
                }
            }
        }
        val ravager = helper.spawn(EntityType.RAVAGER, CENTER)
        helper.onEachTick { ravager.setDeltaMovement(0.5, ravager.deltaMovement.y, 0.0) }
        helper.succeedWhenLogged(CENTER, Expected("block-break", "ravager", id("oak_leaves")), radius = 4, attempts = LONG_ATTEMPTS)
    },
    Scenario("lightning_starts_fire") { helper ->
        helper.floor(4)
        helper.spawn(EntityType.LIGHTNING_BOLT, CENTER)
        helper.succeedWhenLogged(CENTER, Expected("block-place", "lightning_bolt", id("fire")), radius = 3)
    },
    Scenario("frost_walker_freezes_water") { helper ->
        helper.floor(4, Blocks.WATER)
        val player = helper.player()
        val boots = ItemStack(Items.LEATHER_BOOTS)
        boots.enchant(helper.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FROST_WALKER), 1)
        player.setItemSlot(EquipmentSlot.FEET, boots)
        player.setOnGround(true)
        EnchantmentHelper.runLocationChangedEffects(helper.level, player)
        helper.succeedWhenLogged(FLOOR, Expected("block-place", "frost_walker", id("frosted_ice")), radius = 3)
    },

    // Copper golems
    Scenario("copper_golem_waxed_and_scraped") { helper ->
        helper.floor(2)
        val player = helper.player(CENTER.north())
        val golem = helper.spawnWithNoFreeWill(EntityType.COPPER_GOLEM, CENTER)
        helper.interact(player, golem, Items.HONEYCOMB)
        helper.whenLogged(CENTER, Expected("entity-change", "wax", id("copper_golem")), radius = 1) {
            helper.interact(player, golem, Items.IRON_AXE)
            helper.succeedWhenLogged(CENTER, Expected("entity-change", "interact", id("copper_golem")), radius = 1)
        }
    },
    Scenario("copper_golem_sheared") { helper ->
        helper.floor(2)
        val golem = helper.spawnWithNoFreeWill(EntityType.COPPER_GOLEM, CENTER)
        golem.setItemSlot(EquipmentSlot.SADDLE, ItemStack(Items.POPPY))
        helper.interact(helper.player(CENTER.north()), golem, Items.SHEARS)
        helper.succeedWhenLogged(CENTER, Expected("entity-change", "shear", id("copper_golem")), radius = 1)
    },
    Scenario("copper_golem_throws_held_item") { helper ->
        helper.floor(4)
        val golem = helper.spawnWithNoFreeWill(EntityType.COPPER_GOLEM, CENTER)
        golem.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.DIAMOND))
        helper.interact(helper.player(CENTER.north(2)), golem, null)
        helper.succeedWhenLogged(CENTER, Expected("item-drop", "copper_golem", id("diamond")), radius = 3)
    },
    Scenario("copper_golem_turns_to_statue") { helper ->
        helper.floor(2)
        val golem = helper.spawnWithNoFreeWill(EntityType.COPPER_GOLEM, CENTER)
        // Normally takes many in-game days of oxidising, so call the final step directly.
        val turn = CopperGolem::class.java.getDeclaredMethod("turnToStatue", ServerLevel::class.java)
        turn.isAccessible = true
        turn.invoke(golem, helper.level)
        helper.whenLogged(CENTER, Expected("entity-kill", "statue", id("copper_golem"))) {
            helper.succeedWhenLogged(CENTER, Expected(action = "block-place", source = "statue"))
        }
    },
    Scenario("copper_golem_statue_reanimated") { helper ->
        helper.floor(2)
        // Only a statue with no oxidation left comes back to life.
        helper.setBlock(CENTER, Blocks.COPPER_GOLEM_STATUE)
        helper.useOn(helper.player(CENTER.north()), CENTER, ItemStack(Items.IRON_AXE))
        helper.succeedWhenLogged(CENTER, Expected("entity-change", "reanimate", id("copper_golem")), radius = 1)
    },
    Scenario("copper_golem_moves_items", maxTicks = 3000) { helper ->
        helper.floor(4)
        helper.setBlock(CENTER.east(2), Blocks.COPPER_CHEST)
        helper.getBlockEntity(CENTER.east(2), ChestBlockEntity::class.java).setItem(0, ItemStack(Items.DIAMOND, 3))
        helper.setBlock(CENTER.west(2), Blocks.CHEST)
        helper.spawn(EntityType.COPPER_GOLEM, CENTER)
        helper.succeedWhenLogged(CENTER.east(2), Expected("item-remove", "copper_golem", id("diamond")), attempts = VERY_LONG_ATTEMPTS)
    },
)
