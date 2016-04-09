package tcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import org.apache.log4j.Logger;

public class ConnectionThread extends Thread
{
	private Socket client;
	private Logger logger;
	private double rate;
	private int factor;
	private int packetSize;

	public ConnectionThread(Socket client, Logger logger, int packetSize)
	{
		this.client = client;
		this.logger = logger;
		this.packetSize = packetSize;
		this.rate = 0;
		this.factor = 0;
	}

	@Override
	public void run()
	{
		String clientIP = client.getInetAddress().getHostAddress();
		System.out.println("Neue Verbindung " + clientIP);

		logger.info("General: New Connection from " + clientIP + ".");

		try
		{
			BufferedReader in = new BufferedReader(new InputStreamReader(
					client.getInputStream()));

			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
					client.getOutputStream()));

			String line = "";

			while ((line = in.readLine()) != null)
			{
				if (line.equals("Spruce"))
				{
					char[] a = new char[packetSize];
					Arrays.fill(a, 'x');
					String message = new String(a);
					out.write(message + "\n");
					out.flush();

					Thread.sleep(200);

					out.write(message + "\n");
					out.flush();
				} else if (line.equals("PacketTrain") || line.contains("Value"))
				{
					if (line.equals("PacketTrain"))
					{
						logger.info("PacketTrain requested from " + clientIP
								+ ".");
					}
					if (line.contains("Value"))
					{
						int value = Integer.parseInt(line.substring(5,
								line.length()));
						// System.out.println("Value: " + value);
						factor = factor + value;
						if (factor < 0)
						{
							factor = 0;
						}
					}
					char[] a = new char[packetSize];
					Arrays.fill(a, 'x');
					String message = new String(a);

					long start, end = 0;
					start = System.nanoTime();

					for (int i = 0; i < 20; i++)
					{
						out.write(message + System.nanoTime() + "\n");
						out.flush();
						Thread.sleep(factor);
						if (i == 0)
						{
							end = System.nanoTime();
						}
					}
					double delta = (end - start) / 1000000000.0;
					// System.out.println("Delta: " + delta);
					rate = (packetSize / (delta)) / 1000;
					// System.out.println("Rate R: " + rate + "KB/s");
				} else if (line.equals("Bandwidth"))
				{
					out.write(rate + "\n");
					out.flush();
				} else
				{
					out.write(line + "\n");
					out.flush();
					logger.info("Message (" + line.length() + "Bytes) from "
							+ clientIP + " arrived.");
				}
			}

			client.close();
			System.out.println("Verbindung mit " + clientIP + " beendet.");
			logger.info("Connection with " + clientIP + " closed.");
		} catch (SocketException e)
		{
			logger.error("Socket from " + clientIP
					+ " interrupted. Error Message: " + e.getMessage());
		} catch (SocketTimeoutException e)
		{
			logger.error("Socket Timeout from " + clientIP + ".");
		} catch (InterruptedException e)
		{
			logger.error("Problem with Thread. Error Message: "
					+ e.getMessage());
		} catch (Exception e)
		{
			logger.error("General Error. Error Message: " + e.getMessage());
		}

	}

}
