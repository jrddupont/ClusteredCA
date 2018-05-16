package local;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import library.OpenSimplexNoise;

/**
 * Actually does the cellular automata simulation
 */
public class CAFragment {
	
	public static final int SPLIT_SIZE = 100;
	public static final int SPLIT_RATE = 30;	// No more than (splitsize/8) probably
	public static final int HUNGER_RATE = 10;
	public static final double NEIGHBOR_HUNGER_FACTOR = 1.7;
	public static final double EAT_RATE_MULTIPLIER = 0.1;
	
	static final byte MAX_BACTERIA = 127;	// Full health / food bacteria
	static final byte NEW_BACTERIA = -127;	// Bacteria with "1" or minimum health
	static final byte NO_BACTERIA = -128;	// Empty space, no bacteria
	
	static final byte MUTABLE_BOARD = 0;
	static final byte READ_BOARD = 1;
	static final double SIMPLEX_MULTIPLIER = 0.01;
	
	private int width;
	private int height;
	private int boardXPosition;
	
	
	private byte board[][][];
	private OpenSimplexNoise simplex;
	
	/**	Sets up one node's worth of CA
	 * @param width How wide to start
	 * @param height How tall is the simulation
	 * @param boardXPosition Where on the full board this fragment lies
	 * @param simplexSeed Seed of the noise function. 
	 */
	public CAFragment(int width, int height, int boardXPosition, int simplexSeed){
		this.width = width;
		this.height = height;
		this.boardXPosition = boardXPosition;
		
		simplex = new OpenSimplexNoise(simplexSeed);
		board = new byte[2][width][height];
		for(int x = 0; x < width; x++){
			for(int y = 0; y < height; y++){
				board[MUTABLE_BOARD][x][y] = NO_BACTERIA;
			}
		}
	}
	
	/**
	 * Do one step of the simulation, all cells update simultaneously. 
	 */
	public void step(){
		for(int i = 0; i < width; i++){
		    System.arraycopy(board[MUTABLE_BOARD][i], 0, board[READ_BOARD][i], 0, height);
		}
		for(int x = 1; x < width-1; x++){
			for(int y = 1; y < height-1; y++){
				board[MUTABLE_BOARD][x][y] = getUpdatedCellValue(x, y);
			}
		}
	}
	
	/** Gets the new value of the given cell based on the previous state of the board.
	 * @param x X coordinate.
	 * @param y Y coordinate.
	 * @return
	 */
	private byte getUpdatedCellValue(int x, int y){
		byte newCellValue = board[READ_BOARD][x][y];
		if(newCellValue == NO_BACTERIA){	// If it is empty
			boolean isNowBacteria = false;
			for(int dX = -1; dX <= 1; dX++){	// Loop to create new bacteria 
				for(int dY = -1; dY <= 1; dY++){
					if(dY == 0 && dX == 0 ){
						continue;
					}
					if(board[READ_BOARD][x+dX][y+dY] > SPLIT_SIZE){
						if(!isNowBacteria){
							isNowBacteria = true;
							newCellValue = -127;
						}
						newCellValue += SPLIT_RATE;
					}
				}
			}
			return newCellValue;
		}else{	// If it is a bacteria cell
			doUselessWork((byte) 50);
			int foodSource = (int) (EAT_RATE_MULTIPLIER * 255 * getFoodAt(x, y, false));
			if((foodSource + (int) newCellValue) < MAX_BACTERIA){
				newCellValue += foodSource;
			}else{
				newCellValue = MAX_BACTERIA;
			}
			if(newCellValue > SPLIT_SIZE){
				for(int dX = -1; dX <= 1; dX++){	// Loop to create new bacteria 
					for(int dY = -1; dY <= 1; dY++){
						if(dY == 0 && dX == 0){
							continue;
						}
						if(board[READ_BOARD][x+dX][y+dY] == 0){
							newCellValue -= SPLIT_RATE;
						}
					}
				}
			}
			double totalHunger = HUNGER_RATE;
			for(int dX = -1; dX <= 1; dX++){	// Loop to evaluate crowding 
				for(int dY = -1; dY <= 1; dY++){
					if((dY == 0 && dX == 0)){
						continue;
					}
					if(board[READ_BOARD][x+dX][y+dY] != NO_BACTERIA){
						totalHunger += NEIGHBOR_HUNGER_FACTOR; 
					}
				}
			}
			if(((int) newCellValue - totalHunger) > -128){
				newCellValue -= totalHunger;
				return newCellValue;
			}else{
				return NO_BACTERIA;
			}
		}
	}
	
	Random rand = new Random();
	/** Basically, no-op for a while to simulate actually doing some hard work
	 * @param difficulty how hard the sha is to find, bigger is harder
	 */
	public void doUselessWork(byte difficulty) {
		int currentTarget = rand.nextInt();
		int inc = 0;
		while(true){
			byte[] possibleHash = getHash(currentTarget, inc);
			if(possibleHash[0] >= difficulty) {
				return;
			}
			inc++;
		}
	}
	public byte[] getHash(int randNumber, int offset){
		MessageDigest digest = null;;
		try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { e.printStackTrace(); }
		return digest.digest( Bytes.concat(Ints.toByteArray(randNumber), Ints.toByteArray(offset)) );
	}
	
	
	/** Gets how much "food" is at a given position depending on the simplex noise function
	 * @param x X coordinate
	 * @param y Y coordinate
	 * @param sparseFood Switch to control if there is a lot of food or not.
	 * @return
	 */
	public double getFoodAt(int x, int y, boolean sparseFood){
		return 0.8;
//		int offsetX = x + boardXPosition;
//		double noise = simplex.eval(offsetX * CAFragment.SIMPLEX_MULTIPLIER, y * CAFragment.SIMPLEX_MULTIPLIER);
//		if(sparseFood){
//			return (1+((1/1.5) * Math.atan((noise*Math.PI*2) - 0)))/2; 	// Less food
//		}else{
//			return (1+((1/1.5) * Math.atan((noise*Math.PI*2) + 1)))/2;	// More food
//		}
		//return (noise + 1)/2;	// Too gray
	}

	
	/**
	 * @return Where on the full board this fragment lies.
	 */
	public int getXOffset() {
		return boardXPosition;
	}
	
	/** Set a given position to a cell value, used for initial placement. 
	 * @param x X coordinate
	 * @param y Y coordinate
	 * @param value What to set it to, check the constants. 
	 */
	public void setCell(int x, int y, byte value) {
		board[MUTABLE_BOARD][x][y] = value;
	}
	
	public byte[][][] getBoard(){
		return board;
	}
	
	/** Setter for the board, copies data into a new object
	 * @param newBoard Board to copy in
	 * @param shiftAmount How much to adjust the board's offset.
	 */
	public void updateBoard(byte[][] newBoard, int shiftAmount) {
		board = new byte[2][newBoard.length][newBoard[0].length];
	    System.arraycopy(newBoard, 0, board[MUTABLE_BOARD], 0, newBoard.length);
		width = newBoard.length;
		boardXPosition += shiftAmount;
		board[MUTABLE_BOARD] = newBoard;
	}
}
