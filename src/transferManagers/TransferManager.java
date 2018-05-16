package transferManagers;

import java.io.IOException;

public abstract class TransferManager {
	public byte[][] doneNow(byte[][] currentBoard, long simulationTime) throws IOException{
		return null;
	}
	public void connectLeft(boolean connect, int height) throws IOException{
		
	}
	public void connectRight(byte[] address, boolean connect, int height) throws IOException{
		
	}
	public int getShiftAmount() {
		return 0;
	}
	public void close() throws IOException {
		
	}
}
