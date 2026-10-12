package spawnparty.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import spawnparty.chat.PrefixService;
import spawnparty.chat.PromptService;
import spawnparty.api.SetupMenuHost;
import spawnparty.gui.PartyChestMenu;
import spawnparty.gui.PartyMenus;
import spawnparty.spawn.HoldingCamera;
import spawnparty.spawn.PartySpawnService;
import spawnparty.util.Servers;

@Mixin(ServerPlayer.class)
public class ServerPlayerRespawnMixin {

	@Unique
	private boolean spawnparty$reopenLocked;

	@Inject(method = "setRespawnPosition", at = @At("RETURN"))
	private void spawnparty$restorePartyOrigin(ServerPlayer.RespawnConfig config, boolean showMessage, CallbackInfo ci) {
		if (config == null) {
			ServerPlayer self = (ServerPlayer) (Object) this;
			var server = Servers.of(self);
			if (server != null) {
				server.execute(() -> PartySpawnService.restorePartyOriginIfNeeded(self));
			}
		}
	}

	@Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
	private void spawnparty$tabPrefix(CallbackInfoReturnable<Component> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		Component name = PrefixService.tabName(self);
		if (name != null) {
			cir.setReturnValue(name);
		}
	}

	@Inject(method = "closeContainer", at = @At("HEAD"))
	private void spawnparty$noteLockedChest(CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		this.spawnparty$reopenLocked = self.containerMenu instanceof PartyChestMenu menu && !menu.allowClose();
	}

	@Inject(method = "closeContainer", at = @At("RETURN"))
	private void spawnparty$reopenLockedChests(CallbackInfo ci) {
		if (!this.spawnparty$reopenLocked) return;
		this.spawnparty$reopenLocked = false;
		if (SetupMenuHost.replacingMenu()) return;
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (!HoldingCamera.isHolding(self.getUUID())) return;
		if (PromptService.hasPending(self.getUUID())) return;
		PartyMenus.reopenIfHolding(self);
	}
}
