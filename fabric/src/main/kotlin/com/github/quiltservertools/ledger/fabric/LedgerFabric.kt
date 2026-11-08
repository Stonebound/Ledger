package com.github.quiltservertools.ledger.fabric

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.commands.registerCommands
import com.github.quiltservertools.ledger.network.packet.action.ActionS2CPacket
import com.github.quiltservertools.ledger.network.packet.handshake.HandshakeS2CPacket
import com.github.quiltservertools.ledger.network.packet.response.ResponseS2CPacket
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry

class LedgerFabric : DedicatedServerModInitializer {
    override fun onInitializeServer() {
        Ledger.initialize()

        ServerLifecycleEvents.SERVER_STARTING.register(Ledger::serverStarting)
        ServerLifecycleEvents.SERVER_STARTED.register { Ledger.auditMixinsIfRequested() }
        ServerLifecycleEvents.SERVER_STOPPED.register(Ledger::serverStopped)
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> registerCommands(dispatcher) }
        PayloadTypeRegistry.clientboundPlay().register(ActionS2CPacket.ID, ActionS2CPacket.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(HandshakeS2CPacket.ID, HandshakeS2CPacket.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(ResponseS2CPacket.ID, ResponseS2CPacket.CODEC)
    }
}
