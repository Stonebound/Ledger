package com.github.quiltservertools.ledger.gametest

import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.network.FilteredText
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ComparatorBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.RepeaterBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.LecternBlockEntity
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.state.properties.ComparatorMode
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

private fun GameTestHelper.entityExists(uuid: UUID): Boolean = level.getEntity(uuid)?.isRemoved == false

private fun GameTestHelper.check(condition: Boolean, message: String) = assertTrue(condition, Component.literal(message))

val ROLLBACK_SCENARIOS = listOf(
    Scenario("rollback_block_place") { helper ->
        helper.floor(1)
        helper.useOn(helper.player(CENTER.north()), FLOOR, ItemStack(Items.OAK_PLANKS))
        helper.whenLogged(CENTER, Expected("block-place", "player", id("oak_planks"))) { action ->
            helper.checkRollback(
                action,
                { helper.assertBlockPresent(Blocks.AIR, CENTER) },
                { helper.assertBlockPresent(Blocks.OAK_PLANKS, CENTER) },
            )
        }
    },
    Scenario("rollback_block_change") { helper ->
        helper.setBlock(FLOOR, Blocks.OAK_LOG)
        helper.useOn(helper.player(), FLOOR, ItemStack(Items.IRON_AXE))
        helper.whenLogged(FLOOR, Expected("block-change", "player", id("stripped_oak_log"))) { action ->
            helper.checkRollback(
                action,
                { helper.assertBlockPresent(Blocks.OAK_LOG, FLOOR) },
                { helper.assertBlockPresent(Blocks.STRIPPED_OAK_LOG, FLOOR) },
            )
        }
    },
    Scenario("rollback_keeps_block_properties") { helper ->
        helper.floor(1)
        helper.setBlock(CENTER, Blocks.OAK_TRAPDOOR)
        helper.useOn(helper.player(CENTER.north()), CENTER, ItemStack.EMPTY)
        helper.whenLogged(CENTER, Expected("block-change", "interact", id("oak_trapdoor"))) { action ->
            helper.checkRollback(
                action,
                { helper.check(!helper.getBlockState(CENTER).getValue(TrapDoorBlock.OPEN), "trapdoor should be closed again") },
                { helper.check(helper.getBlockState(CENTER).getValue(TrapDoorBlock.OPEN), "trapdoor should be open again") },
            )
        }
    },
    Scenario("rollback_sign_text") { helper ->
        helper.setBlock(CENTER, Blocks.OAK_SIGN)
        val player = helper.player(CENTER.north())
        val sign = helper.getBlockEntity(CENTER, SignBlockEntity::class.java)
        sign.setAllowedPlayerEditor(player.uuid)
        sign.updateSignText(player, true, List(4) { FilteredText.passThrough("line $it") })
        helper.whenLogged(CENTER, Expected(action = "block-change", source = "player")) { action ->
            helper.check(action.rollback(helper.level.server), "rollback returned false")
            val text = helper.getBlockEntity(CENTER, SignBlockEntity::class.java).getText(true).getMessage(0, false).string
            helper.check(text.isEmpty(), "sign should be blank again but says '$text'")
            helper.succeed()
        }
    },
    Scenario("rollback_block_entity_changed_without_data") { helper ->
        // These blocks hold data, but the change is logged without any.
        helper.setBlock(CENTER, Blocks.COPPER_CHEST)
        val player = helper.player(CENTER.north())
        // Clicking a chest normally opens it.
        player.setShiftKeyDown(true)
        helper.useOn(player, CENTER, ItemStack(Items.HONEYCOMB))
        helper.whenLogged(CENTER, Expected("block-change", "player", id("waxed_copper_chest"))) { action ->
            helper.checkRollback(
                action,
                { helper.assertBlockPresent(Blocks.COPPER_CHEST, CENTER) },
                { helper.assertBlockPresent(Blocks.WAXED_COPPER_CHEST, CENTER) },
            )
        }
    },
    Scenario("rollback_lectern_book") { helper ->
        helper.setBlock(CENTER, Blocks.LECTERN)
        helper.useOn(helper.player(CENTER.north()), CENTER, ItemStack(Items.WRITABLE_BOOK))
        helper.whenLogged(CENTER, Expected("item-insert", "player", id("writable_book"))) { action ->
            helper.checkRollback(
                action,
                { helper.check(!helper.getBlockEntity(CENTER, LecternBlockEntity::class.java).hasBook(), "book should be gone") },
                { helper.check(helper.getBlockEntity(CENTER, LecternBlockEntity::class.java).hasBook(), "book should be back") },
            )
        }
    },
    Scenario("rollback_item_insert") { helper ->
        helper.setBlock(CENTER, Blocks.CHEST)
        val chest = helper.getBlockEntity(CENTER, ChestBlockEntity::class.java)
        val player = helper.player(CENTER.north())
        player.openMenu(chest)
        player.containerMenu.setCarried(ItemStack(Items.DIAMOND, 3))
        player.containerMenu.clicked(0, 0, ContainerInput.PICKUP, player)
        player.closeContainer()
        helper.whenLogged(CENTER, Expected("item-insert", "player", id("diamond"))) { action ->
            helper.checkRollback(
                action,
                { helper.check(chest.isEmpty, "chest should be empty") },
                { helper.check(chest.getItem(0).count == 3, "chest should hold the diamonds, found ${chest.getItem(0)}") },
            )
        }
    },
    Scenario("rollback_item_drop") { helper ->
        helper.floor(2)
        val item = helper.player().drop(ItemStack(Items.DIAMOND), true)!!
        val uuid = item.uuid
        helper.whenLogged(CENTER, Expected("item-drop", "player", id("diamond")), radius = 3) { action ->
            helper.checkRollback(
                action,
                { helper.check(!helper.entityExists(uuid), "dropped item should be gone") },
                { helper.check(helper.entityExists(uuid), "dropped item should be back") },
            )
        }
    },
    Scenario("rollback_item_pick_up") { helper ->
        helper.floor(2)
        val player = helper.player()
        val at = helper.absoluteVec(Vec3.atBottomCenterOf(CENTER))
        val item = ItemEntity(helper.level, at.x, at.y, at.z, ItemStack(Items.DIAMOND))
        item.setNoPickUpDelay()
        helper.level.addFreshEntity(item)
        item.playerTouch(player)
        helper.whenLogged(CENTER, Expected("item-pick-up", "player", id("diamond")), radius = 3) { action ->
            fun itemsOnGround() = helper.level.getEntitiesOfClass(ItemEntity::class.java, AABB.ofSize(at, 8.0, 8.0, 8.0)).size
            helper.checkRollback(
                action,
                { helper.check(itemsOnGround() == 1, "item should be back on the ground") },
                { helper.check(itemsOnGround() == 0, "item should be gone again") },
            )
        }
    },
    Scenario("rollback_entity_kill") { helper ->
        helper.floor(2)
        val pig = helper.spawnWithNoFreeWill(EntityType.PIG, CENTER)
        val uuid = pig.uuid
        pig.hurtServer(helper.level, helper.level.damageSources().playerAttack(helper.player(CENTER.north())), 1000f)
        helper.whenLogged(CENTER, Expected("entity-kill", "player", id("pig")), radius = 3) { action ->
            helper.checkRollback(
                action,
                { helper.check(helper.entityExists(uuid), "pig should be alive again") },
                { helper.check(!helper.entityExists(uuid), "pig should be gone again") },
            )
        }
    },
    Scenario("rollback_entity_change") { helper ->
        helper.floor(2)
        val sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, CENTER)
        helper.interact(helper.player(CENTER.north()), sheep, Items.SHEARS)
        helper.check(sheep.isSheared, "sheep should have been sheared")
        helper.whenLogged(CENTER, Expected("entity-change", "shear", id("sheep")), radius = 2) { action ->
            helper.checkRollback(
                action,
                { helper.check(!sheep.isSheared, "sheep should have its wool back") },
                { helper.check(sheep.isSheared, "sheep should be sheared again") },
            )
        }
    },
    Scenario("rollback_item_frame") { helper ->
        val frame = helper.itemFrame()
        helper.interact(helper.player(CENTER.north()), frame, Items.DIAMOND)
        helper.whenLogged(CENTER, Expected("entity-change", "equip", id("item_frame")), radius = 1) { action ->
            helper.checkRollback(
                action,
                { helper.check(frame.item.isEmpty, "frame should be empty") },
                { helper.check(frame.item.`is`(Items.DIAMOND), "frame should hold the diamond") },
            )
        }
    },
    Scenario("rollback_armor_stand") { helper ->
        helper.floor(2)
        val stand = helper.spawn(EntityType.ARMOR_STAND, CENTER)
        helper.interact(helper.player(CENTER.north()), stand, Items.DIAMOND_CHESTPLATE, Vec3(0.0, 1.0, 0.0))
        helper.whenLogged(CENTER, Expected("entity-change", "equip", id("armor_stand")), radius = 2) { action ->
            helper.checkRollback(
                action,
                { helper.check(stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty, "stand should be bare") },
                { helper.check(!stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty, "stand should be wearing it") },
            )
        }
    },
    Scenario("rollback_fence_gate") { helper ->
        helper.floor(1)
        helper.setBlock(CENTER, Blocks.OAK_FENCE_GATE)
        helper.useOn(helper.player(CENTER.north()), CENTER, ItemStack.EMPTY)
        helper.whenLogged(CENTER, Expected("block-change", "interact", id("oak_fence_gate"))) { action ->
            helper.checkRollback(
                action,
                { helper.check(!helper.getBlockState(CENTER).getValue(FenceGateBlock.OPEN), "gate should be closed again") },
                { helper.check(helper.getBlockState(CENTER).getValue(FenceGateBlock.OPEN), "gate should be open again") },
            )
        }
    },
    Scenario("rollback_repeater") { helper ->
        helper.floor(1)
        helper.setBlock(CENTER, Blocks.REPEATER)
        helper.useOn(helper.player(CENTER.north()), CENTER, ItemStack.EMPTY)
        helper.whenLogged(CENTER, Expected("block-change", "interact", id("repeater"))) { action ->
            helper.checkRollback(
                action,
                { helper.check(helper.getBlockState(CENTER).getValue(RepeaterBlock.DELAY) == 1, "delay should be 1 again") },
                { helper.check(helper.getBlockState(CENTER).getValue(RepeaterBlock.DELAY) == 2, "delay should be 2 again") },
            )
        }
    },
    Scenario("rollback_comparator") { helper ->
        helper.floor(1)
        helper.setBlock(CENTER, Blocks.COMPARATOR)
        helper.useOn(helper.player(CENTER.north()), CENTER, ItemStack.EMPTY)
        helper.whenLogged(CENTER, Expected("block-change", "interact", id("comparator"))) { action ->
            helper.checkRollback(
                action,
                { helper.check(helper.getBlockState(CENTER).getValue(ComparatorBlock.MODE) == ComparatorMode.COMPARE, "should be comparing again") },
                { helper.check(helper.getBlockState(CENTER).getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT, "should be subtracting again") },
            )
        }
    },
    Scenario("rollback_door_both_halves") { helper ->
        helper.floor(1)
        helper.setBlock(CENTER, Blocks.OAK_DOOR.defaultBlockState())
        helper.setBlock(CENTER.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER))
        helper.useOn(helper.player(CENTER.north()), CENTER, ItemStack.EMPTY)
        helper.whenLogged(CENTER, Expected("block-change", "interact", id("oak_door"))) { action ->
            helper.checkRollback(
                action,
                {
                    helper.check(!helper.getBlockState(CENTER).getValue(DoorBlock.OPEN), "lower half should be closed")
                    helper.check(!helper.getBlockState(CENTER.above()).getValue(DoorBlock.OPEN), "upper half should be closed")
                },
                {
                    helper.check(helper.getBlockState(CENTER).getValue(DoorBlock.OPEN), "lower half should be open")
                    helper.check(helper.getBlockState(CENTER.above()).getValue(DoorBlock.OPEN), "upper half should be open")
                },
            )
        }
    },
)
