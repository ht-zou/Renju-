package model;

/**
 * Created by sireniazoe on 2017-05-26.
 */
public class RestrictedGame extends AbstractGame {

	@Override
	public void updateTurnStatus() {
		isPlayerTurn = !isPlayerTurn;
	}
}
