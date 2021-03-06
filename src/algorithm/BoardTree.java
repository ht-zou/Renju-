package algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.Set;

import model.AbstractBoard;
import model.UnrestrictedBoard;

public class BoardTree {
	public static long nodesNum = 0;
	private static final int MAX_BIAS = 50;
	public static Map<Long, StatObj> statMap = new HashMap<>();
	public static Set<Long> cachedLocs = new HashSet<>();
	private static int[] branchingControl = {100, 15, 7, 6, 5, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3};

	// Meant to distinguish calls from outside from recursive calls.
	public static final int SPECIAL_HEURISTIC = 1000000001;
	/**
	 * Do alpha-beta pruning, returning the best move under given depth.
	 * 
	 * @param bd
	 * @param depth
	 * @param alpha
	 * @param beta
	 * @param maximizing Black is always the maximizing player, and
	 * 		white is always the minimizing player.
	 * @return
	 */
	public static int alphaBeta(UnrestrictedBoard bd, int depth, int alpha, 
			int beta, boolean maximizing) {
		return alphaBetaMem(bd, depth, alpha, beta, maximizing, new int[]{SPECIAL_HEURISTIC}, -1, 0, true);
	}
	
	public static int alphaBetaMem(UnrestrictedBoard bd, int depth, int alpha, 
			int beta, boolean maximizing, int[] value, int lastMove, int proceededDepth, boolean useControl) {
	    if (cachedLocs.contains(bd.getZobristHash()) && value[0] != SPECIAL_HEURISTIC) {
            value[0] = maximizing ? AbstractBoard.winning_score : -AbstractBoard.winning_score;
            return -1;
        }

        if (value[0] == SPECIAL_HEURISTIC)
            value[0] = 0;

		if (depth == 0) {
			nodesNum++;
			value[0] = bd.getHeuristics();
			return -1;
		}
		
		if ((lastMove >= 0 && bd.checkWinningLite(lastMove, !maximizing)) || 
				bd.someoneWins()) {
			nodesNum++;
			value[0] = bd.getHeuristics();
			return -1;
		}
		
		int winningMove = bd.canWinNextMove(maximizing);
		if (winningMove >= 0) {
			value[0] = maximizing ? AbstractBoard.winning_score : -AbstractBoard.winning_score;
			return winningMove;
		}

		// TODO control branching factor based on the current heuristics
		// TODO prune next move if the opponent has threats of fours and threes
		// Note that the former is easy, as implemented below; but the latter is
		// hard since we may use global refutation so that threats are to be 
		// dealt with in later moves.
		int[] blocking = new int[]{0};
		if (lastMove >= 0 && bd.formedThreat(!maximizing, lastMove, blocking)) {
			int onlyMove = blocking[0];
			bd.updateBoard(onlyMove, maximizing);
			alphaBetaMem(bd, depth - 1, alpha, beta, !maximizing, value, onlyMove, proceededDepth + 1, useControl);
			bd.withdrawMove(onlyMove);
			return onlyMove;
		}
		
		Set<Integer> nextMoves = new HashSet<>();
		// TODO expect ~30% speedup if find all threes could be further optimized
		Set<Integer> allThrees = bd.findAllThrees(!maximizing);
		if (!allThrees.isEmpty()) {
			Map<Integer, Integer> thLocations = bd.findThreatLocation(maximizing);
			nextMoves = allThrees;
			nextMoves.addAll(thLocations.keySet());
			if (proceededDepth == 0)
                System.out.println("opponent has threes: " + allThrees.size());
		} else {
			nextMoves = bd.nextMoves();
		}
		
		// Sort next moves based on increment of heuristic function in descending order
		// (larger heuristic improvements will be checked earlier)
		
		if (nextMoves.isEmpty()) {
			if (bd.boardFull()) {
				value[0] = bd.getHeuristics();
				return -1;
			} else {
				return bd.getFirstRandomMove();
			}
		}
		
		List<Integer> nmsorted = new ArrayList<>();
		Map<Integer, Integer> incMap = new HashMap<>();
		for (int mv : nextMoves) {
			int inc = bd.getInc(mv, maximizing);
			// TODO best-looking moves are checked (Allis, 1994)
			// TODO inc function might be buggy
			if (inc >= 0) {
				nmsorted.add(mv);
				incMap.put(mv, inc);
			}
		}
		
		nmsorted.sort(new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				// TODO Auto-generated method stub
				int v1 = incMap.get(o1);
				int v2 = incMap.get(o2);
				if (v1 == v2)
					return 0;
				return v1 > v2 ? -1 : 1;
			}
		});
		
		if (nmsorted.isEmpty())
			nmsorted.addAll(nextMoves);
		
		Integer maxInc = incMap.get(nmsorted.get(0));
		if (maxInc != null && maxInc >= AbstractBoard.winning_score / 2) {
			value[0] = maximizing ? AbstractBoard.winning_score : -AbstractBoard.winning_score;
			return nmsorted.get(0);
		}
		
		int bestMove = -1;
		if (maximizing) {
			int maxVal = Integer.MIN_VALUE;
			int count = 0;
			for (int move : nmsorted) {
			    if (useControl && count > branchingControl[proceededDepth])
			        // Branching control kicks in
                    break;
			    count++;
				bd.updateBoard(move, maximizing);
				nodesNum++;
				alphaBetaMem(bd, depth-1, alpha, beta, !maximizing, value, move, proceededDepth + 1, useControl);
				if (value[0] > maxVal) {
					maxVal = value[0];
					bestMove = move;
				}
				
				bd.withdrawMove(move);
				alpha = Math.max(alpha, maxVal);
				if (beta <= alpha)
					break;
			}
			
			value[0] = maxVal;
		} else {
			int minVal = Integer.MAX_VALUE;
			int count = 0;
			for (int move : nmsorted) {
                if (useControl && count > branchingControl[proceededDepth])
                    // Branching control kicks in
                    break;
			    count++;
				bd.updateBoard(move, maximizing);
				nodesNum++;
				alphaBetaMem(bd, depth-1, alpha, beta, !maximizing, value, move, proceededDepth + 1, useControl);
				if (value[0] < minVal) {
					minVal = value[0];
					bestMove = move;
				}
				bd.withdrawMove(move);
				beta = Math.min(beta, minVal);
				if (beta <= alpha)
                    break;
			}
			
			value[0] = minVal;
		}
		
		return bestMove;
	}
	
	public static int alphaBetaCustom(UnrestrictedBoard bd, int depth, int alpha, 
			int beta, boolean maximizing, int[] value, int lastMove, 
			int evalOscillation, int selectionThreshold, int levelToRoot, boolean useControl) {
        if (cachedLocs.contains(bd.getZobristHash()) && levelToRoot != 0) {
            value[0] = maximizing ? AbstractBoard.winning_score : -AbstractBoard.winning_score;
            System.out.println("This is saved by me.");
            return -1; // return value doesn't matter here, since it is not the root node (levelToRoot != 0)
        }

		if (depth == 0) {
			nodesNum++;
			value[0] = bd.getHeuristics();
			return -1;
		}
		
		if ((lastMove >= 0 && bd.checkWinningLite(lastMove, !maximizing)) || 
				bd.someoneWins()) {
			nodesNum++;
			value[0] = bd.getHeuristics();
			return -1;
		}
		
		int winningMove = bd.canWinNextMove(maximizing);
		if (winningMove >= 0) {
			value[0] = maximizing ? AbstractBoard.winning_score : -AbstractBoard.winning_score;
			return winningMove;
		}

		int[] blocking = new int[]{0};
		if (lastMove >= 0 && bd.formedThreat(!maximizing, lastMove, blocking)) {
			int onlyMove = blocking[0];
			bd.updateBoard(onlyMove, maximizing);
			alphaBetaMem(bd, depth - 1, alpha, beta, !maximizing, value, onlyMove, 1, useControl);
			bd.withdrawMove(onlyMove);
			return onlyMove;
		}
		
		Set<Integer> nextMoves = new HashSet<>();
		Set<Integer> allThrees = bd.findAllThrees(!maximizing);
		if (!allThrees.isEmpty()) {
			Map<Integer, Integer> thLocations = bd.findThreatLocation(maximizing);
			nextMoves = allThrees;
			nextMoves.addAll(thLocations.keySet());
            System.out.println("opponent has threes: " + allThrees.size());
        } else {
			nextMoves = bd.getStoneCount() > 3 ? bd.nextMoves() : bd.nextMovesRed();
		}
		
		// Sort next move based on increment of heuristic value
		if (nextMoves.isEmpty()) {
			if (bd.boardFull()) {
				value[0] = bd.getHeuristics();
				return -1;
			} else {
				return bd.getFirstRandomMove();
			}
		}
		
		List<Integer> nmsorted = new ArrayList<>();
		Map<Integer, Integer> incMap = new HashMap<>();
		for (int mv : nextMoves) {
			int inc = bd.getInc(mv, maximizing);
			if (inc >= selectionThreshold) {
                nmsorted.add(mv);
				incMap.put(mv, inc);
			}
		}
		
		nmsorted.sort(new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				// TODO Auto-generated method stub
				int v1 = incMap.get(o1);
				int v2 = incMap.get(o2);
				if (v1 == v2)
					return 0;
				return v1 > v2 ? -1 : 1;
			}
		});
		
		if (nmsorted.isEmpty())
			nmsorted.addAll(nextMoves);
		
		Integer maxInc = incMap.get(nmsorted.get(0));
		if (maxInc != null && maxInc >= AbstractBoard.winning_score / 2) {
			value[0] = maximizing ? AbstractBoard.winning_score : -AbstractBoard.winning_score;
			return nmsorted.get(0);
		}

		// TODO implement quiescent search here
        // Things to consider: what if the optimal move isn't on principal variation?
		int bestMove = -1;
		if (maximizing) {
			// case for black
			int maxVal = Integer.MIN_VALUE;
			for (int move : nmsorted) {
                System.out.println("Current move inc: " + incMap.get(move));
                bd.updateBoard(move, maximizing);
				bd.updateHash(move, maximizing);
				nodesNum++;
//				alphaBetaCustom(bd, depth-1, alpha, beta, !maximizing, value, move, evalOscillation, selectionThreshold, levelToRoot + 1);
				alphaBetaMem(bd, depth - 1, alpha, beta, !maximizing, value, move, 1, useControl);
				if (levelToRoot == 0) {
				    // sway the value
                    value[0] = value[0] + new Random().nextInt(2*evalOscillation + 1) - evalOscillation;
				    if (bd.getStoneCount() > 3)
				        value[0] = value[0] + getBias(bd.getZobristHash());
                }
				if (value[0] > maxVal) {
					maxVal = value[0];
					bestMove = move;
				}

                System.out.println("After oscillation: " + value[0]);
				bd.withdrawMove(move);
                System.out.println("Cur move heu: " + value[0]);
				// Just re-apply XOR operation to restore the hash.
				bd.updateHash(move, maximizing);
				alpha = Math.max(alpha, maxVal);
				if (beta <= alpha)
					break;
			}
		} else {
			// case for white
			int minVal = Integer.MAX_VALUE;
			for (int move : nmsorted) {
                System.out.println("Current move inc: " + incMap.get(move));
				bd.updateBoard(move, maximizing);
                bd.updateHash(move, maximizing);
				nodesNum++;
//				alphaBetaCustom(bd, depth-1, alpha, beta, !maximizing, value, move, evalOscillation, selectionThreshold, levelToRoot + 1);
				alphaBetaMem(bd, depth - 1, alpha, beta, !maximizing, value, move, 1, useControl);
				if (levelToRoot == 0) {
                    // sway the value
                    value[0] = value[0] + new Random().nextInt(2*evalOscillation + 1) - evalOscillation;
                    if (bd.getStoneCount() > 2)
                        value[0] = value[0] + getBias(bd.getZobristHash());
                }

                System.out.println("After oscillation: " + value[0]);
                if (value[0] < minVal) {
					minVal = value[0];
					bestMove = move;
				}
				bd.withdrawMove(move);
				// ditto
                bd.updateHash(move, maximizing);
				beta = Math.min(beta, minVal);
                System.out.println("Cur move heu: " + value[0]);
                if (beta <= alpha)
					break;
			}
		}

		if (levelToRoot == 0) {
            System.out.println("Board evaluation: " + bd.getHeuristics() + ", increment: " + bd.getInc(bestMove, maximizing));
        }
		return bestMove;
	}
	
	/**
	 * Threat-space search version 1; initial version only considers direct threats (4 stones).
	 * (This version of threat space searcher does not guarantee to find the shortest sequence).
	 * @param bd
	 * @param depth
	 * @param first
	 * @return
	 */
	public static int threatSpaceSearch(UnrestrictedBoard bd, int depth, boolean first, int[] blocking) {
		if (depth <= 0)
			return -100;
		
		int selfWinningLoc = bd.canWinNextMove(first);
		if (selfWinningLoc >= 0)
			return selfWinningLoc;
		int blockingLoc = bd.canWinNextMove(!first);
		if (blockingLoc >= 0) {
			blocking[0] = blockingLoc;
			return -200;
		}
		
		Map<Integer, Integer> threatAndCounter = bd.findThreatLocation(first);
		
		for (Entry<Integer, Integer> pair : threatAndCounter.entrySet()) {
			bd.updateBoard(pair.getKey(), first);
			bd.updateBoard(pair.getValue(), !first);
			int childResult = threatSpaceSearch(bd, depth - 1, first, blocking);
			bd.withdrawMove(pair.getKey());
			bd.withdrawMove(pair.getValue());
			if (childResult >= 0)
				return pair.getKey();
		}
		
		return -100;
	}
	
	/**
	 * Shortest-path threat space searcher: generates the winning threat sequence
	 * (if there are any) with the smallest number of rounds.
	 * @param bd
	 * @param depth
	 * @param first
	 * @param blocking
	 * @param pathLen stores the length of the shortest threat sequence
	 * @return
	 */
	public static int threatSpaceSearchV2(UnrestrictedBoard bd, int depth, boolean first, 
			int[] blocking, int[] pathLen) {
		if (depth <= 0)
			return -100;
		
		int selfWinningLoc = bd.canWinNextMove(first);
		if (selfWinningLoc >= 0) {
			pathLen[0] = 1;
			return selfWinningLoc;
		}
		
		int blockingLoc = bd.canWinNextMove(!first);
		if (blockingLoc >= 0) {
			blocking[0] = blockingLoc;
			return -200;
		}
		
		Map<Integer, Integer> threatAndCounter = bd.findThreatLocation(first);
		
		int bestChild = -1, bestLen = Integer.MAX_VALUE;
		for (Entry<Integer, Integer> pair : threatAndCounter.entrySet()) {
			int threatingMove = pair.getKey(), forcedMove = pair.getValue();
			bd.updateBoard(threatingMove, first);
			bd.updateBoard(forcedMove, !first);
			int childResult = threatSpaceSearchV2(bd, depth - 1, first, blocking, pathLen);
			bd.withdrawMove(threatingMove);
			bd.withdrawMove(forcedMove);
			if (childResult >= 0 && pathLen[0] < bestLen) {
				bestChild = threatingMove;
				bestLen = pathLen[0];
			}
		}
		
		if (bestChild >= 0) {
			pathLen[0] = bestLen + 2;
			return bestChild;
		}
		
		return -100;
	}
	
	public static List<Integer> genSortedNextMoves(UnrestrictedBoard bd, boolean first) {
		return null;
	}

    /**
     * Get evaluation bias in terms of black.
     * @param boardHash
     * @return
     */
    public static int getBias(long boardHash) {
	    if (!statMap.containsKey(boardHash)) {
	        // We have no data (which is true for most board positions)
            return 0;
        }
        StatObj obj = statMap.get(boardHash);

	    // TODO not using bias now
	    if (true)
	        return 0;
	    return getBiasFromHistory(obj.getWinRate(), obj.getLossRate(), obj.getSupport());
    }

	/**
	 * A smart function to get bias based on history
	 * @param winProbability the probability of winning
	 * @param loseProbability the probability of losing
	 * @param support total number of events happened
	 * @return the bias
	 */
	private static int getBiasFromHistory(double winProbability, double loseProbability, int support) {
	    int multiplier = winProbability > loseProbability ? 1 : -1;
		return multiplier * (int) (Math.exp(Math.abs(winProbability - loseProbability) - 1) * (Math.pow(Math.min(1, (double) support / 1000.0), 1.0/3.0)) * MAX_BIAS);
	}

	public static void printStatObj(AbstractBoard bd) {
	    if (statMap.containsKey(bd.getZobristHash())) {
	        StatObj obj = statMap.get(bd.getZobristHash());
            System.out.format("Wins: %s, Ties: %s, Losses: %s", obj.wins, obj.ties, obj.losses);
            System.out.println();
            return;
        }
        System.out.println("No data.");
    }

    public static void integrateNewGameRec(List<Integer> game, int result) {
		long currentHash = 0;
		for (int j = 0; j < game.size(); j++) {
			int lastMove = game.get(j);
			if (!statMap.containsKey(currentHash)) {
				statMap.put(currentHash, new StatObj(0,0,0));
			}

			if (result == 1) {
				statMap.get(currentHash).wins++;
			} else if (result == 2) {
				statMap.get(currentHash).losses++;
			} else {
				statMap.get(currentHash).ties++;
			}
			currentHash = Zobrist.zobristHash(lastMove, j % 2 == 0, currentHash);
		}
	}
}
