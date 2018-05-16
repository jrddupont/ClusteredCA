package server;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import local.CARenderer;
import local.LocalFragment;

public class ClusterOverseer {
	private ArrayList<ServerFragment> fragments;
	
	@SuppressWarnings("unused")
	public static void main(String[] args) throws IOException{
		if(args.length == 2 && args[0].equals("-c")) {
			
			LocalFragment lf = new LocalFragment(args[1]);
			
		}else if(args.length == 8 && args[0].equals("-s")) {
			
			//java -jar client.jar -s 500 500 3 1 50 500
			int width = Integer.parseInt(args[1]);
			int height = Integer.parseInt(args[2]);
			int nodes = Integer.parseInt(args[3]);
			int seed = Integer.parseInt(args[4]);
			int stepsPerFrame = Integer.parseInt(args[5]);
			int framesToSimulate = Integer.parseInt(args[6]);
			boolean isBalancing = args[7].contains("t");
			ClusterOverseer co = new ClusterOverseer(width, height, nodes, seed, stepsPerFrame, framesToSimulate, isBalancing);
			
		}else if(args.length == 5 && args[0].equals("-u")) {
			
			//java -jar client.jar -u 500 500 3 500
			int width = Integer.parseInt(args[1]);
			int height = Integer.parseInt(args[2]);
			int nodes = Integer.parseInt(args[3]);
			int frames = Integer.parseInt(args[4]);
			CARenderer.combineAllImages(width, height, nodes);
			
		}else if(args.length == 1 && args[0].equals("-r")) {
			
			CARenderer.renderVideo();
			
		}else if(args.length == 1 && args[0].equals("-t")) {
			File path = null;
			try {
				path = new File(File.separator + FilenameUtils.getPath(CARenderer.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()) + File.separator + "output");
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println(path);
			System.out.println(path.getPath());
			System.out.println(path.getAbsolutePath());
			
			path.mkdirs();
			
		}else {
			printHelp();
		}
	}
	
	private static void printHelp() {
		System.out.println();
		System.out.println("Server Use:");
		System.out.println("The server will wait for `nodes` number of computers to connect, then it will start simulation");
		System.out.println("	java -jar ca.jar -s width height nodes seed stepsPerFrame framesToSimulate, isBalancing");
		System.out.println("		width: How wide in pixels each node starts as.");
		System.out.println("		height: How tall in pixels the entire simulation is.");
		System.out.println("		nodes: How many nodes that the server will wait to connect before starting.");
		System.out.println("		seed: Seed of the noise function.");
		System.out.println("		stepsPerFrame: How many simulation steps to do before rendering.");
		System.out.println("		framesToSimulate: How many frames to render.");
		System.out.println("		framesToSimulate: Should the cluster try to load balance.");
		System.out.println("Example: java -jar ca.jar -s 500 500 3 1 50 500 false");
		System.out.println();
		
		System.out.println("Client Use:");
		System.out.println("	java -jar ca.jar -c masterIP");
		System.out.println("Example: java -jar ca.jar -c 192.168.0.1");
		System.out.println();
	}

	/** Constructs and starts the server side of the CA simulation. 
	 * @param width How wide in pixels each node starts as.
	 * @param height How tall in pixels the entire simulation is.
	 * @param nodes How tall in pixels the entire simulation is.
	 * @param simplexSeed Seed of the noise function.
	 * @param stepsPerFrame How many simulation steps to do before rendering.
	 * @param framesToSimulate How many frames to render.
	 * @throws IOException
	 */
	public ClusterOverseer(int width, int height, int nodes, int simplexSeed, int stepsPerFrame, int framesToSimulate, boolean isBalancing) throws IOException{
		ServerSocket server = new ServerSocket(ServerFragment.PORT);
		fragments = new ArrayList<ServerFragment>(nodes);
		System.out.println("Now accepting connections: ");
		for(int i = 0; i < nodes; i++) {
			Socket newNode = server.accept();
			ServerFragment newFragment = new ServerFragment(newNode);
			fragments.add(newFragment);
			System.out.println(newNode.getInetAddress().getHostAddress() + ": Connected");
		}
		server.close();
		System.out.println("All nodes connected, initializing...");
		for(int i = 0; i < fragments.size(); i++) {
			ServerFragment currentFragment = fragments.get(i);
			int startX = i == 0 ? 250 : -1;
			int startY = 100;
			byte[] rightNode = new byte[4];
			boolean hasRight = i < fragments.size() - 1;
			boolean hasLeft = i != 0;
			if(hasRight) {
				rightNode = fragments.get(i + 1).getAddress();  
			}
			currentFragment.initialize(
					width, 
					height, 
					simplexSeed,
					startX,
					startY,
					rightNode, 
					i * width,
					stepsPerFrame,
					framesToSimulate,
					hasLeft,
					hasRight,
					isBalancing
					);
		}
		System.out.println("All nodes initialized, starting simulation...");
		for(ServerFragment fragment : fragments) {
			fragment.startSimulation();
		}
	}
}
