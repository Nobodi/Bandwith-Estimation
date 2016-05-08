package tcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Timer;

public class Client
{
	private DataModel model;
	// General Attributes
	private String serverAddress;
	private int port;
	private int packetSize;
	private String message;
	private int timeout;
	// File Writing Attributes
	private String path;
	private String timestamp;

	// Packet Pair Attributes
	private long inStart, inEnd, outStart, outEnd;
	private double deltaIn, deltaOut;
	private int[] messageLengths;

	// GPing Attributes
	private int smallPacketSize, largePacketSize, gPingLoops;
	private String smallPacketMessage, largePacketMessage;

	// Packet Train Attributes
	private int numberOfPackets;
	private int ptpacketLength;
	private int ptPackets;

	// RTT Attributes
	private String urlToRead;
	private int bytes = 0;

	public Client()
	{
		// Initialize general Attributes
		// Local
		this.serverAddress = "192.168.1.114";
		// Local Server
		// this.serverAddress = "192.168.1.109";
		// Server
		// this.serverAddress = "132.252.208.102";
		this.port = 2600;
		this.packetSize = 1400;
		this.timeout = 15000;
		char[] a = new char[(packetSize - 1)];
		Arrays.fill(a, 'x');
		this.message = new String(a);
		this.message += "\n";

		this.path = "./Measurements/";
		// Create Timestamp
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat dt = new SimpleDateFormat("d.MM.yyyy,HH.mm");
		DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.MEDIUM);
		this.timestamp = dt.format(cal.getTime());

		// Initialize Packet Pair Attributes
		this.messageLengths = new int[] { 128, 1128 };
		this.inStart = 0;
		this.inEnd = 0;
		this.outStart = 0;
		this.outEnd = 0;
		this.deltaIn = 0;
		this.deltaOut = 0;

		// Initialize GPing Attributes
		this.smallPacketSize = 64;
		this.largePacketSize = 1064;
		this.gPingLoops = 1;

		// Create large Packet Message
		a = new char[(this.largePacketSize - 1)];
		Arrays.fill(a, 'x');
		this.largePacketMessage = new String(a);
		this.largePacketMessage += "\n";

		// Create small Packet Message
		a = new char[(this.smallPacketSize - 1)];
		Arrays.fill(a, 'x');
		this.smallPacketMessage = new String(a);
		this.smallPacketMessage += "\n";

		// Initialize Packet Train Attributes
		this.numberOfPackets = 0;
		this.ptpacketLength = 1400;
		// Number of Packets in the Packet Train (%2 == 0, and
		// 10<=ptPackets<=20)
		this.ptPackets = 16;

		// Initialize RTT Attributes
		this.urlToRead = "http://" + this.serverAddress + "/512KB";
		this.bytes = 0;

		model = new DataModel(serverAddress);
		// new Sniffer(model).start();
	}

	public void startClient(int[] methods, int[] number)
			throws UnknownHostException, IOException, SocketTimeoutException
	{
		Socket socket = new Socket(serverAddress, port);
		// Set Timeout if Server is not able to response
		socket.setSoTimeout(timeout);

		socket.setSendBufferSize(1);
		socket.setReceiveBufferSize(1);

		BufferedReader in = new BufferedReader(new InputStreamReader(
				socket.getInputStream()));

		BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
				socket.getOutputStream()));

		try
		{
			for (int k = 0; k < methods.length; k++)
			{
				for (int i = 0; i < number[k]; i++)
				{
					if (methods[k] == DataModel.PACKETPAIR)
					{
						System.out.println("Packet Pair");
						try
						{
							Thread.sleep(200);
						} catch (InterruptedException e)
						{
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						this.packetPair(in, out);
					} else if (methods[k] == DataModel.PACKETTRAIN)
					{
						System.out.println("Packet Train");
						this.packetTrain(in, out);
					} else if (methods[k] == DataModel.GPING)
					{
						System.out.println("GPing");
						this.gPing(in, out);
					} else if (methods[k] == DataModel.SPRUCE)
					{
						this.spruce(in, out);
					} else if (methods[k] == DataModel.TEST)
					{
						this.singleRTT(in, out);
						// this.test(in, out);
					}
				}
			}

		} finally
		{
			socket.close();
		}

		for (int k = 0; k < methods.length; k++)
		{
			for (int i = 0; i < number[k]; i++)
			{
				if (methods[k] == DataModel.RTT)
				{
					System.out.println("RTT");
					this.rtt(DataModel.RTT, this.urlToRead);
				} else if (methods[k] == DataModel.DOWNLOAD)
				{
					System.out.println("Download");
					this.download();
				}
			}
		}

	}

	/***************************************************************************
	 * Download
	 **************************************************************************/
	public void download() throws ProtocolException, IOException
	{
		this.rtt(DataModel.DOWNLOAD, "http://" + this.serverAddress + "/10MB");
	}

	public void singleRTT(BufferedReader in, BufferedWriter out)
			throws IOException
	{
		inStart = System.nanoTime();
		out.write(this.message);
		out.flush();
		in.readLine();
		inEnd = System.nanoTime();

		double delta = (inEnd - inStart) / 1000000000.0 / 2;
		System.out.println("Delta: " + delta);
		double bandwidth = (packetSize / delta) / 1000;
		model.addBandwidth(new DataObject(DataModel.TEST, this.packetSize,
				bandwidth));
	}

	/***************************************************************************
	 * RTT
	 **************************************************************************/
	public void rtt(int method, String urlToRead) throws ProtocolException,
			IOException
	{
		bytes = 0;
		long start = 0;
		long end = 0;

		URL url = new URL(urlToRead);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		conn.setRequestMethod("GET");
		conn.setUseCaches(false);
		conn.setConnectTimeout(timeout);
		conn.addRequestProperty("User-Agent",
				"Mozilla/5.0 (Windows NT 6.1; WOW64; rv:25.0) Gecko/20100101 Firefox/39.0");

		start = System.nanoTime();
		InputStream in = conn.getInputStream();

		while (in.read() != -1)
		{
			bytes++;
		}
		end = System.nanoTime();

		double rttBW = bytes / ((end - start) / 1000000000.0) / 1000;
		model.addBandwidth(new DataObject(method, bytes, rttBW));

	}

	/***************************************************************************
	 * Packet Pair
	 **************************************************************************/
	public void packetPair(BufferedReader in, BufferedWriter out)
			throws IOException
	{
		double[] bandwidths = new double[messageLengths.length];
		for (int t = 0; t < messageLengths.length; t++)
		{
			char[] a = new char[(messageLengths[t] - 1)];
			Arrays.fill(a, 'x');
			this.message = new String(a);
			this.message += "\n";

			this.outStart = System.nanoTime();
			out.write(this.message);
			out.flush();
			out.write(this.message);
			out.flush();
			this.outEnd = System.nanoTime();

			this.inStart = System.nanoTime();
			in.readLine();
			in.readLine();
			this.inEnd = System.nanoTime();

			this.deltaIn = (inEnd - inStart) / 1000000000.0;
			this.deltaOut = (outEnd - outStart) / 1000000000.0;
			double delta = Math.abs((deltaOut - deltaIn));
			double ppBandwidth = messageLengths[t] / delta / 1000;
			bandwidths[t] = ppBandwidth;
		}

		double combinedBW = 0;
		for (int t = 0; t < bandwidths.length; t++)
		{
			combinedBW = combinedBW + bandwidths[t];
		}

		combinedBW = combinedBW / messageLengths.length;
		DataObject data = new DataObject(DataModel.PACKETPAIR, 0, combinedBW);

		model.addBandwidth(data);
	}

	/***************************************************************************
	 * GPing
	 **************************************************************************/
	public void gPing(BufferedReader in, BufferedWriter out) throws IOException
	{
		ArrayList<Double> smallRTTs = new ArrayList<Double>();
		ArrayList<Double> largeRTTs = new ArrayList<Double>();
		double rttLarge = 0;
		double rttSmall = 0;
		for (int j = 0; j < this.gPingLoops; j++)
		{
			// Send small Packet
			outStart = System.nanoTime();
			out.write(this.smallPacketMessage);
			out.flush();
			in.readLine();
			outEnd = System.nanoTime();
			rttSmall = (outEnd - outStart) / 1000000000.0 / 2;

			// Send large Packet
			outStart = System.nanoTime();
			out.write(this.largePacketMessage);
			out.flush();
			in.readLine();
			outEnd = System.nanoTime();
			rttLarge = (outEnd - outStart) / 1000000000.0 / 2;
			largeRTTs.add(rttLarge);
			smallRTTs.add(rttSmall);

			System.out.println("Small: " + rttSmall);
			System.out.println("Large: " + rttLarge);
		}
		// double alpha = 3.5;
		double gPingBandwidth = 2
				* (this.largePacketSize - this.smallPacketSize)
				/ (Math.abs(Collections.min(largeRTTs)
						- Collections.min(smallRTTs))) / 1000;

		model.addBandwidth(new DataObject(DataModel.GPING, 0, gPingBandwidth));
	}

	/***************************************************************************
	 * Packet Train
	 **************************************************************************/
	public void packetTrain(BufferedReader in, BufferedWriter out)
			throws IOException
	{
		out.write("PacketTrain" + this.ptPackets + this.ptpacketLength + "\n");
		out.flush();

		String message = "";
		ArrayList<Double> deltas = new ArrayList<Double>();

		int factor = 0;
		boolean flag = true;
		int toHighBW = 0, toLowBW = 0;
		while (flag)
		{
			for (int i = 0; i < this.ptPackets; i++)
			{
				message = in.readLine();
				if (message.length() > this.ptpacketLength)
				{
					double timestamp = Double.valueOf(message.substring(
							this.ptpacketLength, message.length()));
					deltas.add((System.nanoTime() - timestamp) / 1000000000.0);
				}
				numberOfPackets++;
			}

			for (int i = 1; i < deltas.size() - 1; i++)
			{
				if (deltas.get(i) > deltas.get(i + 1))
				{
					toLowBW++;
				} else
				{
					toHighBW++;
				}
				System.out.println("Delta " + i + ": " + deltas.get(i));
			}

			System.out.println("ToHigh: " + toHighBW);
			System.out.println("ToLow: " + toLowBW);
			if (toHighBW == toLowBW)
			{
				flag = false;
				out.write("Bandwidth\n");
				out.flush();

				double ptBandwidth = Double.parseDouble(in.readLine());
				model.addBandwidth(new DataObject(DataModel.PACKETTRAIN, 0,
						ptBandwidth));
				System.out.println("Ermittelte Bandbreite: " + ptBandwidth
						+ "KB/s");

			} else
			{
				factor = 0 - (toLowBW - toHighBW);
				deltas.clear();
				out.write("Value" + factor + "\n");
				out.flush();
				toHighBW = 0;
				toLowBW = 0;
			}
		}

	}

	/***************************************************************************
	 * Spruce
	 **************************************************************************/
	public void spruce(BufferedReader in, BufferedWriter out)
			throws IOException
	{
		out.write("Spruce\n");
		out.flush();

		in.readLine();
		outStart = System.nanoTime();
		in.readLine();
		outEnd = System.nanoTime();

		// Example of a capacity Value
		long capacity = 9000000;
		deltaOut = (outEnd - outStart) / 1000000000.0;
		double factor = ((deltaOut - 0.2) / 0.2);
		double bandwidth = capacity * (1 - factor) / 1000;
		System.out.println("DeltaOut: " + deltaOut);
		System.out.println("Faktor: " + factor);
		System.out.println("Ermittelte Bandbreite: " + bandwidth + "KB/s");

		model.addBandwidth(new DataObject(DataModel.SPRUCE, 0, bandwidth));
	}

	/***************************************************************************
	 * Test Method
	 **************************************************************************/
	public void test(BufferedReader in, BufferedWriter out) throws IOException
	{
		ArrayList<Double> deltas = new ArrayList<Double>();
		for (int t = 0; t < 2; t++)
		{
			out.write(message);
			out.flush();

			String response = in.readLine();
			double timestamp = Double.valueOf(response.substring(packetSize,
					response.length()));
			deltas.add((System.nanoTime() - timestamp) / 1000000000.0);
		}

		System.out.println("Delta 0: " + deltas.get(0));
		System.out.println("Delta 1: " + deltas.get(1));
		double delta = deltas.get(0) - deltas.get(1);
		if (delta < 0)
		{
			delta = Math.abs(delta);
		}
		System.out.println("Delta: " + delta);
		double bandwidth = (this.packetSize / delta / 1000);
		model.addBandwidth(new DataObject(DataModel.TEST, 0, bandwidth));
	}

	/***************************************************************************
	 * Write Data to File
	 **************************************************************************/
	public void writeDatatoFile(int method, double transferedData)
	{
		ArrayList<DataObject> bandwidths = model.getBandwidths();

		String fileName = this.getMethodName(method);
		File file = new File(this.path + fileName);

		try (FileWriter fw = new FileWriter(file, true);
				BufferedWriter bw = new BufferedWriter(fw);
				PrintWriter out = new PrintWriter(bw))
		{
			out.print(timestamp + ";");
			out.print(transferedData + ";");
			String temp = "";
			for (int i = 0; i < bandwidths.size(); i++)
			{
				if (bandwidths.get(i).getMethod() == method)
				{
					temp = temp + bandwidths.get(i).getBandwidth() + "|";
				}
			}
			temp = temp.substring(0, temp.length() - 1);
			out.print(temp);
			out.print(System.getProperty("line.separator"));
			out.flush();
		} catch (IOException e)
		{
			// exception handling left as an exercise for the reader
		}
	}

	public String getMethodName(int method)
	{
		switch (method)
		{
		case DataModel.PACKETPAIR:
			return "PacketPair";
		case DataModel.PACKETTRAIN:
			return "PacketTrain";
		case DataModel.GPING:
			return "GPing";
		case DataModel.SPRUCE:
			return "Spruce";
		case DataModel.RTT:
			return "RTT";
		case DataModel.DOWNLOAD:
			return "Download";
		case DataModel.TEST:
			return "Test";
		default:
			return "Unknown";
		}
	}

	public double calculateAvg(ArrayList<Double> measurements)
	{
		if (measurements.size() > 4)
		{
			// Delete smallest measurement
			measurements.remove(Collections.min(measurements));
			// Delete largest measurement
			measurements.remove(Collections.max(measurements));
		}
		double avgBandwidth = 0;
		for (int i = 0; i < measurements.size(); i++)
		{
			avgBandwidth = avgBandwidth + measurements.get(i);
		}
		avgBandwidth = avgBandwidth / measurements.size();
		return avgBandwidth;
	}

	/***************************************************************************
	 * Output of measured Data
	 **************************************************************************/
	public void output(int method)
	{
		ArrayList<DataObject> bandwidths = model.getBandwidths();

		if (bandwidths.size() <= 0)
		{
			System.out.println("Keine Werte gemessen!");
			return;
		}

		String name = this.getMethodName(method);

		System.out.println("-----------------------------------------");
		System.out.println("Messergebnisse Methode " + name);
		System.out.println("-----------------------------------------");
		ArrayList<Double> measurements = new ArrayList<Double>();
		double avgBandwidth = 0;
		int count = 1;
		for (int k = 0; k < bandwidths.size(); k++)
		{
			if (bandwidths.get(k).getMethod() == method)
			{
				System.out.println(count + ": "
						+ bandwidths.get(k).getBandwidth() + "KB/s");
				avgBandwidth = avgBandwidth + bandwidths.get(k).getBandwidth();
				measurements.add(bandwidths.get(k).getBandwidth());
				count++;
			}
		}

		double transferedData = 0;
		switch (method)
		{
		case DataModel.PACKETPAIR:
			for (int k = 0; k < messageLengths.length; k++)
			{
				// 4 = 2 Packets back-to-back (2*2) *
				// Message-Length + 4 * 54 = Packet Headers
				transferedData = transferedData + 4 * messageLengths[k] + 4
						* 54;
			}
			// (Count - 1) = Measurements
			transferedData = transferedData * (count - 1);
			break;
		case DataModel.PACKETTRAIN:
			// 65 Byte Message PacketTrain + Number of Packets in all
			// PacketTrains + 63 Byte Message Bandwidth + ~74 Byte Response
			transferedData = 65 + this.numberOfPackets * 1454 + 63 + 74;
			break;
		case DataModel.GPING:
			// (Count - 1) = Measurements * GPingRate * (2 small Packets + 2
			// large Packets + 4(2*2) * Packet Headers)
			transferedData = (count - 1) * this.gPingLoops
					* (2 * smallPacketSize + 2 * largePacketSize + 4 * 54);
			break;
		case DataModel.RTT:
			// (Count - 1) = Measurements * transferedBytes + Headers
			transferedData = (count - 1) * this.bytes + (count - 1) * 54;
			break;
		case DataModel.DOWNLOAD:
			transferedData = 10000000;
			break;
		}

		// Output in KB
		transferedData = transferedData / 1000;

		this.writeDatatoFile(method, transferedData);
		System.out.println("Benoetigte Datenmenge: " + transferedData + "KB");
		System.out.println("Gemittelte Bandbreite: "
				+ (avgBandwidth / (count - 1)) + "KB/s");

		System.out.println("Gemittelte Bandbreite update: "
				+ this.calculateAvg(measurements) + "KB/s");

	}

	public static void main(String[] args)
	{
		boolean startTimer = false;

		if (!startTimer)
		{
			Client client = new Client();

			int[] methods = { DataModel.PACKETPAIR };
			int rounds[] = { 1, 5, 5, 5, 3 };

			try
			{
				// Short Interruption so that the Sniffer has time to start
				// Thread.sleep(2000);
				client.startClient(methods, rounds);

			} catch (SocketTimeoutException e)
			{
				System.err
						.print("SocketTimeoutException: Server antwortet nicht. ");
				System.err.println(e.getMessage());
			} catch (UnknownHostException e)
			{
				System.err
						.print("UnknowHostException: Client kann die Verbindung zum Server nicht aufbauen. ");
				System.out.println(e.getMessage());
			} catch (IOException e)
			{
				System.err
						.print("IOException: Genereller Eingabe-/Ausgabefehler. ");
				System.out.println(e.getMessage());
			}

			// Output of collected Data
			for (int i = 0; i < methods.length; i++)
			{
				client.output(methods[i]);
			}

		} else
		{
			// One Hour
			int time = 3600000;
			// One Minute
			time = 1800000;
			Timer timer = new Timer();
			ClientThread t = new ClientThread();
			// Restart Thread at the given rate
			timer.scheduleAtFixedRate(t, 0, time);
		}
	}
}
