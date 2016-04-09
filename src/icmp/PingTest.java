package icmp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapIf;
import org.jnetpcap.packet.JHeader;
import org.jnetpcap.packet.JHeaderChecksum;
import org.jnetpcap.packet.JHeaderPool;
import org.jnetpcap.packet.JMemoryPacket;
import org.jnetpcap.packet.JPacket;
import org.jnetpcap.packet.PcapPacket;
import org.jnetpcap.packet.format.FormatUtils;
import org.jnetpcap.protocol.JProtocol;
import org.jnetpcap.protocol.lan.Ethernet;
import org.jnetpcap.protocol.network.Ip4;

public class PingTest
{

	// Method to get MAC-Address as String from byte-Array
	public static String asString(final byte[] mac)
	{
		final StringBuilder buf = new StringBuilder();
		for (byte b : mac)
		{
			if (buf.length() != 0)
			{
				buf.append(':');
			}
			if (b >= 0 && b < 16)
			{
				buf.append('0');
			}
			buf.append(Integer.toHexString((b < 0) ? b + 256 : b).toUpperCase());
		}

		return buf.toString();
	}

	// Change Packet-Payload to given Size
	public JPacket modifyPacket(String rawPacket, String ipAddress,
			byte[] macAddress, int size) throws UnknownHostException
	{
		// Packet Headers + ICMP 32-bit Payload
		byte[] headers = FormatUtils.toByteArray(rawPacket);

		// new PacketSize
		int packetSize = size - 1;
		// Create Payload with Sign 'x'
		char[] a = new char[packetSize];
		Arrays.fill(a, 'x');
		byte[] payload = new String(a).getBytes();

		// Create Byte-Array with Headers and new Payload
		byte[] buffer = new byte[payload.length + headers.length];
		System.arraycopy(headers, 0, buffer, 0, headers.length);
		System.arraycopy(payload, 0, buffer, headers.length, payload.length);

		// Create new packet
		JPacket packet = new JMemoryPacket(JProtocol.ETHERNET_ID, buffer);

		// Modify Destination MAC-Address
		packet.getHeader(new Ethernet()).destination(macAddress);

		// Modify length
		Ip4 ip = packet.getHeader(new Ip4());
		ip.length(ip.length() + packetSize);

		// Modify Destination IP-Address
		InetAddress destAddress = InetAddress.getByName(ipAddress);
		ip.destination(destAddress.getAddress());

		// Because of Adaption of the Packet Checksums of the single Headers
		// have to be recalculated
		JHeaderPool pool = new JHeaderPool();
		packet.scan(Ethernet.ID); // Rescan

		final int count = packet.getHeaderCount();
		for (int i = 0; i < count; i++)
		{
			final int id = packet.getHeaderIdByIndex(i);
			final JHeader header = pool.getHeader(id);

			if ((header instanceof JHeaderChecksum) && packet.hasHeader(header))
			{
				JHeaderChecksum crc = (JHeaderChecksum) header;

				crc.checksum(crc.calculateChecksum());
			}
		}
		return packet;
	}

	public void startPing(JPacket packet, int pings) throws IOException
	{
		ArrayList<PcapIf> alldevs = new ArrayList<PcapIf>(); // Will be filled
		// with NICs
		StringBuilder errbuf = new StringBuilder(); // For any error msgs

		/***************************************************************************
		 * First get a list of devices on this system
		 **************************************************************************/

		int r = Pcap.findAllDevs(alldevs, errbuf);
		if (r != Pcap.OK || alldevs.isEmpty())
		{
			System.err.printf("Can't read list of devices, error is %s",
					errbuf.toString());
			return;
		}
		PcapIf device = alldevs.get(2); // We know we have atleast 1 device

		/*****************************************
		 * Second we open a network interface
		 *****************************************/
		int snaplen = 64 * 1024; // Capture all packets, no trucation
		int flags = Pcap.MODE_PROMISCUOUS; // capture all packets
		int timeout = 1 * 10000; // 10 seconds in millis
		Pcap pcap = Pcap.openLive(device.getName(), snaplen, flags, timeout,
				errbuf);

		// Set Source IP to Interface IP
		Ip4 ip = new Ip4();
		packet.getHeader(ip);
		ip.source(device.getAddresses().get(0).getAddr().getData());
		ip.recalculateChecksum();
		// Change MAC-Address of the Packet to the device MAC-Address
		Ethernet eth = packet.getHeader(new Ethernet());
		eth.source(device.getHardwareAddress());
		// Set Destination MAC-Address to null so that the System insert right
		// address
		eth.destination("".getBytes());
		eth.recalculateChecksum();
		packet.scan(JProtocol.ETHERNET_ID);
		for (int i = 0; i < pings; i++)
		{
			pcap.sendPacket(packet);
		}
	}

	public double calculateDelta(PcapPacket p1, PcapPacket p2)
	{
		return ((p2.getCaptureHeader().timestampInNanos() - p1
				.getCaptureHeader().timestampInNanos()) / 1000000000.0);
	}

	public static void main(String args[])
	{
		// 1-byte ICMP Packet
		String icmpRawPacket = "9c80df8a 62270cd2 92077646 08004500"
				+ "001d6840 00008001 6289c0a8 0165d83a"
				+ "d5ce0800 96fd0001 000161";

		// Address to Ping
		String serverAddress = "188.138.66.20";
		PingTest ping = new PingTest();

		IcmpDataModel model = new IcmpDataModel(serverAddress);

		IcmpSniffer sniffer = new IcmpSniffer(model);
		sniffer.start();

		// Short Sleep Time because of Start-Phase of the Sniffer
		try
		{
			Thread.sleep(1000);
		} catch (InterruptedException e1)
		{
			e1.printStackTrace();
		}

		// Create Socket Connection because of getting MAC-Address of the
		// destination

		try
		{
			new Socket(serverAddress, 80).close();

			JPacket packet = ping.modifyPacket(icmpRawPacket, serverAddress,
					model.getMacAddress(), 1400);

			ArrayList<Double> ppBandwidths = new ArrayList<Double>();
			ArrayList<Double> gPingBandwidths = new ArrayList<Double>();
			ArrayList<PcapPacket> packets = new ArrayList<PcapPacket>();
			int loops = 1;
			for (int i = 0; i < loops; i++)
			{
				/***************************************************************************
				 * Packet Pair
				 **************************************************************************/
				ping.startPing(packet, 2);

				packets = model.getPackets();

				double deltaOut = 0, deltaIn = 0;
				if (packets.size() >= 4)
				{
					deltaOut = ping.calculateDelta(packets.get(0),
							packets.get(1));
					deltaIn = ping.calculateDelta(packets.get(2),
							packets.get(3));
				} else
				{
					System.out.println("Zu wenig Pakete.");
					break;
				}

				System.out.println("PP DeltaOut: " + deltaOut);
				System.out.println("PP DeltaIn: " + deltaIn);

				double delta = Math.sqrt(Math.pow(deltaIn - deltaOut, 2));
				double ppBandwidth = packets.get(0).size() / delta / 1000;
				ppBandwidths.add(ppBandwidth);
				System.out.println("Delta: " + delta);
				System.out.println("Ermittelte Bandbreite mit PP: "
						+ ppBandwidth + "KB/s");
				System.out.println("Sendebandbreite mit PP: "
						+ (packets.get(0).size() / deltaIn / 1000) + "KB/s");
				System.out.println("Empfangsbandbreite mit PP: "
						+ (packets.get(0).size() / deltaOut / 1000) + "KB/s");

				// Remove Packets from Packet-Array
				model.removePackets();
			}

			for (int i = 0; i < loops; i++)
			{
				/***************************************************************************
				 * GPing
				 **************************************************************************/
				// Ping large Packet with 1064-bit
				packet = ping.modifyPacket(icmpRawPacket, serverAddress,
						model.getMacAddress(), 1064);
				ping.startPing(packet, 1);

				// Ping small Packet with 64-bit
				packet = ping.modifyPacket(icmpRawPacket, serverAddress,
						model.getMacAddress(), 64);
				ping.startPing(packet, 1);

				packets = model.getPackets();

				double rttLarge = 0;
				double rttSmall = 0;

				// Loop to get right reply to right request
				if (packets.size() < 4)
				{
					System.out.println("Zu wenig Pakete.");
					break;
				}
				for (int k = 1; k < packets.size(); k++)
				{
					packet = packets.get(k);

					Ip4 ip = new Ip4();
					if (packet.hasHeader(ip))
					{
						// Check length of the Payload; Subtract IP-Header (20)
						// and
						// ICMP-Header (8) Lengths
						if ((ip.length() - 20 - 8) >= 1064)
						{
							// GPing Calculation
							rttLarge = ping.calculateDelta(packets.get(0),
									packets.get(k));
							packets.remove(k);
							packets.remove(0);
							rttSmall = ping.calculateDelta(packets.get(0),
									packets.get(1));
							break;
						}
					}
				}

				// GPing Output
				double gPingBandwidth = 2 * (1064 - 64) / (rttLarge - rttSmall)
						/ 1000;
				if (gPingBandwidth < 0)
				{
					gPingBandwidth = gPingBandwidth + -2 * gPingBandwidth;
				}
				gPingBandwidths.add(gPingBandwidth);

				System.out.println("RTTLarge: " + rttLarge);
				System.out.println("RTTSmall: " + rttSmall);
				System.out.println("Ermittelte Bandbreite mit gPing: "
						+ gPingBandwidth + "KB/s");

				model.removePackets();
			}

			double ppResult = 0;
			double gPingResult = 0;
			for (int i = 0; i < ppBandwidths.size(); i++)
			{
				ppResult = ppResult + ppBandwidths.get(i);
				gPingResult = gPingResult + gPingBandwidths.get(i);
			}

			ppResult = ppResult / ppBandwidths.size();
			gPingResult = gPingResult / gPingBandwidths.size();

			System.out.println("Durchschnittliche Bandbreite mit PP: "
					+ ppResult + "KB/s");
			System.out.println("Durchschnittliche Bandbreite mit gPing: "
					+ gPingResult + "KB/s");

			/***************************************************************************
			 * Throughput
			 **************************************************************************/

			ArrayList<PcapPacket> allPackets = model.getAllPackets();
			long start = allPackets.get(0).getCaptureHeader()
					.timestampInNanos();
			long end = allPackets.get(allPackets.size() - 1).getCaptureHeader()
					.timestampInNanos();
			double delta = (end - start) / 1000000000.0;
			double sizes = 0;
			for (int i = 0; i < allPackets.size(); i++)
			{
				sizes = sizes + allPackets.get(i).size();
			}

			System.out.println("Datendurchsatz: " + ((sizes / delta) / 1000)
					+ "KB/s");

			// Stop Sniffer-Thread
			model.setStopThread(true);
		} catch (IOException e)
		{
			model.setStopThread(true);
			System.out.println("Keine Verbindung zum Server möglich");
			return;
		}
	}
}
