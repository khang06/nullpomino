package mu.nu.nullpo.gui.headless;

import mu.nu.nullpo.game.event.EventReceiver;
import mu.nu.nullpo.game.play.GameEngine;
import mu.nu.nullpo.game.play.GameManager;
import mu.nu.nullpo.util.CustomProperties;

public class RendererHeadless extends EventReceiver {
	// For most of the methods, we're not going to override them anyway, even if required
	
	public boolean gameDone = false;

	@Override
	public void onGameOver(GameEngine engine, int playerID) {
		System.out.println("onGameOver called");
		gameDone = true;
	}

	@Override
	public void saveReplay(GameManager owner, CustomProperties prop) {
		// lol more hardcoded paths
		super.saveReplay(owner, prop, "D:\\Github\\nullpomino\\nullpomino-run\\target\\install\\replay");
	}
}
