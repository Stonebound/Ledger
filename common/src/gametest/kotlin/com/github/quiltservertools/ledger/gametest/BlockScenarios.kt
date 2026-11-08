package com.github.quiltservertools.ledger.gametest

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.LecternMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BonemealableBlock
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CandleBlock
import net.minecraft.world.level.block.CauldronBlock
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.CoralWallFanBlock
import net.minecraft.world.level.block.DispenserBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FarmlandBlock
import net.minecraft.world.level.block.IceBlock
import net.minecraft.world.level.block.LayeredCauldronBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.PointedDripstoneBlock
import net.minecraft.world.level.block.ScaffoldingBlock
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.DispenserBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.block.state.properties.DripstoneThickness
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

/** Hits one point of a block's face, for blocks that care where they're clicked. */
internal fun GameTestHelper.useOnFace(player: ServerPlayer, pos: BlockPos, item: ItemStack, face: Direction, x: Double, y: Double, z: Double) {
    player.setItemInHand(InteractionHand.MAIN_HAND, item)
    val target = absolutePos(pos)
    val hit = BlockHitResult(Vec3(target.x + x, target.y + y, target.z + z), face, target, false)
    player.gameMode.useItemOn(player, level, player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit)
}

/** Sets up a block at the centre, clicks it, and expects [expected] at the same place. */
private fun useScenario(
    name: String,
    expected: Expected,
    item: () -> ItemStack = { ItemStack.EMPTY },
    setup: (GameTestHelper) -> Unit,
) = Scenario(name) { helper ->
    helper.floor(2)
    setup(helper)
    helper.useOn(helper.player(CENTER.north()), CENTER, item())
    helper.succeedWhenLogged(CENTER, expected)
}

private fun useBlock(name: String, block: Block, expected: Expected, item: Item? = null) =
    useScenario(name, expected, { item?.let(::ItemStack) ?: ItemStack.EMPTY }) { it.setBlock(CENTER, block) }

private fun useState(name: String, state: BlockState, expected: Expected, item: Item? = null) =
    useScenario(name, expected, { item?.let(::ItemStack) ?: ItemStack.EMPTY }) { it.setBlock(CENTER, state) }

/** Runs a block's scheduled tick where it has nothing supporting it. */
private fun tickScenario(name: String, state: BlockState, expected: Expected) = Scenario(name) { helper ->
    helper.setBlock(CENTER, state)
    val pos = helper.absolutePos(CENTER)
    helper.level.getBlockState(pos).tick(helper.level, pos, helper.level.random)
    helper.succeedWhenLogged(CENTER, expected, radius = 1)
}

private fun GameTestHelper.placeDoor(pos: BlockPos) {
    setBlock(pos, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER))
    setBlock(pos.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER))
}

/** Dispenser at the centre, pointing up, holding [item]. */
private fun GameTestHelper.dispenser(item: Item) {
    setBlock(CENTER, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.UP))
    getBlockEntity(CENTER, DispenserBlockEntity::class.java).setItem(0, ItemStack(item))
}

private fun redstone(name: String, block: Block, objectName: String) = Scenario(name) { helper ->
    helper.floor(2)
    helper.setBlock(CENTER, block)
    helper.setBlock(CENTER.east(), Blocks.REDSTONE_BLOCK)
    helper.succeedWhenLogged(CENTER, Expected("block-change", "redstone", id(objectName)))
}

/** Hits the block at the centre with an arrow shot by a player. */
private fun GameTestHelper.shoot(player: ServerPlayer, onFire: Boolean) {
    val arrow = Arrow(level, player, ItemStack(Items.ARROW), null)
    if (onFire) arrow.igniteForSeconds(10f)
    val pos = absolutePos(CENTER)
    val state = level.getBlockState(pos)
    state.onProjectileHit(level, state, BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false), arrow)
}

private fun GameTestHelper.bed(pos: BlockPos, level: Level) {
    val bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH)
    level.setBlock(absolutePos(pos), bed.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL)
    level.setBlock(absolutePos(pos.north()), bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL)
}

private fun GameTestHelper.nether(): ServerLevel = level.server.getLevel(Level.NETHER)!!

/** Tests run below the Nether's lowest height, so anything there has to be higher up. */
private val IN_NETHER = CENTER.above(100)

private fun GameTestHelper.dripInto(owner: Class<*>, state: BlockState) {
    setBlock(CENTER, state)
    val drip = owner.getDeclaredMethod("receiveStalactiteDrip", BlockState::class.java, Level::class.java, BlockPos::class.java, Fluid::class.java)
    drip.isAccessible = true
    drip.invoke(state.block, state, level, absolutePos(CENTER), Fluids.WATER)
}

private val floorButton = Blocks.OAK_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR)
private val floorLever = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
private val fullCauldron = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)
private val stalagmite = Blocks.POINTED_DRIPSTONE.defaultBlockState()
    .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
    .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP)

val BLOCK_SCENARIOS = listOf(
    // Doors and other things that open
    useScenario("door_opened_by_player", Expected("block-change", "interact", id("oak_door"))) { it.placeDoor(CENTER) },
    Scenario("door_opened_by_redstone") { helper ->
        helper.floor(2)
        helper.placeDoor(CENTER)
        helper.setBlock(CENTER.east(), Blocks.REDSTONE_BLOCK)
        helper.succeedWhenLogged(CENTER, Expected("block-change", "redstone", id("oak_door")))
    },
    useBlock("trapdoor_opened_by_player", Blocks.OAK_TRAPDOOR, Expected("block-change", "interact", id("oak_trapdoor"))),
    redstone("trapdoor_opened_by_redstone", Blocks.OAK_TRAPDOOR, "oak_trapdoor"),
    useBlock("fence_gate_opened_by_player", Blocks.OAK_FENCE_GATE, Expected("block-change", "interact", id("oak_fence_gate"))),
    redstone("fence_gate_opened_by_redstone", Blocks.OAK_FENCE_GATE, "oak_fence_gate"),

    // Redstone parts a player can click
    useState("button_pressed", floorButton, Expected("block-change", "player", id("oak_button"))),
    useState("lever_pulled", floorLever, Expected("block-change", "player", id("lever"))),
    useBlock("repeater_changed", Blocks.REPEATER, Expected("block-change", "interact", id("repeater"))),
    useBlock("comparator_changed", Blocks.COMPARATOR, Expected("block-change", "interact", id("comparator"))),
    useBlock("daylight_detector_changed", Blocks.DAYLIGHT_DETECTOR, Expected("block-change", "player", id("daylight_detector"))),
    useBlock("note_block_tuned", Blocks.NOTE_BLOCK, Expected("block-change", "player", id("note_block"))),

    // Other blocks that change when clicked
    useBlock("flower_pot_emptied", Blocks.POTTED_POPPY, Expected("block-change", "player", id("potted_poppy"))),
    useBlock("flower_pot_planted", Blocks.FLOWER_POT, Expected("block-change", "player", id("potted_poppy")), Items.POPPY),
    useScenario("cake_eaten", Expected("block-change", "consume", id("cake"))) { it.setBlock(CENTER, Blocks.CAKE) },
    useBlock("candle_put_on_cake", Blocks.CAKE, Expected("block-change", "interact", id("candle_cake")), Items.CANDLE),
    useState(
        "candle_put_out",
        Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true),
        Expected("block-change", "extinguish", id("candle")),
    ),
    useBlock("campfire_put_out", Blocks.CAMPFIRE, Expected("block-change", "extinguish", id("campfire")), Items.IRON_SHOVEL),
    useBlock("jukebox_disc_inserted", Blocks.JUKEBOX, Expected("block-change", "interact", id("jukebox")), Items.MUSIC_DISC_CAT),
    useBlock("copper_waxed", Blocks.COPPER_BLOCK, Expected("block-change", "player", id("waxed_copper_block")), Items.HONEYCOMB),
    useBlock("copper_unwaxed", Blocks.WAXED_COPPER_BLOCK, Expected("block-change", "player", id("copper_block")), Items.IRON_AXE),
    useBlock("sign_waxed", Blocks.OAK_SIGN, Expected("block-change", objectId = id("oak_sign")), Items.HONEYCOMB),
    useBlock("copper_statue_posed", Blocks.COPPER_GOLEM_STATUE, Expected("block-change", "interact", id("copper_golem_statue"))),

    // Blocks holding items without a menu
    Scenario("chiseled_bookshelf_add_and_remove") { helper ->
        helper.setBlock(CENTER, Blocks.CHISELED_BOOKSHELF)
        val player = helper.player(CENTER.north())
        helper.useOnFace(player, CENTER, ItemStack(Items.BOOK), Direction.NORTH, 0.25, 0.75, 0.0)
        helper.whenLogged(CENTER, Expected("item-insert", "player", id("book"))) {
            helper.useOnFace(player, CENTER, ItemStack.EMPTY, Direction.NORTH, 0.25, 0.75, 0.0)
            helper.succeedWhenLogged(CENTER, Expected("item-remove", "player", id("book")))
        }
    },
    useBlock("decorated_pot_filled", Blocks.DECORATED_POT, Expected("item-insert", "player", id("diamond")), Items.DIAMOND),
    Scenario("shelf_add_and_remove") { helper ->
        helper.setBlock(CENTER, Blocks.OAK_SHELF)
        val player = helper.player(CENTER.north())
        helper.useOnFace(player, CENTER, ItemStack(Items.DIAMOND), Direction.NORTH, 0.5, 0.5, 0.0)
        helper.whenLogged(CENTER, Expected("item-insert", "player", id("diamond"))) {
            helper.useOnFace(player, CENTER, ItemStack.EMPTY, Direction.NORTH, 0.5, 0.5, 0.0)
            helper.succeedWhenLogged(CENTER, Expected("item-remove", "player", id("diamond")))
        }
    },
    Scenario("lectern_add_and_take_book") { helper ->
        helper.setBlock(CENTER, Blocks.LECTERN)
        val player = helper.player(CENTER.north())
        helper.useOn(player, CENTER, ItemStack(Items.WRITABLE_BOOK))
        helper.whenLogged(CENTER, Expected("item-insert", "player", id("writable_book"))) {
            helper.useOn(player, CENTER, ItemStack.EMPTY)
            val menu = player.containerMenu as LecternMenu
            menu.clickMenuButton(player, LecternMenu.BUTTON_TAKE_BOOK)
            helper.succeedWhenLogged(CENTER, Expected("item-remove", "player", id("writable_book")))
        }
    },

    // Containers with menus
    Scenario("double_chest_second_half") { helper ->
        helper.setBlock(CENTER, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT))
        helper.setBlock(CENTER.east(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT))
        val player = helper.player(CENTER.north())
        player.openMenu(helper.getBlockState(CENTER).getMenuProvider(helper.level, helper.absolutePos(CENTER))!!)
        // A double chest has 54 slots, so this one exists only if both halves joined up.
        player.containerMenu.setCarried(ItemStack(Items.DIAMOND))
        player.containerMenu.clicked(40, 0, ContainerInput.PICKUP, player)
        player.closeContainer()
        helper.succeedWhenLogged(CENTER, Expected("item-insert", "player", id("diamond")), radius = 1)
    },
    Scenario("shift_click_into_barrel") { helper ->
        helper.setBlock(CENTER, Blocks.BARREL)
        val player = helper.player(CENTER.north())
        player.inventory.setItem(9, ItemStack(Items.DIAMOND, 4))
        player.openMenu(helper.getBlockEntity(CENTER, BarrelBlockEntity::class.java))
        // The barrel's 27 slots come first, then the player's inventory.
        player.containerMenu.clicked(27, 0, ContainerInput.QUICK_MOVE, player)
        player.closeContainer()
        helper.succeedWhenLogged(CENTER, Expected("item-insert", "player", id("diamond")))
    },
    Scenario("chest_contents_blown_up") { helper ->
        helper.floor(3, Blocks.DIRT)
        helper.setBlock(CENTER, Blocks.CHEST)
        helper.getBlockEntity(CENTER, ChestBlockEntity::class.java).setItem(0, ItemStack(Items.DIAMOND, 2))
        helper.spawn(EntityType.TNT, CENTER.east()).setFuse(1)
        helper.succeedWhenLogged(CENTER, Expected("item-remove", "broke", id("diamond")))
    },

    // Fluids
    Scenario("bucket_waterlogs_slab") { helper ->
        helper.floor(1)
        helper.setBlock(FLOOR, Blocks.OAK_SLAB)
        val player = helper.player()
        player.setXRot(90f)
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.WATER_BUCKET))
        player.gameMode.useItem(player, helper.level, player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND)
        helper.succeedWhenLogged(FLOOR, Expected("block-change", objectId = id("oak_slab")))
    },
    Scenario("dispenser_bucket_picks_up_water") { helper ->
        helper.dispenser(Items.BUCKET)
        helper.setBlock(CENTER.above(), Blocks.WATER)
        helper.setBlock(CENTER.east(), Blocks.REDSTONE_BLOCK)
        helper.succeedWhenLogged(CENTER.above(), Expected("block-break", "redstone", id("water")))
    },
    Scenario("dispenser_water_washes_away_torch") { helper ->
        helper.dispenser(Items.WATER_BUCKET)
        helper.setBlock(CENTER.above(), Blocks.TORCH)
        helper.setBlock(CENTER.east(), Blocks.REDSTONE_BLOCK)
        helper.succeedWhenLogged(CENTER.above(), Expected("block-break", "fluid", id("torch")))
    },
    Scenario("dispenser_places_shulker_box") { helper ->
        helper.dispenser(Items.SHULKER_BOX)
        helper.setBlock(CENTER.east(), Blocks.REDSTONE_BLOCK)
        helper.succeedWhenLogged(CENTER.above(), Expected("block-place", "redstone", id("shulker_box")))
    },
    Scenario("sponge_gets_wet") { helper ->
        helper.floor(2)
        helper.setBlock(CENTER.east(), Blocks.WATER)
        helper.setBlock(CENTER, Blocks.SPONGE)
        helper.succeedWhenLogged(CENTER, Expected("block-change", "wet", id("wet_sponge")))
    },

    // Cauldrons
    useBlock("cauldron_filled_from_bucket", Blocks.CAULDRON, Expected("block-change", "fill", id("water_cauldron")), Items.WATER_BUCKET),
    useState("cauldron_emptied_into_bucket", fullCauldron, Expected("block-change", "drain", id("water_cauldron")), Items.BUCKET),
    useState("cauldron_used_by_bottle", fullCauldron, Expected("block-change", "drain", id("water_cauldron")), Items.GLASS_BOTTLE),
    useScenario(
        "cauldron_filled_from_bottle",
        Expected("block-change", "fill", id("water_cauldron")),
        { PotionContents.createItemStack(Items.POTION, Potions.WATER) },
    ) { it.setBlock(CENTER, Blocks.CAULDRON) },
    Scenario("cauldron_puts_out_burning_mob") { helper ->
        helper.floor(2)
        helper.setBlock(CENTER, fullCauldron)
        helper.spawn(EntityType.PIG, CENTER).setRemainingFireTicks(200)
        helper.succeedWhenLogged(CENTER, Expected("block-change", "drain", id("water_cauldron")))
    },
    Scenario("cauldron_collects_snow") { helper ->
        helper.setBlock(CENTER, Blocks.CAULDRON)
        val pos = helper.absolutePos(CENTER)
        // The game only collects some of the time.
        repeat(500) {
            val state = helper.level.getBlockState(pos)
            if (state.`is`(Blocks.CAULDRON)) state.block.handlePrecipitation(state, helper.level, pos, Biome.Precipitation.SNOW)
        }
        helper.succeedWhenLogged(CENTER, Expected("block-change", "snow", id("powder_snow_cauldron")))
    },
    Scenario("cauldron_collects_more_snow") { helper ->
        helper.setBlock(CENTER, Blocks.POWDER_SNOW_CAULDRON)
        val pos = helper.absolutePos(CENTER)
        val start = helper.level.getBlockState(pos)
        repeat(500) {
            val state = helper.level.getBlockState(pos)
            if (state == start) state.block.handlePrecipitation(state, helper.level, pos, Biome.Precipitation.SNOW)
        }
        helper.succeedWhenLogged(CENTER, Expected("block-change", "snow", id("powder_snow_cauldron")))
    },

    // Blocks that break when unsupported
    tickScenario("bamboo_unsupported", Blocks.BAMBOO.defaultBlockState(), Expected("block-break", "gravity", id("bamboo"))),
    tickScenario("cactus_unsupported", Blocks.CACTUS.defaultBlockState(), Expected("block-break", "gravity", id("cactus"))),
    tickScenario("chorus_plant_unsupported", Blocks.CHORUS_PLANT.defaultBlockState(), Expected("block-break", "gravity", id("chorus_plant"))),
    tickScenario("sugar_cane_unsupported", Blocks.SUGAR_CANE.defaultBlockState(), Expected("block-break", "gravity", id("sugar_cane"))),
    tickScenario("dripstone_unsupported", stalagmite, Expected("block-break", "gravity", id("pointed_dripstone"))),
    tickScenario(
        "scaffolding_unsupported",
        Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 1),
        Expected(source = "gravity", objectId = id("scaffolding")),
    ),
    tickScenario("hanging_vine_unsupported", Blocks.WEEPING_VINES.defaultBlockState(), Expected("block-break", "broke", id("weeping_vines"))),
    Scenario("torch_loses_support") { helper ->
        helper.setBlock(FLOOR, Blocks.STONE)
        helper.setBlock(CENTER, Blocks.TORCH)
        helper.setBlock(FLOOR, Blocks.AIR)
        helper.succeedWhenLogged(CENTER, Expected("block-break", "gravity", id("torch")))
    },
    Scenario("sand_lands") { helper ->
        helper.floor(1)
        helper.setBlock(CENTER.above(2), Blocks.SAND)
        helper.succeedWhenLogged(CENTER, Expected("block-place", "gravity", id("sand")))
    },

    // Growth, melting and decay
    Scenario("vine_grows_from_bone_meal") { helper ->
        helper.setBlock(CENTER.above(), Blocks.STONE)
        helper.setBlock(CENTER, Blocks.WEEPING_VINES)
        val pos = helper.absolutePos(CENTER)
        val state = helper.level.getBlockState(pos)
        (state.block as BonemealableBlock).performBonemeal(helper.level, helper.level.random, pos, state)
        helper.succeedWhenLogged(CENTER, Expected("block-place", "grow", id("weeping_vines")), radius = 4)
    },
    Scenario("rooted_dirt_grows_from_bone_meal") { helper ->
        helper.setBlock(CENTER, Blocks.ROOTED_DIRT)
        val pos = helper.absolutePos(CENTER)
        val state = helper.level.getBlockState(pos)
        (state.block as BonemealableBlock).performBonemeal(helper.level, helper.level.random, pos, state)
        helper.succeedWhenLogged(CENTER.below(), Expected("block-place", "grow", id("hanging_roots")))
    },
    Scenario("ice_melts") { helper ->
        helper.setBlock(CENTER, Blocks.ICE)
        val pos = helper.absolutePos(CENTER)
        // The game needs a bright light and a random tick, so call the step directly.
        val melt = IceBlock::class.java.getDeclaredMethod("melt", BlockState::class.java, Level::class.java, BlockPos::class.java)
        melt.isAccessible = true
        melt.invoke(Blocks.ICE, helper.level.getBlockState(pos), helper.level, pos)
        helper.succeedWhenLogged(CENTER, Expected("block-break", "melt", id("ice")))
    },
    tickScenario("coral_dies", Blocks.TUBE_CORAL.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false), Expected("block-change", "decay", id("dead_tube_coral"))),
    tickScenario("coral_fan_dies", Blocks.TUBE_CORAL_FAN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false), Expected("block-change", "decay", id("dead_tube_coral_fan"))),
    Scenario("coral_wall_fan_dies") { helper ->
        helper.setBlock(CENTER.south(), Blocks.STONE)
        helper.setBlock(CENTER, Blocks.TUBE_CORAL_WALL_FAN.defaultBlockState().setValue(CoralWallFanBlock.FACING, Direction.NORTH).setValue(BlockStateProperties.WATERLOGGED, false))
        val pos = helper.absolutePos(CENTER)
        helper.level.getBlockState(pos).tick(helper.level, pos, helper.level.random)
        helper.succeedWhenLogged(CENTER, Expected("block-change", "decay", id("dead_tube_coral_wall_fan")))
    },
    Scenario("farmland_trampled") { helper ->
        helper.setBlock(CENTER, Blocks.FARMLAND)
        val pos = helper.absolutePos(CENTER)
        val player = helper.player(CENTER.above())
        FarmlandBlock.turnToDirt(player, helper.level.getBlockState(pos), helper.level, pos)
        helper.succeedWhenLogged(CENTER, Expected("block-change", "trample", id("dirt"), player.scoreboardName))
    },
    Scenario("grass_covered_turns_to_dirt") { helper ->
        helper.setBlock(CENTER, Blocks.GRASS_BLOCK)
        helper.setBlock(CENTER.above(), Blocks.STONE)
        val pos = helper.absolutePos(CENTER)
        helper.level.getBlockState(pos).randomTick(helper.level, pos, helper.level.random)
        helper.succeedWhenLogged(CENTER, Expected("block-change", "trample", id("dirt")))
    },
    Scenario("anvil_wears_down") { helper ->
        helper.setBlock(CENTER, Blocks.ANVIL)
        val player = helper.player(CENTER.north())
        player.abilities.instabuild = false
        val pos = helper.absolutePos(CENTER)
        val menu = AnvilMenu(0, player.inventory, ContainerLevelAccess.create(helper.level, pos))
        val onTake = AnvilMenu::class.java.getDeclaredMethod(
            "onTake",
            net.minecraft.world.entity.player.Player::class.java,
            ItemStack::class.java,
        )
        onTake.isAccessible = true
        // Using an anvil only damages it some of the time.
        repeat(500) {
            if (helper.level.getBlockState(pos).`is`(Blocks.ANVIL)) onTake.invoke(menu, player, ItemStack(Items.DIAMOND))
        }
        helper.succeedWhenLogged(CENTER, Expected("block-change", "decay", id("chipped_anvil")))
    },

    // Explosions, statues, portals, vehicles
    Scenario("explosion_starts_fire") { helper ->
        helper.floor(4, Blocks.OAK_PLANKS)
        val at = helper.absoluteVec(Vec3.atCenterOf(CENTER))
        helper.level.explode(null, at.x, at.y, at.z, 3f, true, Level.ExplosionInteraction.TNT)
        helper.succeedWhenLogged(CENTER, Expected("block-place", "explosion", id("fire")), radius = 4)
    },
    Scenario("tnt_ignited_by_player") { helper ->
        helper.floor(3, Blocks.DIRT)
        helper.setBlock(CENTER, Blocks.TNT)
        val player = helper.player(CENTER.north(3))
        helper.useOn(player, CENTER, ItemStack(Items.FLINT_AND_STEEL))
        helper.succeedWhenLogged(FLOOR, Expected("block-break", "tnt", id("dirt"), player.scoreboardName), radius = 3)
    },
    Scenario("snow_golem_built") { helper ->
        helper.floor(2)
        helper.setBlock(CENTER, Blocks.SNOW_BLOCK)
        helper.setBlock(CENTER.above(), Blocks.SNOW_BLOCK)
        helper.setBlock(CENTER.above(2), Blocks.CARVED_PUMPKIN)
        helper.succeedWhenLogged(CENTER, Expected("block-break", "statue", id("snow_block")), radius = 2)
    },
    Scenario("nether_portal_lit") { helper ->
        for (x in 4..7) {
            for (y in 1..5) {
                if (x == 4 || x == 7 || y == 1 || y == 5) helper.setBlock(BlockPos(x, y, 5), Blocks.OBSIDIAN)
            }
        }
        helper.useOn(helper.player(CENTER.north()), BlockPos(5, 1, 5), ItemStack(Items.FLINT_AND_STEEL))
        helper.succeedWhenLogged(CENTER, Expected("block-place", "portal", id("nether_portal")), radius = 3)
    },
    Scenario("boat_breaks_lily_pad") { helper ->
        helper.floor(2)
        helper.setBlock(CENTER, Blocks.WATER)
        helper.setBlock(CENTER.above(), Blocks.LILY_PAD)
        helper.spawn(EntityType.OAK_BOAT, CENTER.above())
        helper.succeedWhenLogged(CENTER.above(), Expected("block-break", "vehicle", id("lily_pad")))
    },
    // Projectiles
    Scenario("chorus_flower_shot") { helper ->
        helper.setBlock(FLOOR, Blocks.END_STONE)
        helper.setBlock(CENTER, Blocks.CHORUS_FLOWER)
        val player = helper.player(CENTER.north())
        helper.shoot(player, onFire = false)
        helper.succeedWhenLogged(CENTER, Expected("block-break", "projectile", id("chorus_flower"), player.scoreboardName))
    },
    Scenario("decorated_pot_shot") { helper ->
        helper.setBlock(CENTER, Blocks.DECORATED_POT)
        val player = helper.player(CENTER.north())
        helper.shoot(player, onFire = false)
        helper.succeedWhenLogged(CENTER, Expected("block-break", "player", id("decorated_pot"), player.scoreboardName))
    },
    Scenario("campfire_lit_by_arrow") { helper ->
        helper.setBlock(CENTER, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false))
        helper.shoot(helper.player(CENTER.north()), onFire = true)
        helper.succeedWhenLogged(CENTER, Expected("block-change", "fire", id("campfire")))
    },
    Scenario("candle_lit_by_arrow") { helper ->
        helper.setBlock(CENTER, Blocks.CANDLE)
        helper.shoot(helper.player(CENTER.north()), onFire = true)
        helper.assertTrue(helper.getBlockState(CENTER).getValue(CandleBlock.LIT), Component.literal("candle should be lit"))
        helper.succeedWhenLogged(CENTER, Expected("block-change", "fire", id("candle")))
    },
    Scenario("candle_hit_by_plain_arrow_is_not_logged") { helper ->
        helper.setBlock(CENTER, Blocks.CANDLE)
        helper.shoot(helper.player(CENTER.north()), onFire = false)
        helper.assertTrue(!helper.getBlockState(CENTER).getValue(CandleBlock.LIT), Component.literal("candle should still be out"))
        helper.succeedIfNotLogged(CENTER, Expected("block-change", objectId = id("candle")))
    },

    // Beds, and what only happens in the Nether
    Scenario("bed_broken_in_creative") { helper ->
        helper.floor(2)
        helper.bed(CENTER, helper.level)
        val player = helper.player(CENTER.east())
        player.abilities.instabuild = true
        helper.breakAs(player, CENTER)
        helper.succeedWhenLogged(CENTER.north(), Expected("block-break", "player", id("red_bed"), player.scoreboardName))
    },
    Scenario("bed_explodes_in_nether") { helper ->
        val nether = helper.nether()
        helper.bed(IN_NETHER, nether)
        val player = helper.player(CENTER.east())
        val pos = helper.absolutePos(IN_NETHER)
        nether.getBlockState(pos).useWithoutItem(nether, player, BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))
        helper.succeedWhenLogged(IN_NETHER.north(), Expected("block-break", "interact", id("red_bed"), player.scoreboardName))
    },
    Scenario("wet_sponge_dries_in_nether") { helper ->
        helper.nether().setBlock(helper.absolutePos(IN_NETHER), Blocks.WET_SPONGE.defaultBlockState(), Block.UPDATE_ALL)
        helper.succeedWhenLogged(IN_NETHER, Expected("block-change", "dry", id("sponge")))
    },

    // Dripstone
    Scenario("cauldron_fills_from_dripstone") { helper ->
        helper.dripInto(CauldronBlock::class.java, Blocks.CAULDRON.defaultBlockState())
        helper.succeedWhenLogged(CENTER, Expected("block-change", "drip", id("water_cauldron")))
    },
    Scenario("cauldron_fills_more_from_dripstone") { helper ->
        helper.dripInto(LayeredCauldronBlock::class.java, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 1))
        helper.succeedWhenLogged(CENTER, Expected("block-change", "drip", id("water_cauldron")))
    },
    Scenario("snow_golem_leaves_trail") { helper ->
        helper.floor(4)
        helper.spawn(EntityType.SNOW_GOLEM, CENTER)
        helper.succeedWhenLogged(CENTER, Expected("block-place", "snow_golem", id("snow")), radius = 4)
    },
)
