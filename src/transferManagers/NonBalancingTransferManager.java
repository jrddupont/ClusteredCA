package transferManagers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import server.ServerFragment;

/**
 * Handles network traffic between adjacent nodes, this is the class that does the bulk of network handling. 
 *
 */
public class NonBalancingTransferManager extends TransferManager{
	
	TransactionHandler leftTransactionHandler = null;
	TransactionHandler rightTransactionHandler = null;
	
	boolean leftConnected = false;
	boolean rightConnected = false;
	
	/** Just transfers the border evenly between two nodes 
	 * @param currentBoard The node's board after it finishes simulation
	 * @return The new board after the transfer manager changes it.
	 * @throws IOException
	 */
	@Override
	public byte[][] doneNow(byte[][] currentBoard, long simulationTime) throws IOException{
		if(!(leftConnected || rightConnected)) {
			throw new IllegalStateException("Must initialize either left or right side");
		}
		if(leftConnected) {
			leftTransactionHandler.sendData(currentBoard[1]);
		}
		if(rightConnected) {
			rightTransactionHandler.sendData(currentBoard[currentBoard.length - 2]);
		}
		
		if(leftConnected) {
			currentBoard[0] = leftTransactionHandler.getData();
		}
		if(rightConnected) {
			currentBoard[currentBoard.length - 1] = rightTransactionHandler.getData();
		}
		
		return currentBoard;
	}

	
	/**
	 * @return How much the board shifted left or right as a result of the network transfer.
	 */
	public int getShiftAmount() {
		return 0;
	}
	
	Socket leftSocket;
	/** Opens a server and waits for the node to the left to connect, does not need an address. 
	 * @param connect True to connect, false to disconnect
	 * @param height Height of the board
	 * @throws IOException
	 */
	public void connectLeft(boolean connect, int height) throws IOException{
		if(connect == leftConnected){
			return;
		}
		if(connect){
			ServerSocket server = new ServerSocket(ServerFragment.PORT);
			leftSocket = server.accept();
			server.close();
			leftTransactionHandler = new TransactionHandler(leftSocket, height);
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
			
			rightTransactionHandler = new TransactionHandler(rightSocket, height);
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
	
	/**
	 * This thread object handles the exchange of slices left and right. It has a built in state machine to keep track of the state of the network exchange.
	 * It will throw errors if it gets packets out of order.
	 *
	 */
	public static class TransactionHandler extends Thread{

		private byte[] messageBuffer;
		boolean stop = false;
		
		DataInputStream reader;
		DataOutputStream writer;
		
		
		/**	Constructs the streams and sets up the state machine and buffers
		 * @param socket Socket to construct the read and write buffers on
		 * @param height Height of the board, important to construct buffer size. 
		 * @param isLeft If it is the left node, important to know which side to give priority in the event of a race condition as well as which part of the board to send.
		 * @throws IOException
		 */
		public TransactionHandler(Socket socket, int height) throws IOException{
			messageBuffer = new byte[height];
			this.reader = new DataInputStream(socket.getInputStream());
			this.writer =  new DataOutputStream(socket.getOutputStream());
		}
		@Override
		public void run() {
			while(!stop){
				int messageSize = 0;
				try { 
					messageSize = reader.readInt();
					if(messageSize == 0){
						continue;
					}
					reader.readFully(messageBuffer, 0, messageSize); 
				}catch (SocketException | EOFException e){
					try {
						close();
					} catch (IOException e1) {
						e1.printStackTrace();
						System.exit(1);
					}
					break;
				} catch (IOException e) {
					e.printStackTrace();
				}
				synchronized (this) {
					nowDone = true;
					this.notifyAll();
				}
			}
		}
		/** Specifically for sending slices of the board
		 * @param data
		 * @throws IOException
		 */
		private void sendData(byte[] data) throws IOException {
			writer.writeInt(data.length);
			writer.write(data, 0, data.length);
			writer.flush();
		}

		boolean nowDone = false;
		/**
		 * Function that simply blocks until the network transaction is done.
		 */
		public void waitForTransaction() {	// Blocks until transaction is done
			synchronized (this) {
				while(!nowDone) {
					try { wait(); } catch (InterruptedException e) { }
				}
			}
			nowDone = false;
		}

		/** Gets the slices that were resultant from the transaction
		 * @return Two slices
		 */
		public byte[] getData(){
			waitForTransaction();
			byte[] returnBytes = new byte[messageBuffer.length];
			System.arraycopy(messageBuffer, 0, returnBytes, 0, messageBuffer.length);
			return returnBytes;
		}
		/** Closes and cleans up the object.
		 * @throws IOException
		 */
		public void close() throws IOException{
			reader.close();
			writer.close();
			stop = true;
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