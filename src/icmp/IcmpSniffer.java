package icmp;

import java.util.ArrayList;
import java.util.List;

import org.jnetpcap.Pcap;
import org.jnetpcap.Pcap.Direction;
import org.jnetpcap.PcapIf;
import org.jnetpcap.packet.PcapPacket;
import org.jnetpcap.packet.PcapPacketHandler;
import org.jnetpcap.packet.format.FormatUtils;
import org.jnetpcap.protocol.lan.Ethernet;
import org.jnetpcap.protocol.network.Icmp;
import org.jnetpcap.protocol.network.Ip4;

public class IcmpSniffer extends Thread
{
	private IcmpDataModel model;
	private int count;
	private boolean flag = false;
	private long timer;

	// Init in a short way
	// // Init
	// Pcap pcap = Pcap.openLive(allDevices.get(0).getName(), 64 *
	// 1024,Pcap.MODE_PROMISCUOUS, 1000, errbuf);

	public IcmpSniffer(IcmpDataModel model)
	{
		this.model = model;
		this.count = 0;
		timer = System.currentTimeMillis();
	}

	@Override
	public void run()
	{
		List<PcapIf> alldevs = new ArrayList<PcapIf>(); // Will be filled with
		// // NICs
		StringBuilder errbuf = new StringBuilder(); // For any error msgs

		int r = Pcap.findAllDevs(alldevs, errbuf);

		if (r != Pcap.OK || alldevs.isEmpty())
		{
			System.err.printf("Can't read list of devices, error is %s",
					errbuf.toString());
			return;
		}

		PcapIf device = alldevs.get(2); // We know we have atleast 1 device
		System.out.printf("\nChoosing '%s' on your behalf:\n",
				(device.getDescription() != null) ? device.getDescription()
						: device.getName());

		int snaplen = 64 * 1024; // Capture all packets, no trucation
		int flags = Pcap.MODE_PROMISCUOUS; // capture all packets
		int timeout = 1 * 2000; // 10 seconds in millis

		/*
		 * Display a little message so that we know which API we are using in
		 * this example
		 */
		System.out.printf("Is 'pcap' library API 1.0.0 or above loaded? %s%n",
				Pcap.isPcap100Loaded());

		Pcap pcap;
		if (Pcap.isPcap100Loaded())
		{
			pcap = Pcap.create(device.getName(), errbuf);
			if (pcap == null)
			{
				System.err.printf("Error while opening device for capture: "
						+ errbuf.toString());
				return;
			}

			/* Set our standard properties */
			pcap.setSnaplen(snaplen);
			pcap.setPromisc(flags);
			pcap.setTimeout(timeout);

			/* Here are some new ones */
			pcap.setDirection(Direction.INOUT); // We now have IN, OUT or INOUT
			pcap.setBufferSize(128 * 1024 * 1024); // Set ring-buffer to 128Mb

			pcap.activate(); // Make our handle active and start capturing

		} else
		{
			pcap = Pcap.openLive(device.getName(), snaplen, flags, timeout,
					errbuf);

			if (pcap == null)
			{
				System.err.printf("Error while opening device for capture: "
						+ errbuf.toString());
				return;
			}
		}

		PcapPacketHandler<String> jpacketHandler = new PcapPacketHandler<String>()
		{

			public void nextPacket(PcapPacket packet, String user)
			{
				Ip4 ip = new Ip4();
				if (packet.hasHeader(ip) && !flag)
				{
					packet.getHeader(ip);
					if (FormatUtils.ip(ip.destination()).equals(
							model.getServerAddress()))
					{
						System.out.println(PingTest.asString(packet.getHeader(
								new Ethernet()).destination()));
						model.setMacAddress(packet.getHeader(new Ethernet())
								.destination());
						flag = true;
					}
				}
				// If time difference becomes to large, Thread will be canceled
				if (((System.currentTimeMillis() - timer) / 1000.0) > 10.0)
				{
					model.setStopThread(true);
				}

				if (packet.hasHeader(new Icmp()))
				{
					timer = System.currentTimeMillis();
					// Check that sending and receiving Packets are from right
					// Server
					if (FormatUtils.ip(ip.source()).equals(
							model.getServerAddress())
							|| FormatUtils.ip(ip.destination()).equals(
									model.getServerAddress()))
					{
						System.out.println("ICMP Paket gefunden");
						model.addPacket(packet);
						count++;
					}
				}

				if (model.isStopThread())
				{
					System.out.println("COUNT: " + count
							+ "!!!!!!!!!!!!!!!!!!!!");
					// Inform Listeners
					model.inform();
					pcap.breakloop();
				}
			}
		};

		try
		{
			pcap.loop(Pcap.LOOP_INFINITE, jpacketHandler, "jNetPcap rocks!");

		} finally
		{
			pcap.close();
		}
	}

	public static void main(String[] args)
	{
		IcmpDataModel model = new IcmpDataModel("188.138.66.20");

		IcmpSniffer sniffer = new IcmpSniffer(model);
		sniffer.start();
	}

}
