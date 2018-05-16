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
public class LeftTransactionHandler extends Thread{

	public static final int STATE_IDLE = 0;
	public static final int STATE_WAITFORDECISION = 1;
	public static final int STATE_WAITFORACK = 2;
	
	public static final int OPC_TIME = 99;
	public static final int OPC_REQ = 100;
	public static final int OPC_DATA = 101;
	public static final int OPC_ACK = 102;
	
	private byte[] messageBuffer;
	private byte[] data;
	private boolean stop = false;
	
	private DataInputStream reader;
	private DataOutputStream writer;
	
	private int state;
	
	byte[][] currentBoard;
	/**	Constructs the streams and sets up the state machine and buffers
	 * @param socket Socket to construct the read and write buffers on
	 * @param height Height of the board, important to construct buffer size. 
	 * @throws IOException
	 */
	public LeftTransactionHandler(Socket socket, int height) throws IOException{
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
						throw new IllegalStateException("Message idle.");
					
					case STATE_WAITFORDECISION:
						switch (opCode) {
							case OPC_REQ:
								sendPacket(OPC_DATA, Bytes.concat(currentBoard[0], currentBoard[1]));
								state = STATE_WAITFORACK;
								break;
							case OPC_DATA:
								System.arraycopy(messageBuffer, 0, data, 0, messageBuffer.length);
								nowDone = true;
								gotData = true;
								sendPacket(OPC_ACK, null);
								this.notifyAll();
								break;
							default:
								throw new IllegalStateException("Wait for decision " + opCode);
						}
						break;
					case STATE_WAITFORACK:
						switch (opCode) {
							case OPC_ACK:
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
			sendPacket(OPC_TIME, Longs.toByteArray(simulationTime));
			state = STATE_WAITFORDECISION;
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
		//System.out.println(hasBytes ? "Got new slice" : "Lost a slice");
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