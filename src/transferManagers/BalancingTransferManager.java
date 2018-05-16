package transferManagers;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import server.ServerFragment;

/**
 * Handles network traffic between adjacent nodes, this is the class that does the bulk of network handling. 
 *
 */
public class BalancingTransferManager extends TransferManager{
	
	LeftTransactionHandler leftTransactionHandler = null;
	RightTransactionHandler rightTransactionHandler = null;
	
	boolean leftConnected = false;
	boolean rightConnected = false;
	
	/** Run this function immediately after finishing a simulation step, it is timing critical. 
	 *  This function accepts the current board and depending on which node finished first, either requests a slice of the neighbor's board or gives one up.
	 *  This is how it balances the load over the cluster, if a node takes a long time to finish it gets smaller.  
	 * @param currentBoard The node's board after it finishes simulation
	 * @return The new board after the transfer manager changes it.
	 * @throws IOException
	 */
	@Override
	public byte[][] doneNow(byte[][] currentBoard, long simulationTime) throws IOException{
		if(!(leftConnected || rightConnected)) {
			throw new IllegalStateException("Must initialize either left or right side");
		}
		
		int newBoardWidth = currentBoard.length;
		int newBoardHeight = currentBoard[0].length;
		if(leftConnected) {
			leftTransactionHandler.startTransaction(currentBoard, simulationTime);
		}
		if(rightConnected) {
			rightTransactionHandler.startTransaction(currentBoard, simulationTime);
		}
		
		byte[][] newLeftData = null;
		byte[][] newRightData = null;
		boolean newBytesFromLeft = false;
		boolean newBytesFromRight = false;
		if(leftConnected) {
			leftTransactionHandler.waitForTransaction();
			newBytesFromLeft = leftTransactionHandler.hasNewBytes();
			if(newBytesFromLeft) {
				newBoardWidth++;
				newLeftData = leftTransactionHandler.getBytes();
			}else {
				newBoardWidth--;
			}
		}
		if(rightConnected) {
			rightTransactionHandler.waitForTransaction();
			newBytesFromRight = rightTransactionHandler.hasNewBytes();
			if(newBytesFromRight) {
				newBoardWidth++;
				newRightData = rightTransactionHandler.getBytes();
			}else {
				newBoardWidth--;
			}
		}
		
		
		byte[][] newBoard = new byte[newBoardWidth][newBoardHeight];
		System.arraycopy(	currentBoard,
							leftConnected ? 1 : 0, 
							newBoard, 
							leftConnected ? (newBytesFromLeft ? 2 : 0) : 0, 
							currentBoard.length + (leftConnected ? -1 : 0) + (rightConnected ? -1 : 0)); 
		
		if(leftConnected && newBytesFromLeft){	// Get new slice from left
			System.arraycopy(newLeftData[0], 0, newBoard[0], 0, newBoardHeight);
			System.arraycopy(newLeftData[1], 0, newBoard[1], 0, newBoardHeight);
		}
		
		if(rightConnected && newBytesFromRight){	// Get new slice from right
			System.arraycopy(newRightData[0], 0, newBoard[newBoardWidth - 2], 0, newBoardHeight);
			System.arraycopy(newRightData[1], 0, newBoard[newBoardWidth - 1], 0, newBoardHeight);
		}
		
		shiftAmount = leftConnected ? (newBytesFromLeft ? -1 : 1) : 0; 
		
		return newBoard;
	}
	
	private int shiftAmount = 0;
	/**
	 * @return How much the board shifted left or right as a result of the network transfer.
	 */
	public int getShiftAmount() {
		return shiftAmount;
	}
	
	Socket leftSocket;
	/** Opens a server and waits for the node to the left to connect, does not need an address. 
	 * @param connect True to connect, false to disconnect
	 * @param height Height of the board
	 * @throws IOException
	 */
	@Override
	public void connectLeft(boolean connect, int height) throws IOException{
		if(connect == leftConnected){
			return;
		}
		if(connect){
			ServerSocket server = new ServerSocket(ServerFragment.PORT);
			leftSocket = server.accept();
			server.close();
			leftTransactionHandler = new LeftTransactionHandler(leftSocket, height);
			leftTransactionHandler.start();
			try { Thread.sleep(100); } catch (InterruptedException e) { e.printStackTrace(); }
			leftConnected = true;
		}else{
			leftTransactionHandler.close();
			leftTransactionHandler = null;
			leftSocket.close();
			leftConnected = false;
		}
	}
	Socket rightSocket;
	/** Connects to the given address, assumes the address is to the node to the right
	 * @param address Address to connect to
	 * @param connect True to connect, false to disconnect
	 * @param height Height of the board
	 * @throws IOException
	 */
	@Override
	public void connectRight(byte[] address, boolean connect, int height) throws IOException{
		if(connect == rightConnected){
			return;
		}
		if(connect){
			int tries = 0;
			while(true) {
				
				try {
					InetAddress rightAddress = InetAddress.getByAddress(address);
					rightSocket = new Socket(rightAddress, ServerFragment.PORT);
					break;
				}catch (ConnectException e) {
					if(tries > 5) {
						System.exit(1);
						System.out.println("Still retrying, try opening port " + ServerFragment.PORT + "...");
					}else {
						System.out.println("Connection attempt timed out, retry...");
					}
					tries++;
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) { }
			}
			
			rightTransactionHandler = new RightTransactionHandler(rightSocket, height);
			rightTransactionHandler.start();
			try { Thread.sleep(100); } catch (InterruptedException e) { e.printStackTrace(); }
			rightConnected = true;
		}else{
			rightTransactionHandler.close();
			rightTransactionHandler = null;
			rightSocket.close();
			rightConnected = false;
		}
	}
	

	@Override
	public void close() throws IOException {
		if(rightTransactionHandler != null) {
			rightTransactionHandler.close();	
		}
		if(leftTransactionHandler != null) {
			leftTransactionHandler.close();
		}
	}
}