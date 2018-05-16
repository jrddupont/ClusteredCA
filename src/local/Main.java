package local;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.commons.io.FileUtils;

public class Main {
	public static void main(String[] args) throws IOException, URISyntaxException{
		System.out.println("Cleaning output...");
		FileUtils.cleanDirectory(new File("output")); 
		System.out.println("Output Cleaned.");
		
		int simplexSeed = 1;
		CAFragment ca =		new CAFragment(101, 101, 0, simplexSeed);
		CARenderer car =	new CARenderer(simplexSeed, "Test");
		
		ca.setCell(50, 50, CAFragment.MAX_BACTERIA);
		//ca.setCell(420, 35, CAFragment.MAX_BACTERIA);
		
		int renderFrames = 100;
		int renderSkip = 100;
		car.renderCurrentFrame(ca.getBoard(), 0);
		for(int i = 0; i < renderFrames; i++){
			for(int j = 0; j < renderSkip; j++){
				ca.step();
			}
			car.renderCurrentFrame(ca.getBoard(), 0);
			System.out.printf("%.2f%% done", (i/(double)renderFrames)*100);
			System.out.println();
		}
		//car.renderVideo();
		System.out.println("100% done");
	}
}