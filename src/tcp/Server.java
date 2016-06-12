package tcp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

public class Server
{
	private static final int NUMBEROFTHREADS = 50;

	private int port;
	private int packetSize;
	private int clientTimeout;
	private Logger logger;
	private String rtt, download;
	private ExecutorService executor;

	public Server()
	{
		this.port = 2600;
		this.packetSize = 1400;
		this.clientTimeout = 8000;

		this.executor = Executors.newFixedThreadPool(NUMBEROFTHREADS);

		File myFile = new File("256KB");
		byte[] mybytearray = new byte[(int) myFile.length()];
		FileInputStream fis;
		try
		{
			fis = new FileInputStream(myFile);
			BufferedInputStream bis = new BufferedInputStream(fis);
			bis.read(mybytearray, 0, mybytearray.length);
			this.rtt = new String(mybytearray);

			myFile = new File("10MB");
			mybytearray = new byte[(int) myFile.length()];
			fis = new FileInputStream(myFile);
			bis = new BufferedInputStream(fis);
			bis.read(mybytearray, 0, mybytearray.length);
			this.download = new String(mybytearray);

			bis.close();
		} catch (FileNotFoundException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void initLogger() throws IOException
	{
		// initialize Logger
		this.logger = Logger.getRootLogger();

		SimpleLayout layout = new SimpleLayout();
		FileAppender fileAppender = new FileAppender(layout,
				"logs/MeineLogDatei.log", false);
		logger.addAppender(fileAppender);
		// ALL | DEBUG | INFO | WARN | ERROR | FATAL | OFF:
		logger.setLevel(Level.ALL);
	}

	public void startServer()
	{
		ServerSocket sSocket = null;
		try
		{
			sSocket = new ServerSocket(port);
			logger.info("General: Server started.");
			while (true)
			{
				System.out.println("Warte auf Verbindungen...");

				Socket client = sSocket.accept();

				client.setTcpNoDelay(true);
				client.setTrafficClass(8);
				// Set Timeout if Client is not able to response
				client.setSoTimeout(clientTimeout);

				ConnectionThread connection = new ConnectionThread(client,
						logger, packetSize, rtt, download);

				executor.execute(connection);
			}
		} catch (SocketException e)
		{
			logger.error("Server Socket interrupted. Error Message: "
					+ e.getMessage());
			System.out.println("SocketException: Problem mit Server Socket.");
		} catch (IOException e)
		{
			logger.error("IO Error. " + e.getMessage());
		} finally
		{
			executor.shutdown();
			try
			{
				logger.error("Server shutdown.");
				sSocket.close();
			} catch (IOException e)
			{

			}
		}
	}

	public static void main(String[] args)
	{
		Server server = new Server();
		try
		{
			server.initLogger();
		} catch (IOException e)
		{
			System.err.println("Logger konnte nicht initialisiert werden.");
			System.err.println(e.getMessage());
		}

		System.out.println("Server Starten...");
		server.startServer();
	}

}
