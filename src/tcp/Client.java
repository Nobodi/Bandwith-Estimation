package tcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

import org.jnetpcap.packet.PcapPacket;

public class Client
{
	private DataModel model;
	// General Attributes
	private String serverAddress;
	private int port;
	private int packetSize;
	private String message;

	// Packet Pair Attributes
	private long inStart, inEnd, outStart, outEnd;
	private double deltaIn, deltaOut;

	// GPing Attributes
	private int smallPacketSize, largePacketSize;

	// Packet Train Attributes
	private int numberOfPackets;

	public Client()
	{
		// Initialize general Attributes
		// Local
		// this.serverAddress = "127.0.0.1";
		// Server
		this.serverAddress = "132.252.208.102";
		this.port = 2600;
		this.packetSize = 1400;
		char[] a = new char[packetSize];
		Arrays.fill(a, 'x');
		this.message = new String(a);

		// Initialize Packet Pair Attributes
		this.inStart = 0;
		this.inEnd = 0;
		this.outStart = 0;
		this.outEnd = 0;
		this.deltaIn = 0;
		this.deltaOut = 0;

		// Initialize GPing Attributes
		this.smallPacketSize = 64;
		this.largePacketSize = 1064;

		// Initialize Packet Train Attributes
		this.numberOfPackets = 0;

		model = new DataModel(serverAddress);
		// new Sniffer(model).start();
	}

	public void startClient(int[] methods, int[] number)
			throws UnknownHostException, IOException, SocketTimeoutException
	{
		Socket socket = new Socket(serverAddress, port);
		// Set Timeout if Server is not able to response
		socket.setSoTimeout(10000);
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
						this.packetPair(in, out);
					} else if (methods[k] == DataModel.PACKETTRAIN)
					{
						this.packetTrain(in, out);
					} else if (methods[k] == DataModel.GPING)
					{
						this.gPing(in, out);
					} else if (methods[k] == DataModel.SPRUCE)
					{
						this.spruce(in, out);
					}
				}
			}

		} finally
		{
			model.setStopThread(true);
			socket.close();
		}
	}

	public static double calculateDelta(PcapPacket p1, PcapPacket p2)
	{
		return ((p2.getCaptureHeader().timestampInNanos() - p1
				.getCaptureHeader().timestampInNanos()) / 1000000000.0);
	}

	/***************************************************************************
	 * Packet Pair
	 **************************************************************************/
	public void packetPair(BufferedReader in, BufferedWriter out)
			throws IOException
	{
		this.inStart = System.nanoTime();
		out.write(message + "\n");
		out.flush();
		out.write(message + "\n");
		out.flush();
		this.inEnd = System.nanoTime();

		System.out.println("Empfange...");
		this.outStart = System.nanoTime();
		String line = in.readLine();
		System.out.println(line);
		line = in.readLine();
		this.outEnd = System.nanoTime();
		System.out.println(line);

		this.deltaIn = (inEnd - inStart) / 1000000000.0;
		this.deltaOut = (outEnd - outStart) / 1000000000.0;

		double delta = Math.sqrt(Math.pow(deltaIn - deltaOut, 2));
		double ppBandwidth = packetSize / delta / 1000;

		DataObject data = new DataObject(DataModel.PACKETPAIR, ppBandwidth);

		double sendBW = (packetSize / deltaOut) / 1000.0;
		double receiveBW = (packetSize / deltaIn) / 1000.0;

		data.setReceiveBW(receiveBW);
		data.setSendBW(sendBW);

		model.addBandwidth(data);
	}

	/***************************************************************************
	 * GPing
	 **************************************************************************/
	public void gPing(BufferedReader in, BufferedWriter out) throws IOException
	{
		// Send large Packet
		char[] a = new char[this.largePacketSize];
		Arrays.fill(a, 'x');
		this.message = new String(a);

		outStart = System.nanoTime();
		out.write(message + "\n");
		out.flush();
		String line = in.readLine();
		outEnd = System.nanoTime();
		System.out.println(line);
		double rttLarge = (outEnd - outStart) / 1000000000.0;

		// Send small Packet
		a = new char[this.smallPacketSize];
		Arrays.fill(a, 'x');
		this.message = new String(a);
		outStart = System.nanoTime();
		out.write(message + "\n");
		out.flush();
		line = in.readLine();
		outEnd = System.nanoTime();
		System.out.println(line);
		double rttSmall = (outEnd - outStart) / 1000000000.0;

		// Calculate Bandwidth
		double gPingBandwidth = 2 * (1064 - 64) / (rttLarge - rttSmall) / 1000;

		System.out.println("RTTLarge: " + rttLarge);
		System.out.println("RTTSmall: " + rttSmall);
		System.out.println("Ermittelte Bandbreite mit gPing: " + gPingBandwidth
				+ "KB/s");

		model.addBandwidth(new DataObject(DataModel.GPING, gPingBandwidth));
	}

	/***************************************************************************
	 * Packet Train
	 **************************************************************************/
	public void packetTrain(BufferedReader in, BufferedWriter out)
			throws IOException
	{
		out.write("PacketTrain\n");
		out.flush();

		String message = "";
		ArrayList<Double> deltas = new ArrayList<Double>();

		int factor = 0;
		boolean flag = true;
		int toHigh = 0, toLow = 0;
		while (flag)
		{
			for (int i = 0; i < 20; i++)
			{
				message = in.readLine();
				if (message.length() > 1400)
				{
					double timestamp = new Double(message.substring(packetSize,
							message.length()));
					deltas.add((System.nanoTime() - timestamp) / 1000000000.0);
				}
				numberOfPackets++;
			}

			for (int i = 1; i < deltas.size() - 1; i++)
			{
				System.out.println(i + ": " + deltas.get(i));
				if (deltas.get(i) > deltas.get(i + 1))
				{
					toHigh++;
				} else
				{
					toLow++;
				}
			}

			if (toHigh == toLow)
			{
				flag = false;
				out.write("Bandwidth\n");
				out.flush();

				double ptBandwidth = Double.parseDouble(in.readLine());
				model.addBandwidth(new DataObject(DataModel.PACKETTRAIN,
						ptBandwidth));
				System.out.println("Ermittelte Bandbreite: " + ptBandwidth
						+ "KB/s");

			} else
			{
				factor = 0 - (toHigh - toLow);
				deltas.clear();
				out.write("Value" + factor + "\n");
				out.flush();
				toHigh = 0;
				toLow = 0;
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

		model.addBandwidth(new DataObject(DataModel.SPRUCE, bandwidth));
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

		String name = "";
		switch (method)
		{
		case 0:
			name = "PacketPair";
			break;
		case 1:
			name = "PacketTrain";
			break;
		case 2:
			name = "GPing";
			break;
		case 3:
			name = "Spruce";
			break;
		}

		System.out.println("-----------------------------------------");
		System.out.println("Messergebnisse Methode " + name);
		System.out.println("-----------------------------------------");
		double avgBandwidth = 0, avgSendBW = 0, avgReceiveBW = 0;
		int count = 1;
		for (int k = 0; k < bandwidths.size(); k++)
		{
			if (bandwidths.get(k).getMethod() == method)
			{
				System.out.println(count + ": "
						+ bandwidths.get(k).getBandwidth() + "KB/s");
				avgBandwidth = avgBandwidth + bandwidths.get(k).getBandwidth();

				if (method == DataModel.PACKETPAIR)
				{
					System.out.println("Sendebandbreite: "
							+ bandwidths.get(k).getSendBW() + "KB/s");
					System.out.println("Empfangsbandbreite: "
							+ bandwidths.get(k).getReceiveBW() + "KB/s");
					avgSendBW = avgSendBW + bandwidths.get(k).getSendBW();
					avgReceiveBW = avgReceiveBW
							+ bandwidths.get(k).getReceiveBW();
				}

				count++;
			}
		}

		if (method == DataModel.PACKETPAIR)
		{
			System.out.println("Benötigte Datenmenge: "
					+ ((count - 1) * 4 * packetSize + 4 * 54) + "Bytes");
			System.out.println("Gemittelte Sendebandbreite: " + avgSendBW + "");
			System.out.println("Gemittelte Empfangsbandbreite: " + avgReceiveBW
					+ "");
		} else if (method == DataModel.GPING)
		{
			System.out
					.println("Benötigte Datenmenge: "
							+ ((count - 1) * 2 * smallPacketSize + 2
									* largePacketSize + 4 * 54) + "Bytes");
		} else if (method == DataModel.PACKETTRAIN)
		{
			// 65 Byte Message PacketTrain + Number of Packets in all
			// PacketTrains + 63 Byte Message Bandwidth + ~74 Byte Response
			System.out.println("Benötigte Datenmenge: "
					+ (65 + this.numberOfPackets * 1454 + 63 + 74) + "Bytes");
		}
		System.out.println("Gemittelte Bandbreite: "
				+ (avgBandwidth / (count - 1)) + "KB/s");
	}

	public static void main(String[] args)
	{
		Client client = new Client();

		int[] methods = { DataModel.PACKETPAIR };
		int rounds[] = { 2, 2, 2 };

		try
		{
			// Short Interruption so that the Sniffer has time to start
			Thread.sleep(2000);
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
		} catch (InterruptedException e)
		{
			System.err.print("InterruptedException: ");
			System.out.println(e.getMessage());
		}

		// Output of collected Data
		for (int i = 0; i < methods.length; i++)
		{
			client.output(methods[i]);
		}
	}
}
