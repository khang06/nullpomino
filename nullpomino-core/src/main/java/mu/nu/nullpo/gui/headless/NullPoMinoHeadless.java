package mu.nu.nullpo.gui.headless;

import mu.nu.nullpo.game.component.RuleOptions;
import mu.nu.nullpo.game.play.GameEngine;
import mu.nu.nullpo.game.play.GameManager;
import mu.nu.nullpo.game.subsystem.ai.DummyAI;
import mu.nu.nullpo.game.subsystem.mode.VSBattleMode;
import mu.nu.nullpo.game.subsystem.wallkick.Wallkick;
import mu.nu.nullpo.util.GeneralUtil;
import net.omegaboshi.nullpomino.game.subsystem.randomizer.Randomizer;

public class NullPoMinoHeadless {

	public static GameManager gameManager = null;

	public static void main(String[] args) {
		System.out.println("hello");
		org.apache.log4j.BasicConfigurator.configure();

		gameManager = new GameManager(new RendererHeadless());
		gameManager.mode = new VSBattleMode();
		gameManager.init();

		for (int i = 0; i < gameManager.getPlayers(); i++) {
			// lol hardcoded path
			RuleOptions ruleopt = GeneralUtil
					.loadRule("D:\\Github\\nullpomino\\nullpomino-run\\config\\rule\\Standard.rul");
			gameManager.engine[i].ruleopt = ruleopt;
			// NEXTOrder generation algorithm
			if ((ruleopt.strRandomizer != null) && (ruleopt.strRandomizer.length() > 0)) {
				Randomizer randomizerObject = GeneralUtil.loadRandomizer(ruleopt.strRandomizer);
				gameManager.engine[i].randomizer = randomizerObject;
			}
			// Wallkick
			if ((ruleopt.strWallkick != null) && (ruleopt.strWallkick.length() > 0)) {
				Wallkick wallkickObject = GeneralUtil.loadWallkick(ruleopt.strWallkick);
				gameManager.engine[i].wallkick = wallkickObject;
			}
			// AI
			DummyAI aiObj;
			aiObj = GeneralUtil.loadAIPlayer("khangaroo.aibinding.TCPAIPlayer");
			gameManager.engine[i].ai = aiObj;
			gameManager.engine[i].aiMoveDelay = 0;
			gameManager.engine[i].aiThinkDelay = 0;
			gameManager.engine[i].aiUseThread = false; // doesn't matter in the case of TCPAIPlayer
			gameManager.engine[i].aiShowHint = false;
			gameManager.engine[i].aiPrethink = false;
			gameManager.engine[i].aiShowState = false;

			gameManager.engine[i].init();
		}

		while (true) {
			gameManager.updateAll();
			// lol no accessor
			if (((RendererHeadless) gameManager.receiver).gameDone) {
				System.out.println("Stats:");
				GameEngine engine = gameManager.engine[0];
				System.out.printf("Player 1: score=%d lines=%d state=%s\n", engine.statistics.score, engine.statistics.lines, engine.stat.name());
				engine = gameManager.engine[1];
				System.out.printf("Player 2: score=%d lines=%d state=%s\n", engine.statistics.score, engine.statistics.lines, engine.stat.name());
				gameManager.saveReplay();
				gameManager.reset();
				((RendererHeadless) gameManager.receiver).gameDone = false;
			}
		}
	}
}
