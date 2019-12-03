package khangaroo.aibinding;

import mu.nu.nullpo.game.component.Controller;
import mu.nu.nullpo.game.play.GameEngine;
import mu.nu.nullpo.game.play.GameEngine.Status;
import mu.nu.nullpo.game.subsystem.ai.DummyAI;
import java.net.*;
import java.io.*;

public class TCPAIPlayer extends DummyAI {
	private Socket sock;
	private DataInputStream sock_in;
	private DataOutputStream sock_out;
	
	private Boolean initialized = false;
	private Boolean first_shutdown = true;
	
	private static final int BOARD_WIDTH = 10;
	private static final int BOARD_HEIGHT = 17; // *visible* board height
	private static final int MAX_QUEUE = 6;
	
	private static final int OK = 0;
	private static final int GAME_ENDED = 1;
	private static final int READY_FROM_RESET = 2;

	private void sendObservation(GameEngine engine) throws IOException {
		// write board
		// could be more efficient...
		// expected size: 17x10 bytes
		if (engine.fieldWidth * (engine.fieldHeight - engine.fieldHiddenHeight) != BOARD_WIDTH * BOARD_HEIGHT)
			throw new UnsupportedOperationException("tried to send a field that isn't 17x10");
		byte[] field = new byte[engine.fieldWidth * (engine.fieldHeight - engine.fieldHiddenHeight)];
		for (int i = 0; i < engine.fieldWidth; i++) {
			for (int j = 0; j < engine.fieldHeight - engine.fieldHiddenHeight; j++) {
				if (!engine.field.getBlock(i, j).isEmpty())
					field[i + (j * engine.fieldWidth)] = 1;
			}
		}
		sock_out.write(field);
		
		// write queue
		// expected size: 6 bytes
		byte[] queue = new byte[MAX_QUEUE];
		for (int i = 0; i < MAX_QUEUE; i++)
			queue[i] = (byte)engine.nextPieceArrayID[i];
		if (engine.nextPieceCount < MAX_QUEUE) {
			for (int i = engine.nextPieceCount; i < MAX_QUEUE; i++)
				queue[i] = 8;
		}
		sock_out.write(queue);
		
		// write hold
		// expected size: 1 byte
		if (engine.holdPieceObject != null)
			sock_out.writeByte(engine.holdPieceObject.id);
		else
			sock_out.writeByte(8);
		
		// write incoming lines
		// expected size: 1 byte
		sock_out.writeByte(engine.meterValue);
	}
	
	@Override
	public String getName() {
		return "AI TCP Binding";
	}

	@Override
	public void init(GameEngine engine, int playerID) {
		first_shutdown = true;
		if (!initialized) {
			try {
				System.out.printf("tcpaiplayer %d actual init path\n", playerID);
				sock = new Socket("localhost", 1337 + playerID);
				sock_in = new DataInputStream(sock.getInputStream());
				sock_out = new DataOutputStream(sock.getOutputStream());
				//sock_out.writeInt(playerID);
				initialized = true;
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		} else {
			// looks like this was a reset!
			System.out.printf("tcpaiplayer %d init reset path\n", playerID);
		}
	}

	@Override
	public void setControl(GameEngine engine, int playerID, Controller ctrl) {
		//System.out.printf("tcpaiplayer %d setcontrol\n", playerID);
		/*
		 * this function sends:
		 * on-screen board
		 * queue
		 * hold
		 * incoming lines
		 * 
		 * maybe opponent board too?
		 */
		try {
			sock_out.writeInt(OK);
			sendObservation(engine);
			// wait for input from server...
			int input = sock_in.readInt();
			ctrl.setButtonBit(input);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(2);
		}
	}

	@Override
	public void shutdown(GameEngine engine, int playerID) {
		if (initialized && first_shutdown) {
			try {
				System.out.printf("tcpaiplayer %d shutdown status=%s\n", playerID, engine.stat.name());
				if (engine.stat != Status.SETTING) {
					// server is going to send an input anyway, ignore it
					sock_in.skip(4);
					sock_out.writeInt(GAME_ENDED);
					sendObservation(engine);
					sock_out.writeInt(engine.stat.ordinal());
					// shutdown gets called twice for some reason
					first_shutdown = false;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
