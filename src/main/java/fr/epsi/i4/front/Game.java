package fr.epsi.i4.front;

import fr.decisiontree.Config;
import fr.decisiontree.DecisionTree;
import fr.decisiontree.model.Result;
import fr.epsi.i4.back.model.*;
import fr.epsi.i4.back.model.board.Board;
import fr.epsi.i4.back.model.board.Case;
import fr.epsi.i4.back.model.board.Direction;
import fr.epsi.i4.back.model.board.content.Weight;
import fr.epsi.i4.util.Randomizer;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static fr.epsi.i4.back.model.board.Direction.*;

public class Game extends JFrame implements KeyListener {

	public static final int caseSize = 100;

	private static final String DIRECTION = "Direction";

	private final Board board;

	private final Mode mode;

	private final int exploration;

	private final int balls;

	private final int gonzesse;

	private int rounds = 0;

	private int win = 0;

	private int death = 0;

	private PathFinder pathFinder;

	private DecisionTree decisionTree;

	private int maxGames;

	public Game(Board board, Mode mode, int balls, int maxGames) {
		this.board = board;
		this.mode = mode;
		this.exploration = 10;
		this.balls = balls / 10;
		this.gonzesse = (10 - this.balls);
		this.maxGames = maxGames;
		this.pathFinder = new PathFinder(board);
		initWindow();
		initDecisionTree();
	}

	private void initWindow() {
		setTitle("Wumpus");
		setSize(board.getWidth() * caseSize, board.getHeight() * caseSize);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setContentPane(new FrontGame(board));
		addKeyListener(this);
		setFocusable(true);
		setFocusTraversalKeysEnabled(false);
	}

	private void initDecisionTree() {
		Config config = new Config("./decisionTree");
//		config.addAttribut("Actuelle", Weight.getNames());
		String[] params = new String[]{
				Weight.SAFE.name(),
				Weight.DEFAULT.name(),
				Weight.POSSIBLE_PIT.name(),
				Weight.POSSIBLE_WUMPUS.name(),
				Weight.PIT.name(),
				Weight.POSSIBLE_PIT_OR_WUMPUS.name(),
				Weight.WUMPUS.name(),
				Weight.VISITED.name()
		};
		config.addAttribut(LEFT.name(), params);
		config.addAttribut(RIGHT.name(), params);
		config.addAttribut(UP.name(), params);
		config.addAttribut(DOWN.name(), params);
		config.addAttribut(DIRECTION, LEFT.name(), RIGHT.name(), UP.name(), DOWN.name());

		config.addDecision("Vivant");
		config.addDecision("Mort");

		decisionTree = new DecisionTree(config);
	}

	public FrontGame getGame() {
		return (FrontGame) getContentPane();
	}

	public void play() {
		refresh();
		setVisible(true);
		if (mode.equals(Mode.AUTO)) {
			while (win + death < maxGames) {
//                try {
//                    Thread.sleep(500);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
				if (rounds > 200) {
					death++;
					System.out.println("L'agent est décédé...");
					reset();
				} else {
					playRound();
				}
			}
			System.exit(0);
		}
	}

	public void reset() {
		displayResult();
		System.out.println("------------------------");
		System.out.println("New Game");
		System.out.println("------------------------");
		rounds = 0;
		board.regenerate();
		refresh();
	}

	private void playRound() {
//        System.out.println(board.toString());

		// Cases autour de l'agent
		HashMap<Direction, Case> casesAround = board.getAgent().getCasesAround();

		Direction direction;
		Stack<Case> pathFinded = pathFinder.findPath();
		Stack<Case> pathFindedCloned = new Stack<>();
		pathFindedCloned.clone(pathFinded);
		// pathFinded[0] correspond à la case actuelle donc on ne prends pas en compte ce deplacement
		while (pathFinded.size() > 1) {
			try {
				rounds += processDirection(board.getDirectionByCase(pathFinded.antePop()));
			} catch (NullPointerException e) {
				e.printStackTrace();
				System.out.println(pathFinded.toString());
				System.out.println(pathFindedCloned.toString());
			}
			refresh();
		}

		// Défini les directions possibles
		List<Direction> directionsPossibles = new ArrayList<>();
		if (board.getAgent().getX() > 1 && board.getAgent().getCasesAround().get(LEFT).isMegaSafe()) {
			directionsPossibles.add(LEFT);
		}
		if (board.getAgent().getCasesAround().get(RIGHT).isMegaSafe()) {
			directionsPossibles.add(RIGHT);
		}
		if (board.getAgent().getCasesAround().get(UP).isMegaSafe()) {
			directionsPossibles.add(UP);
		}
		if (board.getAgent().getY() > 1 && board.getAgent().getCasesAround().get(DOWN).isMegaSafe()) {
			directionsPossibles.add(Direction.DOWN);
		}

		if (board.getAgent().getBackCounter() > 0) {
			directionsPossibles.remove(board.getAgent().getDirection().getOpposite());
		}

		// Initialisation de l'entry
		HashMap<String, String> entry = new HashMap<>();
		for (Direction dir : Direction.values()) {
			if (!casesAround.get(dir).getWeight().equals(Weight.WALL)) {
				entry.put(dir.name(), casesAround.get(dir).getWeight().name());
			}
		}

		// Utiliser l'arbre de décision
		Result result;
		List<PossibleChoice> possibleChoices = new ArrayList<>();
		int i = 0;
		while (i < directionsPossibles.size()) {
			entry.put(DIRECTION, directionsPossibles.get(i).name());
			result = decisionTree.decide(entry);
			// Gestion du ratio
			if (result != null) {
				double ratio = result.getRatio();
				verifierSafe(possibleChoices, result, directionsPossibles.get(i));
				if (result.getValue().equals("Vivant")) {
					//Si la case à deja ete visité on l'ajoute une seule fois sinon on ajoute la possibilité normalement
					if (!explore(possibleChoices, result, directionsPossibles.get(i)) && ratio > 0.9d) {
						for (int j = 0; j < (int) (ratio * 10); j++) {
							possibleChoices.add(new PossibleChoice(result, directionsPossibles.get(i)));
						}
					}
//				} else {
//					for (int j = 0; j < (int) ((1 - ratio) * 10); j++) {
//						possibleChoices.add(new PossibleChoice(result, directionsPossibles.get(i)));
//					}
				}
			} else {
				possibleChoices.removeAll(possibleChoices);
				possibleChoices.add(new PossibleChoice(result, directionsPossibles.get(i)));
				i = directionsPossibles.size();
			}
			entry.remove(DIRECTION);
			i++;
		}

		// Process result
		Direction choice;
		Randomizer randomizer;
		if (possibleChoices.isEmpty()) {
			randomizer = new Randomizer(0, directionsPossibles.size() - 1);
			choice = directionsPossibles.get(randomizer.randomize());
		} else {
			randomizer = new Randomizer(0, possibleChoices.size() - 1);
			choice = possibleChoices.get(randomizer.randomize()).getChoice();
		}
		entry.put(DIRECTION, choice.name());

		// Incrémente les tours et process result
		rounds += processDirection(choice);

		// Mise à jour de l'état du jeu
		updateGameState(entry);

		// Mise à jour de l'affichage
		refresh();
	}

	public void refresh() {
		//getGame().refresh();
	}

	private int processDirection(Direction treeResult) {
		Agent agent = board.getAgent();
		return agent.move(treeResult);
	}

	private boolean verifierSafe(List<PossibleChoice> possibleChoices, Result result, Direction direction) {
		int x = board.getAgent().getX();
		int y = board.getAgent().getY();
		boolean isSaferThanSomethingElse = false;
		switch (direction) {
			case UP:
				if (board.getCase(x, y + 1).getWeight().equals(Weight.SAFE)) {
					for (int j = 0; j < exploration; j++) {
						possibleChoices.add(new PossibleChoice(result, direction));
					}
					isSaferThanSomethingElse = true;
				}
				break;
			case DOWN:
				if (board.getCase(x, y - 1).getWeight().equals(Weight.SAFE)) {
					for (int j = 0; j < exploration; j++) {
						possibleChoices.add(new PossibleChoice(result, direction));
					}
					isSaferThanSomethingElse = true;
				}
				break;
			case LEFT:
				if (board.getCase(x - 1, y).getWeight().equals(Weight.SAFE)) {
					for (int j = 0; j < exploration; j++) {
						possibleChoices.add(new PossibleChoice(result, direction));
					}
					isSaferThanSomethingElse = true;
				}
				break;
			case RIGHT:
				if (board.getCase(x + 1, y).getWeight().equals(Weight.SAFE)) {
					for (int j = 0; j < exploration; j++) {
						possibleChoices.add(new PossibleChoice(result, direction));
					}
					isSaferThanSomethingElse = true;
				}
				break;
		}
		return isSaferThanSomethingElse;
	}

	private boolean explore(List<PossibleChoice> possibleChoices, Result result, Direction direction) {
		int x = board.getAgent().getX();
		int y = board.getAgent().getY();
		boolean toExplore = false;
		switch (direction) {
			case UP:
				if (board.getCase(x, y + 1).getWeight().equals(Weight.VISITED)) {
					for (int j = 0; j < balls; j++) {
						possibleChoices.add(new PossibleChoice(result, direction));
					}
					toExplore = true;
				}
				break;
			case DOWN:
				if (board.getCase(x, y - 1).getWeight().equals(Weight.VISITED)) {
					for (int j = 0; j < balls; j++) {
						possibleChoices.add(new PossibleChoice(result, direction));
					}
					toExplore = true;
				}
				break;
			case LEFT:
				if (board.getCase(x - 1, y).getWeight().equals(Weight.VISITED)) {
					for (int j = 0; j < balls; j++) {
						possibleChoices.add(new PossibleChoice(result, direction));
					}
					toExplore = true;
				}
				break;
			case RIGHT:
				if (board.getCase(x + 1, y).getWeight().equals(Weight.VISITED)) {
					for (int j = 0; j < balls; j++) {
						possibleChoices.add(new PossibleChoice(result, direction));
					}
					toExplore = true;
				}
				break;
		}
		return toExplore;
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void keyPressed(KeyEvent e) {
	}

	@Override
	public void keyReleased(KeyEvent e) {
		if (mode.equals(Mode.MANUAL)) {
			Direction direction = null;
			switch (e.getKeyCode()) {
				case 37:
					direction = LEFT;
					break;
				case 39:
					direction = RIGHT;
					break;
				case 38:
					direction = UP;
					break;
				case 40:
					direction = DOWN;
					break;
			}
			if (direction != null) {
				// Cases autour de l'agent
				HashMap<Direction, Case> casesAround = board.getAgent().getCasesAround();

				// Ajout de l'entry dans l'arbre
				HashMap<String, String> entry = new HashMap<>();
				for (Direction dir : Direction.values()) {
					if (!casesAround.get(dir).getWeight().equals(Weight.WALL)) {
						entry.put(dir.name(), casesAround.get(dir).getWeight().name());
					}
				}
				entry.put(DIRECTION, direction.name());

				// Déplacement
				rounds += processDirection(direction);

				// Mise à jour de l'affichage
				refresh();

				// Mise à jour de l'état du jeu
				updateGameState(entry);

			}
			e.consume();
		}
	}

	private void updateGameState(HashMap<String, String> entry) {
		// Vérifie l'état du jeu
		System.out.println("Tour " + rounds);

		int result;
		if (board.getAgent().isAlive()) {
			result = 0;
		} else {
			result = 1;
		}
		decisionTree.addData(entry, result);
//		decisionTree.print();
		decisionTree.save();

		if (!board.getAgent().isAlive()) {
			death++;
			System.out.println("L'agent est décédé...");
			reset();
		} else if (board.getAgent().hasGold()) {
			win++;
			System.out.println("L'agent a récupéré l'or!!");
			reset();
		}
	}

	private void displayResult() {
		System.out.println("Nombre de morts: " + death);
		System.out.println("Nombre de victoires: " + win);
		System.out.println("Nombre de parties: " + (win + death));
		float winRate = (float) win / (float) (win + death) * 100.0f;
		System.out.println("Pourcentage de victoire: " + winRate + "%");
	}
}
