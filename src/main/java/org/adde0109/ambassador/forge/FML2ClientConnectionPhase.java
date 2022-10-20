package org.adde0109.ambassador.forge;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.LoginPluginMessage;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.adde0109.ambassador.velocity.VelocityForgeClientConnectionPhase;
import org.adde0109.ambassador.velocity.VelocityLoginPayloadManager;
import org.apache.commons.collections4.map.PassiveExpiringMap;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class FML2ClientConnectionPhase extends VelocityForgeClientConnectionPhase {

  private static String OUTBOUND_CATCHER_NAME = "ambassador-catcher";

  private static final PassiveExpiringMap<String,RegisteredServer> TEMPORARY_FORCED = new PassiveExpiringMap<>(120, TimeUnit.SECONDS);

  private Throwable throwable;
  private RegisteredServer triedServer;
  private Continuation continuation;

  public FML2ClientConnectionPhase(VelocityForgeClientConnectionPhase.ClientPhase clientPhase, VelocityLoginPayloadManager payloadManager) {
    super(clientPhase,payloadManager);
  }
  public FML2ClientConnectionPhase(){
    super();
  }


  @Override
  public void handleLogin(ConnectedPlayer player, VelocityServer server, Continuation continuation) {
    this.continuation = continuation;
    final MinecraftConnection connection = player.getConnection();

    final Runnable defaultTask = () -> {
      Optional<RegisteredServer> initialFromConfig = player.getNextServerToTry();
      PlayerChooseInitialServerEvent event = new PlayerChooseInitialServerEvent(player,
              initialFromConfig.orElse(null));
      server.getEventManager().fire(event)
              .thenRun(() -> {
                Optional<RegisteredServer> toTry = event.getInitialServer();
                tryServer(player, toTry.orElse(null));
              });
    };

    forced = TEMPORARY_FORCED.remove(player.getUsername());
    connection.eventLoop().submit(defaultTask);
  }

  @Override
  public void reset(VelocityServerConnection serverConnection, ConnectedPlayer player, Runnable whenComplete) {
    TEMPORARY_FORCED.put(player.getUsername(),serverConnection.getServer());
    player.disconnect(Component.text("Please reconnect"));
  }

  @Override
  public void complete(VelocityServer server, ConnectedPlayer player, MinecraftConnection connection) {
    if (triedServer != null)
      player.sendMessage(Component.translatable("velocity.error.connecting-server-error",
              Component.text(triedServer.getServerInfo().getName())));
    if (clientPhase == ClientPhase.VANILLA) {
      player.setPhase(new FML2CRPMClientConnectionPhase(ClientPhase.VANILLA,getPayloadManager()));
    } else if (clientPhase == ClientPhase.MODLIST) {
      clientPhase = ClientPhase.MODDED;
      internalServerConnection = player.getConnectionInFlight();
      player.resetInFlightConnection();
      continuation.resume();
    }
  }

  private void tryServer(ConnectedPlayer player, RegisteredServer server) {
    if (server == null) {
      player.disconnect0(Component.translatable("velocity.error.no-available-servers",
              NamedTextColor.RED), true);
      return;
    }
    server.ping().whenCompleteAsync((msg,ex) -> {
      if (ex != null) {
        if (throwable == null)
          throwable = ex;
        tryServer(player, player.getNextServerToTry().orElse(null));
      } else {
        handlePingResponse(player, server, msg);
      }
    }, player.getConnection().eventLoop());
    }


  @Override
  public void forwardPayload(VelocityServerConnection serverConnection, LoginPluginMessage payload) {
    ByteBuf buf = payload.content().copy();
    String channel = ProtocolUtils.readString(buf);
    int length = ProtocolUtils.readVarInt(buf);
    int id = ProtocolUtils.readVarInt(buf);
    if (id == 1) {
      String[] mods = ProtocolUtils.readStringArray(buf);

      if (Arrays.stream(mods).anyMatch(s -> s.equals("clientresetpacket"))) {
        serverConnection.getPlayer().setPhase(new FML2CRPMClientConnectionPhase(ClientPhase.VANILLA,getPayloadManager()));
      }
    }
    super.forwardPayload(serverConnection, payload);
  }

  private void handlePingResponse(ConnectedPlayer player, RegisteredServer server, ServerPing ping) {
    if (ping.getModinfo().isEmpty()) {
      clientPhase = ClientPhase.VANILLA;
      continuation.resume();
    } else {
      player.createConnectionRequest(server).fireAndForget();
    }

  }
}
