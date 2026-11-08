package com.github.quiltservertools.ledger.fabric.gametest

import com.github.quiltservertools.ledger.gametest.SHARED_SCENARIOS
import com.github.quiltservertools.ledger.gametest.guarded
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component

private const val STRUCTURE = "ledger_gametest:empty"
private const val PADDING = 8
private const val MAX_TICKS = 3000

/** Fabric finds tests through annotated methods, so each one points at its shared scenario. */
@Suppress("FunctionNaming", "TooManyFunctions", "LargeClass")
class LedgerFabricGameTests {
    private fun run(name: String, helper: GameTestHelper) {
        helper.guarded { SHARED_SCENARIOS.first { it.name == name }.run(helper) }
    }

    /** Without this, a new shared scenario with no method here would silently never run on Fabric. */
    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true)
    fun every_shared_scenario_has_a_method(helper: GameTestHelper) {
        val methods = LedgerFabricGameTests::class.java.declaredMethods.map { it.name }.toSet()
        val missing = SHARED_SCENARIOS.map { it.name }.filterNot(methods::contains)
        helper.assertTrue(missing.isEmpty(), Component.literal("No Fabric test method for $missing"))
        helper.succeed()
    }

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun player_break(helper: GameTestHelper) = run("player_break", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun player_place(helper: GameTestHelper) = run("player_place", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun axe_strip(helper: GameTestHelper) = run("axe_strip", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun hoe_till(helper: GameTestHelper) = run("hoe_till", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun shovel_path(helper: GameTestHelper) = run("shovel_path", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun flint_and_steel(helper: GameTestHelper) = run("flint_and_steel", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun bucket_empty(helper: GameTestHelper) = run("bucket_empty", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun bucket_fill(helper: GameTestHelper) = run("bucket_fill", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun campfire_food(helper: GameTestHelper) = run("campfire_food", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun chest_insert_and_remove(helper: GameTestHelper) = run("chest_insert_and_remove", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun sign_edit(helper: GameTestHelper) = run("sign_edit", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun item_drop(helper: GameTestHelper) = run("item_drop", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun item_pick_up(helper: GameTestHelper) = run("item_pick_up", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun entity_kill(helper: GameTestHelper) = run("entity_kill", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun shear_sheep(helper: GameTestHelper) = run("shear_sheep", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun shear_snow_golem(helper: GameTestHelper) = run("shear_snow_golem", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun mount_and_dismount(helper: GameTestHelper) = run("mount_and_dismount", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun dispenser_water_bucket(helper: GameTestHelper) = run("dispenser_water_bucket", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun lava_meets_water(helper: GameTestHelper) = run("lava_meets_water", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun sponge_absorbs_water(helper: GameTestHelper) = run("sponge_absorbs_water", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun tnt_explosion(helper: GameTestHelper) = run("tnt_explosion", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun creeper_explosion(helper: GameTestHelper) = run("creeper_explosion", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun falling_sand(helper: GameTestHelper) = run("falling_sand", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun leaf_decay(helper: GameTestHelper) = run("leaf_decay", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun fire_burns_block(helper: GameTestHelper) = run("fire_burns_block", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_and_restore_chest(helper: GameTestHelper) = run("rollback_and_restore_chest", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun door_opened_by_player(helper: GameTestHelper) = run("door_opened_by_player", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun door_opened_by_redstone(helper: GameTestHelper) = run("door_opened_by_redstone", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun trapdoor_opened_by_player(helper: GameTestHelper) = run("trapdoor_opened_by_player", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun trapdoor_opened_by_redstone(helper: GameTestHelper) = run("trapdoor_opened_by_redstone", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun fence_gate_opened_by_player(helper: GameTestHelper) = run("fence_gate_opened_by_player", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun fence_gate_opened_by_redstone(helper: GameTestHelper) = run("fence_gate_opened_by_redstone", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun button_pressed(helper: GameTestHelper) = run("button_pressed", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun lever_pulled(helper: GameTestHelper) = run("lever_pulled", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun repeater_changed(helper: GameTestHelper) = run("repeater_changed", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun comparator_changed(helper: GameTestHelper) = run("comparator_changed", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun daylight_detector_changed(helper: GameTestHelper) = run("daylight_detector_changed", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun note_block_tuned(helper: GameTestHelper) = run("note_block_tuned", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun flower_pot_emptied(helper: GameTestHelper) = run("flower_pot_emptied", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun flower_pot_planted(helper: GameTestHelper) = run("flower_pot_planted", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun cake_eaten(helper: GameTestHelper) = run("cake_eaten", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun candle_put_on_cake(helper: GameTestHelper) = run("candle_put_on_cake", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun candle_put_out(helper: GameTestHelper) = run("candle_put_out", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun campfire_put_out(helper: GameTestHelper) = run("campfire_put_out", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun jukebox_disc_inserted(helper: GameTestHelper) = run("jukebox_disc_inserted", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun copper_waxed(helper: GameTestHelper) = run("copper_waxed", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun copper_unwaxed(helper: GameTestHelper) = run("copper_unwaxed", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun sign_waxed(helper: GameTestHelper) = run("sign_waxed", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun copper_statue_posed(helper: GameTestHelper) = run("copper_statue_posed", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun chiseled_bookshelf_add_and_remove(helper: GameTestHelper) = run("chiseled_bookshelf_add_and_remove", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun decorated_pot_filled(helper: GameTestHelper) = run("decorated_pot_filled", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun shelf_add_and_remove(helper: GameTestHelper) = run("shelf_add_and_remove", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun lectern_add_and_take_book(helper: GameTestHelper) = run("lectern_add_and_take_book", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun double_chest_second_half(helper: GameTestHelper) = run("double_chest_second_half", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun shift_click_into_barrel(helper: GameTestHelper) = run("shift_click_into_barrel", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun chest_contents_blown_up(helper: GameTestHelper) = run("chest_contents_blown_up", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun bucket_waterlogs_slab(helper: GameTestHelper) = run("bucket_waterlogs_slab", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun dispenser_bucket_picks_up_water(helper: GameTestHelper) = run("dispenser_bucket_picks_up_water", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun dispenser_water_washes_away_torch(helper: GameTestHelper) = run("dispenser_water_washes_away_torch", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun dispenser_places_shulker_box(helper: GameTestHelper) = run("dispenser_places_shulker_box", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun sponge_gets_wet(helper: GameTestHelper) = run("sponge_gets_wet", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun cauldron_filled_from_bucket(helper: GameTestHelper) = run("cauldron_filled_from_bucket", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun cauldron_emptied_into_bucket(helper: GameTestHelper) = run("cauldron_emptied_into_bucket", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun cauldron_used_by_bottle(helper: GameTestHelper) = run("cauldron_used_by_bottle", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun cauldron_filled_from_bottle(helper: GameTestHelper) = run("cauldron_filled_from_bottle", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun cauldron_puts_out_burning_mob(helper: GameTestHelper) = run("cauldron_puts_out_burning_mob", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun cauldron_collects_snow(helper: GameTestHelper) = run("cauldron_collects_snow", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun cauldron_collects_more_snow(helper: GameTestHelper) = run("cauldron_collects_more_snow", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun bamboo_unsupported(helper: GameTestHelper) = run("bamboo_unsupported", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun cactus_unsupported(helper: GameTestHelper) = run("cactus_unsupported", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun chorus_plant_unsupported(helper: GameTestHelper) = run("chorus_plant_unsupported", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun sugar_cane_unsupported(helper: GameTestHelper) = run("sugar_cane_unsupported", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun dripstone_unsupported(helper: GameTestHelper) = run("dripstone_unsupported", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun scaffolding_unsupported(helper: GameTestHelper) = run("scaffolding_unsupported", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun hanging_vine_unsupported(helper: GameTestHelper) = run("hanging_vine_unsupported", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun torch_loses_support(helper: GameTestHelper) = run("torch_loses_support", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun sand_lands(helper: GameTestHelper) = run("sand_lands", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun vine_grows_from_bone_meal(helper: GameTestHelper) = run("vine_grows_from_bone_meal", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rooted_dirt_grows_from_bone_meal(helper: GameTestHelper) = run("rooted_dirt_grows_from_bone_meal", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun ice_melts(helper: GameTestHelper) = run("ice_melts", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun coral_dies(helper: GameTestHelper) = run("coral_dies", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun coral_fan_dies(helper: GameTestHelper) = run("coral_fan_dies", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun coral_wall_fan_dies(helper: GameTestHelper) = run("coral_wall_fan_dies", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun farmland_trampled(helper: GameTestHelper) = run("farmland_trampled", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun grass_covered_turns_to_dirt(helper: GameTestHelper) = run("grass_covered_turns_to_dirt", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun anvil_wears_down(helper: GameTestHelper) = run("anvil_wears_down", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun explosion_starts_fire(helper: GameTestHelper) = run("explosion_starts_fire", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun tnt_ignited_by_player(helper: GameTestHelper) = run("tnt_ignited_by_player", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun snow_golem_built(helper: GameTestHelper) = run("snow_golem_built", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun nether_portal_lit(helper: GameTestHelper) = run("nether_portal_lit", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun boat_breaks_lily_pad(helper: GameTestHelper) = run("boat_breaks_lily_pad", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun chorus_flower_shot(helper: GameTestHelper) = run("chorus_flower_shot", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun decorated_pot_shot(helper: GameTestHelper) = run("decorated_pot_shot", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun campfire_lit_by_arrow(helper: GameTestHelper) = run("campfire_lit_by_arrow", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun candle_lit_by_arrow(helper: GameTestHelper) = run("candle_lit_by_arrow", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun candle_hit_by_plain_arrow_is_not_logged(helper: GameTestHelper) = run("candle_hit_by_plain_arrow_is_not_logged", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun bed_broken_in_creative(helper: GameTestHelper) = run("bed_broken_in_creative", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun bed_explodes_in_nether(helper: GameTestHelper) = run("bed_explodes_in_nether", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun wet_sponge_dries_in_nether(helper: GameTestHelper) = run("wet_sponge_dries_in_nether", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun cauldron_fills_from_dripstone(helper: GameTestHelper) = run("cauldron_fills_from_dripstone", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun cauldron_fills_more_from_dripstone(helper: GameTestHelper) = run("cauldron_fills_more_from_dripstone", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun snow_golem_leaves_trail(helper: GameTestHelper) = run("snow_golem_leaves_trail", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun sheep_dyed(helper: GameTestHelper) = run("sheep_dyed", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun wolf_collar_dyed(helper: GameTestHelper) = run("wolf_collar_dyed", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun cat_collar_dyed(helper: GameTestHelper) = run("cat_collar_dyed", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun armor_stand_dressed_undressed_and_broken(helper: GameTestHelper) = run("armor_stand_dressed_undressed_and_broken", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun item_frame_filled_rotated_emptied_and_broken(helper: GameTestHelper) = run("item_frame_filled_rotated_emptied_and_broken", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun item_frame_loses_support(helper: GameTestHelper) = run("item_frame_loses_support", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun creeper_ignited_by_player(helper: GameTestHelper) = run("creeper_ignited_by_player", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun end_crystal_blown_up_by_player(helper: GameTestHelper) = run("end_crystal_blown_up_by_player", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun ghast_fireball_blames_its_target(helper: GameTestHelper) = run("ghast_fireball_blames_its_target", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun ender_dragon_breaks_blocks(helper: GameTestHelper) = run("ender_dragon_breaks_blocks", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun mob_converted(helper: GameTestHelper) = run("mob_converted", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun evoker_turns_sheep_red(helper: GameTestHelper) = run("evoker_turns_sheep_red", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun silverfish_calls_for_help(helper: GameTestHelper) = run("silverfish_calls_for_help", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun silverfish_infests_block(helper: GameTestHelper) = run("silverfish_infests_block", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun ravager_breaks_leaves(helper: GameTestHelper) = run("ravager_breaks_leaves", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun lightning_starts_fire(helper: GameTestHelper) = run("lightning_starts_fire", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun frost_walker_freezes_water(helper: GameTestHelper) = run("frost_walker_freezes_water", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun copper_golem_waxed_and_scraped(helper: GameTestHelper) = run("copper_golem_waxed_and_scraped", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun copper_golem_sheared(helper: GameTestHelper) = run("copper_golem_sheared", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun copper_golem_throws_held_item(helper: GameTestHelper) = run("copper_golem_throws_held_item", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun copper_golem_turns_to_statue(helper: GameTestHelper) = run("copper_golem_turns_to_statue", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun copper_golem_statue_reanimated(helper: GameTestHelper) = run("copper_golem_statue_reanimated", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun copper_golem_moves_items(helper: GameTestHelper) = run("copper_golem_moves_items", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_block_place(helper: GameTestHelper) = run("rollback_block_place", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_block_change(helper: GameTestHelper) = run("rollback_block_change", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_keeps_block_properties(helper: GameTestHelper) = run("rollback_keeps_block_properties", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_sign_text(helper: GameTestHelper) = run("rollback_sign_text", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_block_entity_changed_without_data(helper: GameTestHelper) = run("rollback_block_entity_changed_without_data", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_lectern_book(helper: GameTestHelper) = run("rollback_lectern_book", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_item_insert(helper: GameTestHelper) = run("rollback_item_insert", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_item_drop(helper: GameTestHelper) = run("rollback_item_drop", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_item_pick_up(helper: GameTestHelper) = run("rollback_item_pick_up", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_entity_kill(helper: GameTestHelper) = run("rollback_entity_kill", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_entity_change(helper: GameTestHelper) = run("rollback_entity_change", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_item_frame(helper: GameTestHelper) = run("rollback_item_frame", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_armor_stand(helper: GameTestHelper) = run("rollback_armor_stand", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_fence_gate(helper: GameTestHelper) = run("rollback_fence_gate", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_repeater(helper: GameTestHelper) = run("rollback_repeater", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_comparator(helper: GameTestHelper) = run("rollback_comparator", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun rollback_door_both_halves(helper: GameTestHelper) = run("rollback_door_both_halves", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_search_filters(helper: GameTestHelper) = run("command_search_filters", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_page(helper: GameTestHelper) = run("command_page", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_rollback_and_restore(helper: GameTestHelper) = run("command_rollback_and_restore", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_rollback_chest_with_contents(helper: GameTestHelper) = run("command_rollback_chest_with_contents", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_rollback_needs_specific_search(helper: GameTestHelper) = run("command_rollback_needs_specific_search", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_rollback_nothing_found(helper: GameTestHelper) = run("command_rollback_nothing_found", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_rollback_continues_after_failed_action(helper: GameTestHelper) = run("command_rollback_continues_after_failed_action", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_setblock_and_fill(helper: GameTestHelper) = run("command_setblock_and_fill", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_setblock_block_entity_rollback_and_restore(helper: GameTestHelper) = run("command_setblock_block_entity_rollback_and_restore", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_purge(helper: GameTestHelper) = run("command_purge", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_status(helper: GameTestHelper) = run("command_status", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_player(helper: GameTestHelper) = run("command_player", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_inspect_position(helper: GameTestHelper) = run("command_inspect_position", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun inspect_mode_stops_placing_and_breaking(helper: GameTestHelper) = run("inspect_mode_stops_placing_and_breaking", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun command_tp(helper: GameTestHelper) = run("command_tp", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun clicking_a_position_in_chat_teleports(helper: GameTestHelper) = run("clicking_a_position_in_chat_teleports", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun clicking_a_page_in_chat_turns_the_page(helper: GameTestHelper) = run("clicking_a_page_in_chat_turns_the_page", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun preview_apply(helper: GameTestHelper) = run("preview_apply", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun preview_cancel(helper: GameTestHelper) = run("preview_cancel", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun preview_of_entities_and_containers(helper: GameTestHelper) = run("preview_of_entities_and_containers", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun non_op_cannot_use_commands(helper: GameTestHelper) = run("non_op_cannot_use_commands", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun player_name_taken_by_someone_else(helper: GameTestHelper) = run("player_name_taken_by_someone_else", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun player_renamed(helper: GameTestHelper) = run("player_renamed", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun player_who_never_joined(helper: GameTestHelper) = run("player_who_never_joined", helper)

    @GameTest(structure = STRUCTURE, padding = PADDING, skyAccess = true, maxTicks = MAX_TICKS)
    fun queue_survives_an_action_that_fails(helper: GameTestHelper) = run("queue_survives_an_action_that_fails", helper)
}
