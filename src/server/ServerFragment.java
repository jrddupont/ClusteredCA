package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import local.CARenderer;

/**
 * The server side object that sets up and handles some basic network traffic.
 */
public class ServerFragment {
	public static final int PORT = 9876;
	
	public static final int PACKET_SIZE = (9 * 4) + 3; // 9 int * 4 bytes (36) + 3 bytes = 39 
	public static final int OPC_METADATAPACKET = 50;	// This is a metadata packet
	public static final int OPC_STARTSIMULATION = 51;	// Start the simulation
	public static final int OPC_FRAMEMARK = 53;	// Simulation reached frame
	public static final int OPC_DONE = 54;	// Simulation is done
	
	NodeCommunication nodeCommunication;
	public ServerFragment(Socket socket) throws IOException{
		nodeCommunication = new NodeCommunication(socket);
		nodeCommunication.start();
	}
	
	/**
	 * @param width	Width of this fragment
	 * @param height Height of this fragment
	 * @param simplexSeed Seed of the simplex noise
	 * @param startX Where to place a bacteria, -1 to not
	 * @param startY Where to place a bacteria, -1 to not
	 * @param rightNode Address of the node to the right of this one
	 * @param boardXPosition Starting x offset of this board
	 * @param simulationFrameSteps How many steps to simulate per frame
	 * @param framesToSimulate How many frames to simulate total
	 * @param hasLeft Does it have a left node?
	 * @param hasRight Does it have a right node?
	 * @throws IOException
	 */
	public void initialize(int width, int height, int simplexSeed, int startX, int startY, byte[] rightNode, int boardXPosition, int simulationFrameSteps, int framesToSimulate, boolean hasLeft, boolean hasRight, boolean isBalancing) throws IOException{
		byte[] packet = Bytes.concat(
				Ints.toByteArray(width),
				Ints.toByteArray(height),
				Ints.toByteArray(simplexSeed),
				Ints.toByteArray(startX),
				Ints.toByteArray(startY),
				rightNode,
				Ints.toByteArray(boardXPosition),
				Ints.toByteArray(simulationFrameSteps),
				Ints.toByteArray(framesToSimulate),
				new byte[] {(byte) (hasLeft ? 1 : 0)},
				new byte[] {(byte) (hasRight ? 1 : 0)},
				new byte[] {(byte) (isBalancing ? 1 : 0)}
				);
		if(packet.length != PACKET_SIZE) {
			throw new IllegalStateException("Packet is the wrong size, " + packet.length);
		}
		nodeCommunication.sendPacket(OPC_METADATAPACKET, packet);
	}
	
	public void startSimulation() {
		nodeCommunication.sendPacket(OPC_STARTSIMULATION, new byte[]{1});
	}
	
	public static class NodeCommunication extends Thread{
		public static final int OPC_METADATAPACKET = 50;
		public static final int OPC_STARTSIMULATION = 51;
		
		private byte[] messageBuffer;
		DataInputStream reader;
		DataOutputStream writer;
		Socket nodeSocket;
		public NodeCommunication(Socket communicationSocket) throws IOException {
			nodeSocket = communicationSocket;
			this.reader = new DataInputStream(nodeSocket.getInputStream());
			this.writer =  new DataOutputStream(nodeSocket.getOutputStream());
			messageBuffer = new byte[PACKET_SIZE];
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
					break;
				} catch (EOFException e){
					try {
						close();
						break;
					} catch (IOException e1) {
						System.exit(1);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				if(opCode == OPC_FRAMEMARK) {
					System.out.println("Frame mark from node " + getAddress()[3]);
				}else if(opCode == OPC_DONE) {
					System.out.println("Done from node " + getAddress()[3]);
					try {
						close();
						break;
					} catch (IOException e) {
						System.exit(1);
					}
				} 
				
			}
		}
		
		private boolean stop = false;
		public void close() throws IOException{
			reader.close();
			writer.close();
			nodeSocket.close();
			stop = true;
		}
		
		private void sendPacket(int opCode, byte[] packet) {
			try {
				writer.writeInt(packet.length);
				writer.writeInt(opCode);
				writer.write(packet, 0, packet.length);
				writer.flush();
			} catch (IOException e) { e.printStackTrace(); }
		}
		
		public byte[] getAddress() {
			return nodeSocket.getInetAddress().getAddress();
		}
	}

	public byte[] getAddress() {
		return nodeCommunication.getAddress();
	}
}
