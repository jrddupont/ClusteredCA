package local;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import library.OpenSimplexNoise;


public class CARenderer {
	int imageName = 0;
	File currentDirectory;
	OpenSimplexNoise simplex;
	
	
	public CARenderer(long simplexSeed, String folderName) throws URISyntaxException{
		this.simplex = new OpenSimplexNoise(simplexSeed);
		
		currentDirectory = new File(
				File.separator + 
				FilenameUtils.getPath(CARenderer.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()) + 
				File.separator + 
				"output" + 
				File.separator + 
				folderName);
		System.out.println(currentDirectory.getAbsolutePath());
	    if (! currentDirectory.exists()){
	        currentDirectory.mkdirs();
	    }
	}
	
	public double getFoodAt(int x, int y, boolean sparseFood, int xOffset){
		return 0.8;
//		double noise = simplex.eval((x + xOffset) * CAFragment.SIMPLEX_MULTIPLIER, y * CAFragment.SIMPLEX_MULTIPLIER);
//		if(sparseFood){
//			return (1+((1/1.5) * Math.atan((noise*Math.PI*2) - 0)))/2; 	// Less food
//		}else{
//			return (1+((1/1.5) * Math.atan((noise*Math.PI*2) + 1)))/2;	// More food
//		}
	}
	
	public void renderCurrentFrame(byte[][][] board, int xOffset) throws IOException{
		
		int width = board[CAFragment.MUTABLE_BOARD].length;
		int height = board[CAFragment.MUTABLE_BOARD][0].length;
		BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for(int y = 0; y < height; y++){
			for(int x = 0; x < width; x++){
				if(board[CAFragment.MUTABLE_BOARD][x][y] == CAFragment.NO_BACTERIA ){	// If it is food
					int newColor = (int)(255*getFoodAt(x, y, false, xOffset));
					output.setRGB(x, y, getIntRGB(newColor, newColor, newColor));
				}else{
					int newColor = 128 + (int)board[CAFragment.MUTABLE_BOARD][x][y];
					newColor = newColor > 255 ? 255 : newColor;
					output.setRGB(x, y, getIntRGB(255-newColor, 0, newColor));
				}
				
			}
		}
		try {
		    File outputfile = new File(currentDirectory.getPath() + String.format(File.separator +"%d.png", imageName));
		    imageName++;
		    ImageIO.write(output, "png", outputfile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public static void combineAllImages( int width, int height, int nodes) {
		File parentDirectory = null;
		File outputDirectory = null;
		try {
			parentDirectory = new File(
					File.separator + 
					FilenameUtils.getPath(CARenderer.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()) + 
					File.separator + 
					"output"
					);
			outputDirectory = new File(
					File.separator + 
					FilenameUtils.getPath(CARenderer.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()) + 
					File.separator + 
					"output"
					+ 
					File.separator + 
					"combined"
					);
		} catch (URISyntaxException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		if (! parentDirectory.exists()){
			parentDirectory.mkdirs();
		}
		if (! outputDirectory.exists()){
			outputDirectory.mkdirs();
		}
		System.out.println("Cleaning output...");
		try {
			FileUtils.cleanDirectory(outputDirectory);
		} catch (IOException e1) {
			e1.printStackTrace();
		} 
		System.out.println("Output Cleaned.");
		System.out.println("Combining...");
		int totalWidth = (width * nodes) - (nodes - 1);
	    BufferedImage output = new BufferedImage(totalWidth, height, BufferedImage.TYPE_INT_ARGB);
	    Graphics g = output.getGraphics();
	    g.setColor(Color.GREEN);
	    BufferedImage input = null;
	    int frame = 0;
	    outer:
		while(true) {
			int currentWidth = 0;
			for(int node = 0; node < nodes; node++) {
				try {
					input = ImageIO.read(new File(String.format(parentDirectory + File.separator +"node%d"+ File.separator +"%d.png", node, frame)));
				} catch (IIOException e) {
					break outer;
				}catch (IOException e) {e.printStackTrace();}
				
				g.drawImage(input, currentWidth, 0, null);
			    
				if(currentWidth != 0) {
					g.drawLine(currentWidth, 0, currentWidth, height - 1);
				}
				
				currentWidth += input.getWidth() - 1;
			}
			
		    try {
		    	File outputfile = new File(String.format(outputDirectory + File.separator +"%d.png", frame));
				ImageIO.write(output, "png", outputfile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		    frame++;
	    }
		g.dispose();
		System.out.println("Combined.");
	}
	
	public static void renderVideo() throws IOException{
		String strCmdText = String.format("C:\\\\cmd\\\\ffmpeg\\\\bin\\\\ffmpeg.exe -r 20 -f image2 -i \"%s\\output\\%%d.png\" -vcodec libx264 -crf 1 -pix_fmt yuv420p \"%s\\output.mp4\" -y", System.getProperty("user.dir"), System.getProperty("user.dir"));
		System.out.println(strCmdText);
		Runtime.getRuntime().exec(strCmdText);
	}
	
//	public void renderVideo() throws IOException{
//		String strCmdText = String.format("C:\\cmd\\ffmpeg\\bin\\ffmpeg.exe -r 20 -f image2 -i \"%s\\output\\%%d.png\" -vcodec libx264 -crf 1 -pix_fmt yuv420p \"%s\\output.mp4\" -y", System.getProperty("user.dir"), System.getProperty("user.dir"));
//		System.out.println(strCmdText);
//		Runtime.getRuntime().exec(strCmdText);
//	}
	
	private int getIntRGB(int r, int g, int b){
		int rgb = r;
		rgb = (rgb << 8) + g;
		rgb = (rgb << 8) + b;
		return rgb;
	}
}
