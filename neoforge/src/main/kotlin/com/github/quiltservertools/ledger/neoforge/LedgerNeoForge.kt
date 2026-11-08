package com.github.quiltservertools.ledger.neoforge

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.commands.registerCommands
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent

@Mod(value = Ledger.MOD_ID, dist = [Dist.DEDICATED_SERVER])
class LedgerNeoForge {
    init {
        Ledger.initialize()

        val bus = NeoForge.EVENT_BUS
        // ServerStartingEvent fires after worlds load, but Ledger's world listener has to be in place before that.
        bus.addListener(ServerAboutToStartEvent::class.java) { Ledger.serverStarting(it.server) }
        bus.addListener(ServerStoppedEvent::class.java) { Ledger.serverStopped(it.server) }
        bus.addListener(RegisterCommandsEvent::class.java) { registerCommands(it.dispatcher) }
        bus.addListener(PermissionGatherEvent.Nodes::class.java) { event ->
            NeoForgePermissions.nodes.values.forEach { event.addNodes(it) }
        }
        bus.addListener(ServerStartedEvent::class.java) { Ledger.auditMixinsIfRequested() }
    }
}
