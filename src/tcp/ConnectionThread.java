package tcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;

import org.apache.log4j.Logger;

import com.google.gson.JsonSyntaxException;

public class ConnectionThread extends Thread
{
	private Socket client;
	private Logger logger;
	private double rate;
	private int factor;
	private int packetSize;
	private int ptPackets;
	private String rtt, download;
	private int bufferSize;

	public ConnectionThread(Socket client, Logger logger, int packetSize,
			String rtt, String download)
	{
		this.client = client;
		this.logger = logger;
		this.packetSize = packetSize;
		this.rate = 0;
		this.factor = 800000;
		this.rtt = rtt;
		this.bufferSize = 0;
	}

	public static String getActualDate()
	{
		// Get exakt Date
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat dt = new SimpleDateFormat("d.M.yyyy,HH.mm.ss",
				Locale.US);
		DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.MEDIUM);
		return dt.format(cal.getTime());
	}

	@Override
	public void run()
	{
		String clientIP = client.getInetAddress().getHostAddress();
		System.out.println("Neue Verbindung " + clientIP + ". - "
				+ ConnectionThread.getActualDate());

		logger.info("Connection: New Connection from " + clientIP + ". - "
				+ ConnectionThread.getActualDate());

		try
		{
			this.bufferSize = client.getReceiveBufferSize();

			BufferedReader in = new BufferedReader(new InputStreamReader(
					client.getInputStream()));

			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
					client.getOutputStream()));

			String line = "";

			while ((line = in.readLine()) != null)
			{
				if (line.startsWith("PacketTrain") || line.startsWith("Value"))
				{
					if (line.startsWith("PacketTrain"))
					{
						this.packetSize = new Integer(line.substring(13,
								line.length()));
						this.ptPackets = new Integer(line.substring(11, 13));
						System.out.println("Paketlaenge: " + this.packetSize);
						System.out.println("Anzahl Pakete: " + this.ptPackets);
						logger.info("PacketTrain requested from " + clientIP
								+ ". - " + ConnectionThread.getActualDate());
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
				} else if (line.equals("Download"))
				{
					System.out.println("Download erkannt");
					logger.info("Download requested from " + clientIP + ". - "
							+ ConnectionThread.getActualDate());

					for (int i = 0; i < 40; i++)
					{
						out.write(rtt);
						out.flush();
					}
					out.write("\nEnd\n");
					out.flush();

				} else if (line.equals("RTT"))
				{
					System.out.println("RTT erkannt");
					logger.info("RTT requested from " + clientIP + ". - "
							+ ConnectionThread.getActualDate());

					out.write(rtt);
					out.write("\nEnd\n");
					out.flush();
					System.out.println("Done.");
				} else if (line.equals("data"))
				{
					System.out.println("New Data Arrived!!!");

					logger.info("New Data for Storage from " + clientIP
							+ ". - " + ConnectionThread.getActualDate());

					String temp = "";
					String message = "";
					while ((temp = in.readLine()) != null)
					{
						System.out.println(temp);
						message = message + temp;
						if (temp.equals("End"))
						{
							System.out.println("BREAK");
							break;
						}
					}
					message = message.replace("End", "");

					// Add new Data to Database
					AddResultstoDatabase add = new AddResultstoDatabase();
					ArrayList<Results> results = add
							.getObjectsFromString(message);
					add.putDataintoDatabase(results);
					System.out.println("Data saved.");

				} else
				{
					client.setReceiveBufferSize(1);
					client.setSendBufferSize(1);
					out.write(line + "\n");
					out.flush();
					client.setReceiveBufferSize(this.bufferSize);
					client.setSendBufferSize(this.bufferSize);
					logger.info("Message (" + line.length() + "Bytes) from "
							+ clientIP + " arrived. - "
							+ ConnectionThread.getActualDate());
				}
			}

			client.close();
			System.out.println("Verbindung mit " + clientIP + " beendet. - "
					+ ConnectionThread.getActualDate());
			logger.info("Connection: Connection with " + clientIP
					+ " closed. - " + ConnectionThread.getActualDate());
		} catch (SocketException e)
		{
			logger.error("Socket from " + clientIP + " interrupted. - "
					+ ConnectionThread.getActualDate() + " - Error Message: "
					+ e.getMessage());
			System.out.println("SocketException: Verbindung unterbrochen. - "
					+ ConnectionThread.getActualDate());
		} catch (SocketTimeoutException e)
		{
			logger.error("Socket Timeout from " + clientIP + ". - "
					+ ConnectionThread.getActualDate());
			System.out
					.println("SocketTimeoutException: Client antwortet nicht. - "
							+ ConnectionThread.getActualDate());
		} catch (SQLException e)
		{
			logger.error("SQLException: Problem to save Data to Database. - "
					+ ConnectionThread.getActualDate() + " - Error Message: "
					+ e.getMessage());
			System.out
					.println("SQLException: Problem to save Data to Database. - "
							+ ConnectionThread.getActualDate());
		} catch (JsonSyntaxException e)
		{
			logger.error("Problem with Data Syntax. Not compatible Data. - "
					+ ConnectionThread.getActualDate());
			System.out
					.println("Problem with Data Syntax. Not compatible Data. - "
							+ ConnectionThread.getActualDate());
		} catch (Exception e)
		{
			logger.error("General Error. - " + ConnectionThread.getActualDate()
					+ " - Error Message: " + e.getMessage());
		}

	}
}
