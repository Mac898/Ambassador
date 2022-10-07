package org.adde0109.ambassador.velocity;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import org.adde0109.ambassador.Ambassador;
import org.adde0109.ambassador.forge.ForgeFML2ClientConnectionPhase;

import java.util.Objects;

public class VelocityEventHandler {

  private final Ambassador ambassador;

  public VelocityEventHandler(Ambassador ambassador) {
    this.ambassador = ambassador;
  }

  @Subscribe
  public void onPermissionsSetupEvent(PermissionsSetupEvent event, Continuation continuation) {
    if(!(event.getSubject() instanceof ConnectedPlayer player)) {
      continuation.resume();
      return;
    }
    if (!(player.getPhase() instanceof VelocityForgeClientConnectionPhase phase)) {
      continuation.resume();
      return;
    }
    player.getConnection().eventLoop().submit(() -> phase.handleLogin(player,null,continuation));
  }

  @Subscribe
  public void onKickedFromServerEvent(KickedFromServerEvent event, Continuation continuation) {
    if (((ConnectedPlayer) event.getPlayer()).getPhase() instanceof ForgeFML2ClientConnectionPhase phase) {
      phase.handleKick(event);
    }
    continuation.resume();
  }
}
