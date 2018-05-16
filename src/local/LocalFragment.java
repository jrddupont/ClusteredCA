package local;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URISyntaxException;

import server.ServerFragment;
import transferManagers.BalancingTransferManager;
import transferManagers.NonBalancingTransferManager;
import transferManagers.TransferManager;

/**
 * The node side object that sets up and handles some basic network traffic.
 */
public class LocalFragment {
	CAFragment caFragment;
	CARenderer caRenderer;
	MasterCommunication masterCommunication;
	TransferManager transferManager;
	boolean isInitialized = false;
	int nodeNumber = -1;
	
	/** Constructs and starts the local simulation
	 * 
	 * @param masterIP IP as a string of the master node
	 * @throws IOException
	 */
	public LocalFragment(String masterIP) throws IOException{
		System.out.printf("Connecting to master at %s...", masterIP);
		masterCommunication = new MasterCommunication(this, masterIP);
		masterCommunication.start();
		System.out.println("Connected to master.");
	}
	
	int simulationFrameSteps;
	int framesToSimulate;
	/**	Accepts the metadata packet from the master and constructs a transfer manager
	 * @param receiveData The metadata packet
	 * @throws URISyntaxException 
	 */
	private void initialize(byte[] receiveData) throws URISyntaxException{
		synchronized (masterCommunication) {
			
			int width = getInt(receiveData, 0);			
			int height = getInt(receiveData, 4*1);
			int simplexSeed = getInt(receiveData, 4*2);
			int startX = getInt(receiveData, 4*3);
			int startY = getInt(receiveData, 4*4);
			byte[] rightNode = new byte[4];
			System.arraycopy(receiveData, 4*5, rightNode, 0, 4);
			int boardXPosition = getInt(receiveData, 4*6);
			simulationFrameSteps = getInt(receiveData, 4*7);
			framesToSimulate = getInt(receiveData, 4*8);
			boolean hasLeft = receiveData[36] == 1;
			boolean hasRight = receiveData[37] == 1;
			boolean isBalancing = receiveData[38] == 1;
			
			nodeNumber = boardXPosition / width;
			if(isBalancing) {
				transferManager = new BalancingTransferManager();
			}else {
				transferManager = new NonBalancingTransferManager();
			}
			
			if(hasLeft) {
				try {
					transferManager.connectLeft(true, height);
				} catch (IOException e) { e.printStackTrace(); }
			}
			if(hasRight) {
				try {
					transferManager.connectRight(rightNode, true, height);
				} catch (IOException e) { e.printStackTrace(); }
			}
			
			caFragment = new CAFragment(width, height, boardXPosition, simplexSeed);
			caRenderer = new CARenderer(simplexSeed, "node" + nodeNumber);
			if(startX >= 0) {
				caFragment.setCell(startX, startY, CAFragment.MAX_BACTERIA);
			}
			isInitialized = true;
		}
		
	}
	private long totalNetTime = 0;
	private long totalSimTime = 0;
	/** Starts the simulation, must initialize before starting.
	 * @throws IOException
	 */
	private void startSimulation() throws IOException{
		synchronized (masterCommunication) {
			if(!isInitialized) {
				throw new IllegalStateException("Tried to start simulation without initializing.");
			}
			for(int currentFrame = 0; currentFrame < framesToSimulate; currentFrame++) {
				long frameNetTime = 0;
				long frameSimTime = 0;
				for(int currentStep = 0; currentStep < simulationFrameSteps; currentStep++) {
					long netTime = 0;
					long simTime = 0;
					
					long startTime = System.nanoTime();
					caFragment.step();
					simTime = System.nanoTime() - startTime;
					
					if(currentFrame == framesToSimulate - 1 && currentStep == simulationFrameSteps - 1) {
						break;
					}
					
					startTime = System.currentTimeMillis();
					byte[][] newBoard = transferManager.doneNow(caFragment.getBoard()[CAFragment.MUTABLE_BOARD], simTime);
					netTime = System.currentTimeMillis() - startTime;
					
					frameNetTime += netTime;
					frameSimTime += simTime / 1000000;
					
					int shiftLeft = transferManager.getShiftAmount();
					caFragment.updateBoard(newBoard, shiftLeft);
				}
				totalNetTime += frameNetTime;
				totalSimTime += frameSimTime;
				masterCommunication.sendPacket(ServerFragment.OPC_FRAMEMARK, new byte[] {1});
				caRenderer.renderCurrentFrame(caFragment.getBoard(), caFragment.getXOffset());
				System.out.println(String.format("%d of %d:%d:%d", currentFrame, framesToSimulate, frameNetTime, frameSimTime));
			}
			System.out.println(String.format("Done:%d:%d", totalNetTime, totalSimTime));
			
			masterCommunication.sendPacket(ServerFragment.OPC_DONE, new byte[] {1});
			masterCommunication.close();
			transferManager.close();
		}
	}
	
	/** Given a byte array and an offset, convert the next 4 bytes into an int.
	 * @param bytes The byte array
	 * @param off The offset
	 * @return
	 */
	public static int getInt(byte[] bytes, int off) {
		return ((bytes[off + 0] & 0xFF) << 24) | ((bytes[off + 1] & 0xFF) << 16) | ((bytes[off + 2] & 0xFF) << 8) | (bytes[off + 3] & 0xFF);
	}
	
	/**
	 * This class handles the communication between the local fragment and the master in its own thread. 
	 */
	public static class MasterCommunication extends Thread{
		
		private byte[] messageBuffer;
		DataInputStream reader;
		DataOutputStream writer;
		Socket masterSocket;
		LocalFragment fragment;
		/** Sets up and connects to the master.
		 * @param fragment The local fragment so we can run functions on it. 
		 * @param masterIP IP of the master node as a string.
		 * @throws IOException
		 */
		public MasterCommunication(LocalFragment fragment, String masterIP) throws IOException {
			InetAddress masterAddress = InetAddress.getByName(masterIP);
			int tries = 0;
			while(true) {
				try {
					masterSocket = new Socket(masterAddress, ServerFragment.PORT);
					break;
				}catch (ConnectException e) {
					if(tries > 5) {
						System.exit(1);
						System.out.println("	Still retrying, try opening port " + ServerFragment.PORT + "...");
					}else {
						System.out.println("	Connection attempt timed out, retry...");
					}
					tries++;
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) { }
			}
			
			this.reader = new DataInputStream(masterSocket.getInputStream());
			this.writer =  new DataOutputStream(masterSocket.getOutputStream());
			this.fragment = fragment;
			messageBuffer = new byte[ServerFragment.PACKET_SIZE];
		}
		
		@Override
		public void run() {
			while(!stop) {
				int messageSize = 0;
				int opCode = 0;
				try { 
					messageSize = reader.readInt();
					if(messageSize == 0){
						continue;
					}
					opCode = reader.readInt();
					reader.readFully(messageBuffer, 0, messageSize); 
				}catch (SocketException e){
					try {
						close();
					} catch (IOException e1) {
						e1.printStackTrace();
						System.exit(1);
					}
					break;
				}catch (EOFException e){
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
				System.out.println("#MESSAGE " + opCode);
				if(opCode == ServerFragment.OPC_METADATAPACKET) {
					try {
						fragment.initialize(messageBuffer);
					} catch (URISyntaxException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else if(opCode == ServerFragment.OPC_STARTSIMULATION) {
					try {
						fragment.startSimulation();
					} catch (IOException e) { e.printStackTrace(); }
				}
				
			}
		}
		
		/**	Send a packet to the master node, usually just opcodes
		 * @param opCode What the message will contain, or typically the message itself
		 * @param Packet either the bytes to send or null
		 */
		public void sendPacket(int opCode, byte[] packet) {
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
		
		private boolean stop = false;
		/** Closes all open ports and cleans up the object.
		 * @throws IOException
		 */
		public void close() throws IOException{
			if(reader != null) {
				reader.close();
			}
			if(writer != null) {
				writer.close();
			}
			if(masterSocket != null) {
				masterSocket.close();
			}
			stop = true;
		}
	}
}
