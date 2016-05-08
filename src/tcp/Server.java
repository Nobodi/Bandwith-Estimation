package tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

public class Server
{
	private int port;
	private int packetSize;
	private int clientTimeout;
	private Logger logger;

	public Server()
	{
		this.port = 2600;
		this.packetSize = 1400;
		this.clientTimeout = 8000;
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

				// Set Timeout if Client is not able to response
				client.setSoTimeout(clientTimeout);
				client.setReceiveBufferSize(2);
				client.setSendBufferSize(2);

				ConnectionThread connection = new ConnectionThread(client,
						logger, packetSize);
				connection.start();
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
