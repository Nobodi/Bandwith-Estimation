package tcp;

import java.util.ArrayList;

public class DataModel
{
	public final static int PACKETPAIR = 0;
	public final static int PACKETTRAIN = 1;
	public final static int GPING = 2;
	public final static int SPRUCE = 3;
	public final static int RTT = 4;
	public final static int DOWNLOAD = 5;
	public final static int TEST = 10;

	private String serverAddress;
	private ArrayList<DataObject> bandwidths;

	public DataModel(String serverAddress)
	{
		this.serverAddress = serverAddress;
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

	public ArrayList<DataObject> getBandwidths()
	{
		return bandwidths;
	}

	public void addBandwidth(DataObject object)
	{
		this.bandwidths.add(object);
	}

}
