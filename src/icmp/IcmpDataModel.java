package icmp;

import java.util.ArrayList;

import org.jnetpcap.packet.PcapPacket;

public class IcmpDataModel
{
	private String serverAddress;
	private ArrayList<PcapPacket> sessionPackets;
	private ArrayList<PcapPacket> allPackets;
	private boolean stopThread;
	private byte[] macAddress;

	public IcmpDataModel(String serverAddress)
	{
		this.serverAddress = serverAddress;
		this.sessionPackets = new ArrayList<PcapPacket>();
		this.allPackets = new ArrayList<PcapPacket>();
		this.stopThread = false;
		this.macAddress = null;
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
		return sessionPackets;
	}

	public void setPackets(ArrayList<PcapPacket> packets)
	{
		this.sessionPackets = packets;
	}

	public synchronized void addPacket(PcapPacket packet)
	{
		this.sessionPackets.add(packet);
		this.allPackets.add(packet);
		if (sessionPackets.size() >= 4)
		{
			this.inform();
		}
	}

	public synchronized void inform()
	{
		notify();
	}

	public void removePackets()
	{
		this.sessionPackets.clear();
	}

	public boolean isStopThread()
	{
		return stopThread;
	}

	public void setStopThread(boolean stopThread)
	{
		this.stopThread = stopThread;
	}

	public synchronized byte[] getMacAddress()
	{
		if (macAddress == null)
		{
			try
			{
				wait();
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		return macAddress;
	}

	public synchronized void setMacAddress(byte[] macAddress)
	{
		System.out.println("MAC-Address: " + macAddress);
		this.macAddress = macAddress;
		notify();
	}

	public ArrayList<PcapPacket> getAllPackets()
	{
		return allPackets;
	}

	public void setAllPackets(ArrayList<PcapPacket> allPackets)
	{
		this.allPackets = allPackets;
	}

}
