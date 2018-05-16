package local;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

public class Test {
	public static void main(String[] args) {
		new Test();
	}
	
	// 53000ms for max diff
	//  1957ms for 80% diff
	//  1050ms for 75% diff
	//   760ms for half diff
	//   364ms for min diff
	public Test() {
		System.out.println("start");
		Long currentTime = System.nanoTime();
		for(int i = 0; i < (500*500); i++) {
			doUselessWork((byte)96);
		}
		System.out.println("Stop " + (System.nanoTime() - currentTime) / 1000000);
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
}
