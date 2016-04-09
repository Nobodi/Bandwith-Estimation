package tcp;

import java.util.ArrayList;

import org.jnetpcap.packet.PcapPacket;

public class DataModel
{
	public static int PACKETPAIR = 0;
	public static int PACKETTRAIN = 1;
	public static int GPING = 2;
	public static int SPRUCE = 3;

	private String serverAddress;
	private ArrayList<PcapPacket> packets;
	private boolean stopThread;
	private ArrayList<DataObject> bandwidths;

	public DataModel(String serverAddress)
	{
		this.serverAddress = serverAddress;
		this.packets = new ArrayList<PcapPacket>();
		this.stopThread = false;
		this.bandwidths = new ArrayList<DataObject>();
	}

	public String getServerAddress()
	{
		return serverAddress;
	}

	public void setServerAddress(String serverAddress)
	{
		this.serverAddress = serverAddress;
	}

	public synchronized ArrayList<PcapPacket> getPackets()
	{
		try
		{
			wait();
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		return packets;
	}

	public void setPackets(ArrayList<PcapPacket> packets)
	{
		this.packets = packets;
	}

	public synchronized void addPacket(PcapPacket packet)
	{
		this.packets.add(packet);
		if (packets.size() >= 2)
		{
			notify();
		}
	}

	public boolean isStopThread()
	{
		return stopThread;
	}

	public void setStopThread(boolean stopThread)
	{
		this.stopThread = stopThread;
	}

	public ArrayList<DataObject> getBandwidths()
	{
		return bandwidths;
	}

	public void addBandwidth(DataObject object)
	{
		this.bandwidths.add(object);
	}

}
