package com.github.quiltservertools.ledger.fabric.gametest

import com.github.quiltservertools.ledger.gametest.SHARED_SCENARIOS
import com.github.quiltservertools.ledger.gametest.guarded
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper

private const val STRUCTURE = "ledger_gametest:empty"
private const val PADDING = 8

/** Fabric finds tests through annotated methods, so each one points at its shared scenario. */
@Suppress("FunctionNaming", "TooManyFunctions")
class LedgerFabricGameTests {
    private fun run(name: String, helper: GameTestHelper) {
        helper.guarded { SHARED_SCENARIOS.first { it.name == name }.run(helper) }
    }

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun player_break(helper: GameTestHelper) = run("player_break", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun player_place(helper: GameTestHelper) = run("player_place", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun axe_strip(helper: GameTestHelper) = run("axe_strip", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun hoe_till(helper: GameTestHelper) = run("hoe_till", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun shovel_path(helper: GameTestHelper) = run("shovel_path", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun flint_and_steel(helper: GameTestHelper) = run("flint_and_steel", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun bucket_empty(helper: GameTestHelper) = run("bucket_empty", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun bucket_fill(helper: GameTestHelper) = run("bucket_fill", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun campfire_food(helper: GameTestHelper) = run("campfire_food", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun chest_insert_and_remove(helper: GameTestHelper) = run("chest_insert_and_remove", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun sign_edit(helper: GameTestHelper) = run("sign_edit", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun item_drop(helper: GameTestHelper) = run("item_drop", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun item_pick_up(helper: GameTestHelper) = run("item_pick_up", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun entity_kill(helper: GameTestHelper) = run("entity_kill", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun shear_sheep(helper: GameTestHelper) = run("shear_sheep", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun shear_snow_golem(helper: GameTestHelper) = run("shear_snow_golem", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun mount_and_dismount(helper: GameTestHelper) = run("mount_and_dismount", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun dispenser_water_bucket(helper: GameTestHelper) = run("dispenser_water_bucket", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun lava_meets_water(helper: GameTestHelper) = run("lava_meets_water", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun sponge_absorbs_water(helper: GameTestHelper) = run("sponge_absorbs_water", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun tnt_explosion(helper: GameTestHelper) = run("tnt_explosion", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun creeper_explosion(helper: GameTestHelper) = run("creeper_explosion", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun falling_sand(helper: GameTestHelper) = run("falling_sand", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun leaf_decay(helper: GameTestHelper) = run("leaf_decay", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 2400)
    fun fire_burns_block(helper: GameTestHelper) = run("fire_burns_block", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = 600)
    fun rollback_and_restore_chest(helper: GameTestHelper) = run("rollback_and_restore_chest", helper)
}
