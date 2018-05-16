package transferManagers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

/**
 * This thread object handles the exchange of slices left and right. It has a built in state machine to keep track of the state of the network exchange.
 * It will throw errors if it gets packets out of order.
 *
 */
public class RightTransactionHandler extends Thread{

	public static final int STATE_IDLE = 0;
	public static final int STATE_WAITFORTIME = 1;
	public static final int STATE_WAITFORDATA = 2;
	public static final int STATE_WAITFORACK = 3;
	
	private byte[] messageBuffer;
	private byte[] data;
	private boolean stop = false;
	
	private DataInputStream reader;
	private DataOutputStream writer;
	
	private int state;
	private boolean hasTime = false;
	private long rightNodeTime = 0;
	private long leftNodeTime = 0;
	
	byte[][] currentBoard;
	/**	Constructs the streams and sets up the state machine and buffers
	 * @param socket Socket to construct the read and write buffers on
	 * @param height Height of the board, important to construct buffer size. 
	 * @throws IOException
	 */
	public RightTransactionHandler(Socket socket, int height) throws IOException{
		messageBuffer = new byte[height*2];
		data = new byte[height*2];
		this.reader = new DataInputStream(socket.getInputStream());
		this.writer =  new DataOutputStream(socket.getOutputStream());
		state = STATE_IDLE;
	}
	@Override
	public void run() {
		while(!stop){
			int messageSize = 0;
			int opCode = 0;
			try { 
				messageSize = reader.readInt();
				if(messageSize == 0){
					continue;
				}
				opCode = reader.readInt();
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
				switch(state) {
					case STATE_IDLE:
						switch (opCode) {
							case LeftTransactionHandler.OPC_TIME:
								hasTime = true;
								rightNodeTime = Longs.fromByteArray(messageBuffer);
								break;
							default:
								throw new IllegalStateException("Idle " + opCode);
						}
						
						break;
					case STATE_WAITFORTIME:
						switch (opCode) {
							case LeftTransactionHandler.OPC_TIME:
								rightNodeTime = Longs.fromByteArray(messageBuffer);
								handleTime();
								break;
							default:
								throw new IllegalStateException("Wait for time " + opCode);
						}
						break;
					case STATE_WAITFORDATA:
						switch (opCode) {
							case LeftTransactionHandler.OPC_DATA:
								System.arraycopy(messageBuffer, 0, data, 0, messageBuffer.length);
								nowDone = true;
								gotData = true;
								sendPacket(LeftTransactionHandler.OPC_ACK, null);
								state = STATE_IDLE;
								this.notifyAll();
								break;
							default:
								throw new IllegalStateException("Wait for data " + opCode);
						}
						break;
					case STATE_WAITFORACK:
						switch (opCode) {
							case LeftTransactionHandler.OPC_ACK:
								nowDone = true;
								gotData = false;
								state = STATE_IDLE;
								this.notifyAll();
								break;
							default:
								throw new IllegalStateException("Wait for ack " + opCode);
						}
						break;
					default:
						throw new IllegalStateException("How did this happen? " + state + " " + opCode);
				}
			}
		}
	}

	/** Based on the state of this object, either send slices of my board or accept slices
	 * @param currentBoard Board to act on
	 * @throws IOException
	 */
	public void startTransaction(byte[][] currentBoard, long simulationTime) throws IOException {
		synchronized (this) {
			this.currentBoard = currentBoard;
			nowDone = false;
			gotData = false;
			leftNodeTime = simulationTime;
			if(hasTime) {
				handleTime();
			}else {
				state = STATE_WAITFORTIME;
			}
		}
	}
	
	private void handleTime() {
		synchronized (this) {
			hasTime = false;
			if(leftNodeTime > rightNodeTime) {	// Right done first
				sendPacket(LeftTransactionHandler.OPC_DATA, Bytes.concat(currentBoard[currentBoard.length - 2], currentBoard[currentBoard.length - 1]));
				state = STATE_WAITFORACK;
			}else {	// Left done first
				sendPacket(LeftTransactionHandler.OPC_REQ, null);
				state = STATE_WAITFORDATA;
			}
		}
	}
	
	/**	
	 * @param opCode What the message will contain, or typically the message itself
	 * @param Packet either the bytes to send or null
	 */
	private void sendPacket(int opCode, byte[] packet) {
		synchronized (this) {
			byte[] sendPacket = packet;
			if(packet == null || sendPacket.length < 1) {
				sendPacket = new byte[]{1}; 
			}
			try {
				writer.writeInt(sendPacket.length);
				writer.writeInt(opCode);
				writer.write(sendPacket, 0, sendPacket.length);
				writer.flush();
			} catch (SocketException e) {
				System.out.println("Master disconnected, stopping...");
				try {
					close();
				} catch (IOException e1) { e1.printStackTrace(); }
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private boolean nowDone = false;
	private boolean gotData = false;
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
	
	/**
	 * @return If the transaction resulted in new slice or not
	 */
	public boolean hasNewBytes() {
		return gotData;
	}
	
	/** Gets the slices that were resultant from the transaction
	 * @return Two slices
	 */
	public byte[][] getBytes(){
		byte[][] returnBytes = new byte[2][data.length/2];
		
		System.arraycopy(
				data,
				0,
				returnBytes[0],
				0,
				returnBytes[0].length);
		System.arraycopy(
				data,
				returnBytes[1].length,
				returnBytes[1],
				0,
				returnBytes[0].length);
		
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