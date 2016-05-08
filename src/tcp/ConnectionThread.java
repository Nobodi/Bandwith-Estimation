package tcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.apache.log4j.Logger;

public class ConnectionThread extends Thread
{
	private Socket client;
	private Logger logger;
	private double rate;
	private int factor;
	private int packetSize;
	private int ptPackets;

	public ConnectionThread(Socket client, Logger logger, int packetSize)
	{
		this.client = client;
		this.logger = logger;
		this.packetSize = packetSize;
		this.rate = 0;
		this.factor = 500000;
	}

	@Override
	public void run()
	{
		String clientIP = client.getInetAddress().getHostAddress();
		System.out.println("Neue Verbindung " + clientIP);

		logger.info("Connection: New Connection from " + clientIP + ".");

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
				} else if (line.startsWith("PacketTrain")
						|| line.startsWith("Value"))
				{
					if (line.startsWith("PacketTrain"))
					{
						this.packetSize = new Integer(line.substring(13,
								line.length()));
						this.ptPackets = new Integer(line.substring(11, 13));
						System.out.println("Paketlaenge: " + this.packetSize);
						System.out.println("Anzahl Pakete: " + this.ptPackets);
						logger.info("PacketTrain requested from " + clientIP
								+ ".");
					}
					if (line.startsWith("Value"))
					{
						int value = Integer.parseInt(line.substring(5,
								line.length()));
						System.out.println("Value: " + value);
						factor = factor + (value * 10000);
						System.out.println("Factor: " + factor);
						if (factor < 0)
						{
							System.out.println("Bandbreite: " + rate);
							factor = 0;
						}
					}
					char[] a = new char[packetSize];
					Arrays.fill(a, 'x');
					String message = new String(a);

					long start = 0, end = 0, currentTime, referenceTime;

					ArrayList<Double> deltas = new ArrayList<Double>();
					for (int i = 0; i < this.ptPackets; i++)
					{
						currentTime = System.nanoTime();
						referenceTime = currentTime;
						start = System.nanoTime();
						out.write(message + System.nanoTime() + "\n");
						out.flush();
						// Timer
						while ((currentTime - referenceTime) < factor)
						{
							currentTime = System.nanoTime();
							System.out.print("");
						}
						end = System.nanoTime();

						deltas.add((end - start) / 1000000000.0);
					}
					double delta = Collections.min(deltas);
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
			logger.info("Connection: Connection with " + clientIP + " closed.");
		} catch (SocketException e)
		{
			logger.error("Socket from " + clientIP
					+ " interrupted. Error Message: " + e.getMessage());
			System.out.println("SocketException: Verbindung unterbrochen.");
		} catch (SocketTimeoutException e)
		{
			logger.error("Socket Timeout from " + clientIP + ".");
			System.out
					.println("SocketTimeoutException: Client antwortet nicht.");
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
